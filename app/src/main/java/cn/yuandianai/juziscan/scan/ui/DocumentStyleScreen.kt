package cn.yuandianai.juziscan.scan.ui

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import cn.yuandianai.juziscan.scan.DocumentImageStyle
import cn.yuandianai.juziscan.scan.preparePreviewBitmap
import cn.yuandianai.juziscan.scanner.DocumentStyleProcessor
import cn.yuandianai.juziscan.scanner.SimpleDocumentScanner
import cn.yuandianai.juziscan.ui.theme.HpSmartColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Composable
fun DocumentStyleScreen(
    source: Bitmap,
    quad: List<PointF>,
    correctionMode: SimpleDocumentScanner.CorrectionMode,
    selected: DocumentImageStyle,
    onSelected: (DocumentImageStyle) -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    var corrected by remember(source, quad, correctionMode) { mutableStateOf<Bitmap?>(null) }
    var preview by remember(source, quad, correctionMode) { mutableStateOf<Bitmap?>(null) }
    var processing by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val filterMutex = remember { Mutex() }
    var generation by remember { mutableLongStateOf(0L) }
    LaunchedEffect(source, quad, correctionMode, retry) {
        var owned: Bitmap? = null
        try {
            val result = withContext(Dispatchers.Default) {
                val small = preparePreviewBitmap(source, 1280)
                try {
                    SimpleDocumentScanner.perspectiveCorrect(small, quad, correctionMode, maxOutputSide = 1280)
                        .let { if (it === source || it === small) it.copy(Bitmap.Config.ARGB_8888, false) else it }
                        .also { owned = it }
                } finally { if (small !== source) small.recycle() }
            }
            corrected = result
            owned = null
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = true; processing = false }
        finally { owned?.recycle() }
    }
    DisposableEffect(corrected) {
        val bitmap = corrected
        onDispose { bitmap?.recycle() }
    }
    LaunchedEffect(corrected, selected, retry) {
        val base = corrected ?: return@LaunchedEffect
        val request = ++generation
        processing = true
        error = false
        // Own a copy so leaving this screen never waits for a native OpenCV call.
        val working = base.copy(Bitmap.Config.ARGB_8888, false)
        var owned: Bitmap? = null
        try {
            val result = withContext(Dispatchers.Default) {
                filterMutex.withLock {
                    ensureActive()
                    DocumentStyleProcessor.apply(working, selected).also { owned = it }
                        .also { ensureActive() }
                }
            }
            preview = result
            owned = null
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = true }
        finally {
            working.recycle()
            owned?.recycle()
            if (generation == request) processing = false
        }
    }
    Column(Modifier.fillMaxSize().background(HpSmartColors.Background).statusBarsPadding().navigationBarsPadding()) {
        SimpleTitleBar("图片处理", onBack, "完成", onDone, Modifier.padding(horizontal = 20.dp))
        Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            (preview ?: corrected)?.let { Image(it.asImageBitmap(), "图片处理预览", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            if (processing) CircularProgressIndicator(Modifier.align(Alignment.TopEnd).size(28.dp))
            if (error) TextButton(onClick = { retry++ }, modifier = Modifier.align(Alignment.BottomCenter)) { Text("处理失败，点击重试") }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DocumentImageStyle.entries.forEach { style ->
                FilterChip(selected = style == selected, onClick = { onSelected(style) }, label = { Text(style.label) })
            }
        }
    }
}
