package cn.yuandianai.juziscan.scan

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.yuandianai.juziscan.history.HistoryRepository
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Invoked in two separate app processes by run-document-styles-e2e.ps1. */
@RunWith(AndroidJUnit4::class)
class DocumentStylesRestartE2eTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val artifacts get() = File(context.getExternalFilesDir(null), "document-styles-e2e").apply { mkdirs() }
    private val checkpoint get() = File(artifacts, "restart-checkpoint.json")
    private fun phase() { assumeTrue(InstrumentationRegistry.getArguments().getString("documentStylePhase") == "true") }
    @Test fun persistBeforeProcessTermination() {
        phase()
        val repository = HistoryRepository(context)
        val source = Bitmap.createBitmap(640, 900, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(210, 190, 160)) }
        val quad = listOf(PointF(.1f,.1f), PointF(.9f,.1f), PointF(.9f,.9f), PointF(.1f,.9f))
        val record = repository.saveDocumentRecord(listOf("white", "grayscale", "bw").map {
            ScannedPage(sourceBitmap = source, editableQuad = quad, imageStyle = it)
        })
        checkpoint.writeText(JSONObject().put("id", record.id).put("revision", repository.loadDocumentPages(record.id).first().contentRevision).toString())
        assertFalse(File(record.filePath).exists())
        source.recycle()
    }
    @Test fun restoreAfterProcessTerminationAndExport() {
        phase()
        val saved = JSONObject(checkpoint.readText())
        val repository = HistoryRepository(context)
        val id = saved.getString("id")
        try {
            val pages = repository.loadDocumentPages(id)
            assertEquals(listOf("white", "grayscale", "bw"), pages.map { it.imageStyle })
            assertEquals(saved.getString("revision"), pages.first().contentRevision)
            assertEquals(.1f, pages.first().editableQuad.first().x)
            val pdf = repository.ensureDocumentPdf(id)
            pdf.copyTo(File(artifacts, "process-restarted.pdf"), overwrite = true)
            assertTrue(repository.prepareDocumentPages(id).all { it.hasProcessedOutput })
            File(artifacts, "process-restart.json").writeText(JSONObject().put("restored", true).put("styles", "white,grayscale,bw").put("pdfBytes", pdf.length()).toString(2))
        } finally { repository.deleteRecord(id); checkpoint.delete() }
    }
}
