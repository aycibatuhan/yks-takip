package com.yks2027.tracker

import com.yks2027.tracker.core.backup.CsvCodec
import com.yks2027.tracker.core.database.ExamDao
import com.yks2027.tracker.core.platform.PickedFile
import com.yks2027.tracker.feature.importexport.ImportHubViewModel
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.koin.core.context.GlobalContext

/** CSV across platforms: an Android-exported CSV goes through the desktop İçe Aktar path; the desktop export feeds the emulator. */
class DesktopCsvRoundTripTest {

    @Test
    fun androidCsvImportsThroughTheDesktopHub() {
        val path = System.getenv("YKS_ANDROID_CSV"); assumeTrue(path != null && File(path).exists())
        DesktopTestApp.ensureStarted()
        val koin = GlobalContext.get()
        val examDao = koin.get<ExamDao>()
        val vm = ImportHubViewModel(examDao, koin.get(), koin.get(), koin.get(), koin.get(), koin.get())
        val before = runBlocking { examDao.allOnce().size }
        vm.loadCsv(PickedFile("yks_denemeler.csv", File(path!!).readBytes()))
        runBlocking { repeat(50) { if (vm.state.value.csvRows.isNotEmpty() && !vm.state.value.busy) return@repeat; delay(100) } }
        val rows = vm.state.value.csvRows
        assertTrue("CSV rows parsed", rows.isNotEmpty())
        val duplicates = rows.count { it.duplicate }
        println("[csv] android→desktop: rows=${rows.size} duplicate-flagged=$duplicates errors=${vm.state.value.csvErrors.size}")
        // Duplicates arrive unchecked (human-confirm); select everything explicitly to prove the additive insert.
        rows.indices.filter { !rows[it].selected }.forEach(vm::toggleRow)
        vm.importSelected()
        runBlocking { repeat(50) { if (!vm.state.value.busy && vm.state.value.csvRows.isEmpty()) return@repeat; delay(100) } }
        assertEquals(before + rows.size, runBlocking { examDao.allOnce().size })
    }

    @Test
    fun desktopCsvExportForAndroid() {
        DesktopTestApp.ensureStarted()
        val csv = runBlocking { CsvCodec.export(GlobalContext.get().get<ExamDao>().allOnce()) }
        assertTrue(csv.startsWith("﻿tarih;tur;ad;yayinevi;ders;soru;dogru;yanlis;bos;net"))
        System.getenv("YKS_DESKTOP_CSV")?.let { File(it).writeText(csv); println("[csv] desktop export → $it (${csv.lines().size} lines)") }
    }
}
