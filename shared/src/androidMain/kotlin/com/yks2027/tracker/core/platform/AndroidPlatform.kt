package com.yks2027.tracker.core.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.room.RoomDatabase
import com.yks2027.tracker.core.database.DatabaseFactory
import com.yks2027.tracker.core.database.YksDatabase
import java.io.ByteArrayOutputStream
import okio.Path.Companion.toPath

/**
 * v1.x used `Context.preferencesDataStore(name)`, whose file is
 * `<filesDir>/datastore/<name>.preferences_pb`. Same path, same protobuf serializer —
 * settings, timer state and encrypted keys carry over untouched.
 */
class AndroidPreferencesStores(private val context: Context) : PreferencesStores {
    private val open = HashMap<String, DataStore<Preferences>>()

    @Synchronized
    override fun open(name: String): DataStore<Preferences> = open.getOrPut(name) {
        PreferenceDataStoreFactory.createWithPath {
            context.filesDir.resolve("datastore/$name.preferences_pb").absolutePath.toPath()
        }
    }
}

/** Framework SQLite via Room's compatibility path — no driver change, v1.x `yks.db` opens as before. */
class AndroidDatabaseFactory(private val context: Context) : DatabaseFactory {
    override fun builder(): RoomDatabase.Builder<YksDatabase> =
        Room.databaseBuilder(context, YksDatabase::class.java, "yks.db")
}

class AndroidImageDownscaler : ImageDownscaler {
    override fun toJpeg(bytes: ByteArray, maxPx: Int, quality: Int): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0) { "Görsel çözümlenemedi." }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: throw IllegalArgumentException("Görsel çözümlenemedi.")
        val longest = maxOf(bitmap.width, bitmap.height)
        val finalBitmap = if (longest > maxPx) {
            val scale = maxPx.toFloat() / longest
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            bitmap
        }
        val out = ByteArrayOutputStream()
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }
}
