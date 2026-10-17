package cn.yuandianai.juziscan.scan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.platform.app.InstrumentationRegistry
import cn.yuandianai.juziscan.common.*
import cn.yuandianai.juziscan.ui.theme.FireflyTheme
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Renders the actual Android resources and Compose component, with inspectable sheets. */
class FireflyIconRenderE2eTest {
    @get:Rule val ui = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val artifacts get() = File(context.getExternalFilesDir(null), "firefly-icons-e2e").apply { mkdirs() }

    @Test fun vectorsKeepTransparentBoundsAndScaleAcrossDensities() {
        val metrics = JSONArray()
        FireflyIconName.entries.forEach { icon ->
            val coverage = (1..4).map { density ->
                val side = 24 * density
                val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
                val drawable = requireNotNull(context.getDrawable(icon.drawable)).mutate()
                drawable.setBounds(0, 0, side, side)
                drawable.draw(Canvas(bitmap))
                val pixels = IntArray(side * side)
                bitmap.getPixels(pixels, 0, side, 0, 0, side, side)
                assertEquals("Transparent corner: ${icon.name}", 0, AndroidColor.alpha(pixels[0]))
                assertEquals("Transparent corner: ${icon.name}", 0, AndroidColor.alpha(pixels.last()))
                val alphaCoverage = pixels.sumOf { AndroidColor.alpha(it).toLong() }.toDouble() / (255 * side * side)
                assertTrue("Missing paths or opaque fill: ${icon.name} ($alphaCoverage)", alphaCoverage in 0.03..0.6)
                File(artifacts, "vector-${icon.name}-${density}x.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
                alphaCoverage
            }
            assertTrue("Stroke weight changes with density: ${icon.name}", coverage.max() - coverage.min() < 0.06)
            metrics.put(JSONObject().put("icon", icon.name).put("coverage1xTo4x", JSONArray(coverage)))
        }
        File(artifacts, "vector-metrics.json").writeText(metrics.toString(2))
    }

    @Test fun composeSheetsShowSizesThemesSelectedAndDisabledStates() {
        var dark by mutableStateOf(false)
        var size by mutableStateOf(24)
        var enabled by mutableStateOf(true)
        var selected by mutableStateOf(false)
        ui.setContent {
            FireflyTheme(darkTheme = dark) {
                Surface {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(8.dp)) {
                    Text("Tabler v3.49.0 · ${size}dp · ${if (dark) "dark" else "light"}", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                    FireflyIconName.entries.chunked(6).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { icon ->
                                Column(Modifier.weight(1f).height(74.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    FireflyIcon(icon, size = size.dp, selected = selected, enabled = enabled)
                                    Text(icon.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 7.sp, maxLines = 1)
                                }
                            }
                            repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                }
            }
        }
        for (night in listOf(false, true)) for (dp in listOf(20, 24, 32)) {
            ui.runOnIdle { dark = night; size = dp; enabled = true; selected = false }
            ui.onNodeWithContentDescription("相册导入").assertWidthIsEqualTo(dp.dp).assertHeightIsEqualTo(dp.dp)
            val bitmap = ui.onRoot().captureToImage().asAndroidBitmap()
            File(artifacts, "sheet-${if (night) "dark" else "light"}-${dp}dp.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        ui.runOnIdle { dark = false; size = 24; selected = true }
        File(artifacts, "sheet-selected.png").outputStream().use { ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        ui.runOnIdle { enabled = false }
        File(artifacts, "sheet-disabled.png").outputStream().use { ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        ui.onAllNodesWithContentDescription("相册导入").assertCountEquals(1)
    }
}
