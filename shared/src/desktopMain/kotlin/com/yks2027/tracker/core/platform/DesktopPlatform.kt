package com.yks2027.tracker.core.platform

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.yks2027.tracker.core.database.DatabaseFactory
import com.yks2027.tracker.core.database.YksDatabase
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlinx.coroutines.Dispatchers
import okio.Path.Companion.toOkioPath

class DesktopPreferencesStores(private val paths: DesktopPaths) : PreferencesStores {
    private val open = HashMap<String, DataStore<Preferences>>()

    @Synchronized
    override fun open(name: String): DataStore<Preferences> = open.getOrPut(name) {
        PreferenceDataStoreFactory.createWithPath {
            paths.dataStoreDir.resolve("$name.preferences_pb").toOkioPath()
        }
    }
}

/** BundledSQLiteDriver (spec §4) under the user data dir; migrations added by the shared module. */
class DesktopDatabaseFactory(private val paths: DesktopPaths) : DatabaseFactory {
    override fun builder(): RoomDatabase.Builder<YksDatabase> =
        Room.databaseBuilder<YksDatabase>(name = paths.databaseFile.absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
}

class DesktopImageDownscaler : ImageDownscaler {
    override fun toJpeg(bytes: ByteArray, maxPx: Int, quality: Int): ByteArray {
        val src = ImageIO.read(ByteArrayInputStream(bytes)) ?: throw IllegalArgumentException("Görsel çözümlenemedi.")
        val longest = maxOf(src.width, src.height)
        val scale = if (longest > maxPx) maxPx.toFloat() / longest else 1f
        val w = (src.width * scale).toInt().coerceAtLeast(1)
        val h = (src.height * scale).toInt().coerceAtLeast(1)
        val rgb = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        rgb.createGraphics().apply {
            setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            drawImage(src, 0, 0, w, h, java.awt.Color.WHITE, null)
            dispose()
        }
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val params = writer.defaultWriteParam.apply {
            compressionMode = ImageWriteParam.MODE_EXPLICIT
            compressionQuality = quality / 100f
        }
        val out = ByteArrayOutputStream()
        ImageIO.createImageOutputStream(out).use { ios ->
            writer.output = ios
            writer.write(null, IIOImage(rgb, null, null), params)
        }
        writer.dispose()
        return out.toByteArray()
    }
}
