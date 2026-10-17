package cn.yuandianai.juziscan.scan

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.PointF
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import java.io.File
import cn.yuandianai.juziscan.BuildConfig
import cn.yuandianai.juziscan.auth.AuthSessionStore
import cn.yuandianai.juziscan.payment.JuZiScanBackendClient
import cn.yuandianai.juziscan.payment.VipPurchaseManager
import cn.yuandianai.juziscan.payment.AppVersionCheckResponse
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import cn.yuandianai.juziscan.history.HistoryRecord
import cn.yuandianai.juziscan.history.HistoryRepository
import cn.yuandianai.juziscan.history.HistoryRecordKind
import cn.yuandianai.juziscan.history.DocumentSaveCoordinator
import cn.yuandianai.juziscan.history.SavedDocumentState
import cn.yuandianai.juziscan.scan.ui.ActionPillButton
import cn.yuandianai.juziscan.scan.ui.AllToolsScreen
import cn.yuandianai.juziscan.scan.ui.AuthDialog
import cn.yuandianai.juziscan.scan.ui.BindPhoneDialog
import cn.yuandianai.juziscan.scan.ui.FeedbackDialog
import cn.yuandianai.juziscan.scan.ui.CameraCaptureScreen
import cn.yuandianai.juziscan.scan.ui.DocumentStyleScreen
import cn.yuandianai.juziscan.scan.ui.EdgeReviewScreen
import cn.yuandianai.juziscan.scan.ui.IdCardResultScreen
import cn.yuandianai.juziscan.scan.ui.NicknameEditDialog
import cn.yuandianai.juziscan.scan.ui.OcrResultScreen
import cn.yuandianai.juziscan.scan.ui.PreviewResultScreen
import cn.yuandianai.juziscan.scan.ui.ProcessingSpinner
import cn.yuandianai.juziscan.scan.ui.ReorderScreen
import cn.yuandianai.juziscan.scan.ui.ScanHomeScreen
import cn.yuandianai.juziscan.scan.ui.ShareSaveScreen
import cn.yuandianai.juziscan.scan.ui.TableResultScreen
import cn.yuandianai.juziscan.scan.ui.TopSoftButton
import cn.yuandianai.juziscan.scan.ui.AllDocumentsScreen
import cn.yuandianai.juziscan.scanner.SimpleDocumentScanner
import cn.yuandianai.juziscan.settings.SettingsScreen
import cn.yuandianai.juziscan.settings.AccountSecurityScreen
import cn.yuandianai.juziscan.ui.theme.HpSmartColors
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.clickable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import cn.yuandianai.juziscan.common.FireflyIcon
import cn.yuandianai.juziscan.common.FireflyIconName

@Composable
fun ScanFlow() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = remember(context) { context.findComponentActivity() }
    var cameraPermissionRequested by rememberSaveable { mutableStateOf(false) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var isCameraPermissionPermanentlyDenied by remember { mutableStateOf(false) }

    fun refreshCameraPermissionState() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        hasCameraPermission = granted
        isCameraPermissionPermanentlyDenied =
            cameraPermissionRequested &&
                !granted &&
                activity?.let {
                    !ActivityCompat.shouldShowRequestPermissionRationale(
                        it,
                        Manifest.permission.CAMERA
                    )
                } == true
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        refreshCameraPermissionState()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshCameraPermissionState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    ScannerFlowScreen(
        hasCameraPermission = hasCameraPermission,
        isCameraPermissionPermanentlyDenied = isCameraPermissionPermanentlyDenied,
        onRequestCameraPermission = {
            cameraPermissionRequested = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        },
        onOpenAppSettings = {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            )
            context.startActivity(intent)
        }
    )
}

@Composable
private fun PermissionScreen(
    isPermanentlyDenied: Boolean,
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HpSmartColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp)
    ) {
        TopSoftButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 12.dp)
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(142.dp)
                    .background(
                        brush = Brush.linearGradient(
                            listOf(Color(0xFF0B63FF), Color(0xFF36A3FF))
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                CameraPermissionGlyph(
                    color = Color.White,
                    modifier = Modifier.size(72.dp)
                )
            }

            Text(
                text = if (isPermanentlyDenied) "请到设置开启相机权限" else "开启相机权限",
                color = HpSmartColors.TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 28.dp)
            )
            Text(
                text = if (isPermanentlyDenied) {
                    "你已禁止相机权限，请在系统设置中开启后继续扫描。"
                } else {
                    "用于拍摄文档、文字、证件和表格"
                },
                color = HpSmartColors.TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp)
                    .background(Color.White, RoundedCornerShape(20.dp))
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PermissionBenefit(
                    text = if (isPermanentlyDenied) {
                        "在系统设置中允许相机后可继续拍摄"
                    } else {
                        "仅在进入拍摄页时使用相机"
                    }
                )
                PermissionBenefit(
                    text = if (isPermanentlyDenied) {
                        "你也可以先返回首页使用相册导入等功能"
                    } else {
                        "拍摄内容只用于本地扫描处理"
                    }
                )
            }

            ActionPillButton(
                text = if (isPermanentlyDenied) "去设置开启" else "开启相机权限",
                filled = true,
                onClick = if (isPermanentlyDenied) onOpenSettings else onGrant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 26.dp)
                    .height(50.dp)
            )
        }
    }
}

@Composable
private fun PermissionBenefit(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            FireflyIcon(FireflyIconName.Check, size = 16.dp, tint = androidx.compose.material3.MaterialTheme.colorScheme.primary, contentDescription = null)
        }
        Text(
            text = text,
            color = HpSmartColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CameraPermissionGlyph(color: Color, modifier: Modifier = Modifier) {
    FireflyIcon(FireflyIconName.Camera, tint = color, modifier = modifier, contentDescription = null)
}

@Composable
private fun ScannerFlowScreen(
    hasCameraPermission: Boolean,
    isCameraPermissionPermanentlyDenied: Boolean,
    onRequestCameraPermission: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    val context = LocalContext.current
    val hostActivity = remember(context) { context.findComponentActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val captureExecutor = remember { Executors.newSingleThreadExecutor() }
    val scope = rememberCoroutineScope()
    val screenActive = remember { AtomicBoolean(true) }
    val vm = remember { ScanViewModel() }
    var boundCamera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var cameraResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            cameraResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val historyRepository = remember(context) { HistoryRepository(context.applicationContext) }
    val documentSaver = remember(context) { DocumentSaveCoordinator.get(context) }
    val savedDocuments by documentSaver.documents.collectAsState()
    val savedPhases by documentSaver.phases.collectAsState()
    val saveFailures by documentSaver.failures.collectAsState()
    val documentChanges by documentSaver.changes.collectAsState()
    val historyReadVersion = remember { java.util.concurrent.atomic.AtomicLong(0) }
    val authSessionStore = remember(context) { AuthSessionStore(context.applicationContext) }
    val settingsPrefs = remember(context) {
        context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
    }
    var scanQuality by remember {
        mutableStateOf(settingsPrefs.getString("scan_quality", "高清") ?: "高清")
    }
    var defaultExportFormat by remember {
        mutableStateOf(settingsPrefs.getString("default_export_format", "PDF") ?: "PDF")
    }
    var defaultImageStyle by remember {
        mutableStateOf(DocumentImageStyle.parse(settingsPrefs.getString(DocumentImageStyle.PREFERENCE_KEY, "color")))
    }
    var editingImageStyle by remember { mutableStateOf(DocumentImageStyle.Color) }
    fun cacheBytes(): Long = context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    fun formatCacheSize(bytes: Long): String = when {
        bytes < 1024 -> "${bytes} B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
    }
    var cacheSize by remember { mutableStateOf(formatCacheSize(cacheBytes())) }
    var legalReturnStage by remember { mutableStateOf(ScanStage.Home) }
    var vipReturnStage by remember { mutableStateOf(ScanStage.Home) }
    val backendClient = remember(authSessionStore, mainExecutor) {
        JuZiScanBackendClient(
            accessTokenProvider = { authSessionStore.accessTokenOrNull() },
            onUnauthorized = {
                mainExecutor.execute {
                    authSessionStore.clear()
                }
            }
        )
    }
    val vipPurchaseManager = remember(backendClient) { VipPurchaseManager(backendClient = backendClient) }
    var historyRecords by remember { mutableStateOf<List<HistoryRecord>>(emptyList()) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var feedbackBusy by remember { mutableStateOf(false) }
    var showAuthDialog by rememberSaveable { mutableStateOf(false) }
    var showBindPhoneDialog by rememberSaveable { mutableStateOf(false) }
    var authBusy by remember { mutableStateOf(false) }
    var authErrorMessage by remember { mutableStateOf<String?>(null) }
    var showNicknameDialog by rememberSaveable { mutableStateOf(false) }
    var profileBusy by remember { mutableStateOf(false) }
    var profileErrorMessage by remember { mutableStateOf<String?>(null) }
    var pendingPurchasePlanCode by rememberSaveable { mutableStateOf<String?>(null) }
    var latestVersionInfo by remember { mutableStateOf<AppVersionCheckResponse?>(null) }
    var historyActionBusy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val session = authSessionStore.session
        if (session != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        backendClient.getCurrentUser()
                    }
                }.onSuccess { user ->
                    authSessionStore.save(session.copy(user = user))
                }.onFailure { error ->
                    Log.w("JuZiScan", "Validate auth session failed", error)
                }
            }
        }

        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    backendClient.checkLatestVersion(BuildConfig.VERSION_CODE)
                }
            }.onSuccess { versionInfo ->
                if (versionInfo != null) {
                    latestVersionInfo = versionInfo
                }
            }.onFailure { error ->
                Log.w("JuZiScan", "Check app version failed", error)
            }
        }
    }

    with(vm) {
        val currentSession = authSessionStore.session

        fun clearEditorAfterUse() {
            sourceBitmap = null
            reviewBitmap = null
            editableQuad = emptyList()
            correctionMode = SimpleDocumentScanner.CorrectionMode.AUTO
        }

        /** 提交所有尚未处理的页面，由后台队列按优先级逐页处理。 */
        fun submitPendingPages() {
            currentDocumentRecordId?.let { id -> documentSaver.attach(id, SavedDocumentState(documentVersion, pages)) }
        }

        /** 用户切换页面时提升该页的处理优先级。 */
        fun promoteActivePage() {
            currentDocumentRecordId?.let { id -> pages.getOrNull(activeIndex)?.let { documentSaver.promote(id, it.id) } }
        }

        fun refreshHistoryRecords() {
            val requestVersion = historyReadVersion.incrementAndGet()
            scope.launch {
                try {
                    val loaded = withContext(Dispatchers.IO) { historyRepository.loadRecords() }
                    if (historyReadVersion.get() == requestVersion) historyRecords = loaded
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    Log.e("JuZiScan", "Load history failed", error)
                    Toast.makeText(context, "读取历史记录失败，请重试", Toast.LENGTH_LONG).show()
                }
            }
        }

        LaunchedEffect(documentChanges) { refreshHistoryRecords() }
        LaunchedEffect(currentDocumentRecordId, savedDocuments[currentDocumentRecordId], savedPhases[currentDocumentRecordId], stage) {
            val id = currentDocumentRecordId
            val saved = savedDocuments[id]
            if (saved != null && saved.updatedAtMillis >= documentVersion &&
                stage in listOf(ScanStage.Preview, ScanStage.Capture)) {
                pages = saved.pages
                documentVersion = saved.updatedAtMillis
                activeIndex = activeIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
            }
            pagePhases = savedPhases[id].orEmpty()
        }

        fun refreshMembershipStatus() {
            if (currentSession == null) {
                membershipStatus = null
                return
            }
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        vipPurchaseManager.getMembershipStatus()
                    }
                }.onSuccess { status ->
                    membershipStatus = status
                }.onFailure { error ->
                    Log.w("JuZiScan", "Refresh membership status failed", error)
                }
            }
        }

        fun refreshMembershipPlans() {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        vipPurchaseManager.getMembershipPlans()
                    }
                }.onSuccess { response ->
                    membershipPlans = response.plans
                    membershipPaymentChannels = response.payment_channels
                }.onFailure { error ->
                    Log.w("JuZiScan", "Refresh membership plans failed", error)
                }
            }
        }

        fun startVipPurchase(planCode: String) {
            val activity = hostActivity
            if (activity == null) {
                Toast.makeText(context, "当前页面无法拉起支付宝", Toast.LENGTH_SHORT).show()
                return
            }
            vipPurchaseInFlight = true
            vipPurchaseMessage = "正在创建订单并拉起支付宝"
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        vipPurchaseManager.startPurchase(activity, planCode)
                    }
                }.onSuccess { result ->
                    vipPurchaseResult = result
                    refreshMembershipStatus()
                    refreshMembershipPlans()
                    vipPurchaseInFlight = false
                    val shouldShowToast = when {
                        result.orderStatus == "paid" && result.isVip -> {
                            vipPurchaseMessage = "支付成功，会员权益已生效"
                            true
                        }
                        result.alipayResultStatus == "6001" -> {
                            vipPurchaseMessage = "支付已取消"
                            false
                        }
                        result.alipayResultStatus == "4000" -> {
                            vipPurchaseMessage = "支付未完成，请重新尝试"
                            true
                        }
                        else -> {
                            vipPurchaseMessage = "支付结果确认中，请稍后查看；如已扣款，请勿重复支付"
                            false
                        }
                    }
                    if (shouldShowToast) {
                        Toast.makeText(context, vipPurchaseMessage, Toast.LENGTH_LONG).show()
                    }
                }.onFailure { error ->
                    vipPurchaseInFlight = false
                    vipPurchaseMessage = "支付失败，请稍后重试"
                    Toast.makeText(context, vipPurchaseMessage, Toast.LENGTH_LONG).show()
                }
            }
        }

        LaunchedEffect(currentSession?.accessToken) {
            if (currentSession != null) {
                refreshMembershipStatus()
            } else {
                membershipStatus = null
            }
            refreshMembershipPlans()
        }

        /** 历史记录分享：文档按需生成或复用 PDF 缓存，生成在后台执行。 */
        fun shareHistoryRecord(record: HistoryRecord) {
            if (historyActionBusy) return
            if (record.kind == HistoryRecordKind.Document && record.documentManifestPath != null) {
                historyActionBusy = true
                Toast.makeText(context, "正在准备文档…", Toast.LENGTH_SHORT).show()
            }
            scope.launch {
                try {
                    withContext(NonCancellable + Dispatchers.IO) {
                        historyRepository.shareRecord(record)
                    }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    Log.e("JuZiScan", "History share failed", error)
                    Toast.makeText(
                        context,
                        error.message ?: "分享失败，请重试",
                        Toast.LENGTH_LONG
                    ).show()
                } finally {
                    historyActionBusy = false
                }
            }
        }

        fun openHistoryRecord(record: HistoryRecord) {
            if (record.kind == HistoryRecordKind.Document) {
                if (isProcessingPreview) return
                val origin = stage
                isProcessingPreview = true
                processingMessage = "正在打开文档…"
                scope.launch {
                    try {
                        val restored = withContext(Dispatchers.IO) { historyRepository.loadDocumentPages(record.id) }
                        startEntry(ScanEntry.Document, openCapture = false)
                        pages = restored
                        currentDocumentRecordId = record.id
                        documentVersion = record.updatedAtMillis
                        documentSaver.attach(record.id, SavedDocumentState(record.updatedAtMillis, restored))
                        documentReturnStage = origin
                        openedFromHistory = true
                        activeIndex = 0
                        stage = ScanStage.Preview
                        // 已就绪页面直接使用缓存；缺失处理缓存的页面后台续处理。
                        submitPendingPages()
                        promoteActivePage()
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        Log.e("JuZiScan", "Restore document failed", error)
                        Toast.makeText(context, "无法打开文档，请重试", Toast.LENGTH_LONG).show()
                    } finally {
                        isProcessingPreview = false
                        processingMessage = "正在处理…"
                    }
                }
                return
            }
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) { historyRepository.openRecord(record) }
                }.onFailure { error ->
                    Log.e("JuZiScan", "History preview failed", error)
                    Toast.makeText(
                        context,
                        error.message ?: "无法预览此文档",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        fun deleteHistoryRecord(record: HistoryRecord) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        historyRepository.deleteRecord(record.id)
                    }
                }.onSuccess {
                    refreshHistoryRecords()
                    Toast.makeText(context, "已删除历史记录", Toast.LENGTH_SHORT).show()
                }.onFailure { error ->
                    Log.e("JuZiScan", "History delete failed", error)
                    Toast.makeText(
                        context,
                        "删除失败，请重试",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        fun openEdgeReviewForPage(index: Int) {
            if (isProcessingPreview) return
            val currentIndex = index.coerceIn(0, pages.lastIndex)
            val page = pages.getOrNull(currentIndex) ?: return
            val owner = currentDocumentRecordId
            val session = documentUiSession
            editingDocumentPage = page
            editingImageStyle = DocumentImageStyle.parse(page.imageStyle)
            val showEditor: (ScannedPage) -> Unit = { editingPage ->
                sourceBitmap = editingPage.sourceBitmap
                reviewBitmap = editingPage.reviewBitmap
                editableQuad = editingPage.editableQuad.ifEmpty { editingPage.detectedQuad }.ifEmpty { defaultDocumentQuad() }
                correctionMode = editingPage.correctionMode
                replacingIndex = currentIndex
                currentTask = CaptureTask.Document
                stage = ScanStage.EdgeReview
            }
            isProcessingPreview = true
            processingMessage = "正在整理当前页面…"
            scope.launch {
                runCatching {
                    withContext(Dispatchers.Default) {
                        val hydrated = page.sourceImagePath?.let { path ->
                            val bitmap = requireNotNull(decodeBitmapFromFileBounded(path, 1600)) { "无法读取页面原图" }
                            page.copy(sourceBitmap = bitmap, reviewBitmap = bitmap, processedBitmap = null)
                        } ?: hydratePageForEditing(page)
                        val prepared = if (hydrated.editableQuad.isEmpty() && hydrated.detectedQuad.isEmpty()) {
                            hydrated.copy(detectedQuad = SimpleDocumentScanner.detectDocumentQuad(context,
                                hydrated.reviewBitmap ?: hydrated.sourceBitmap).orEmpty())
                        } else hydrated
                        materializePageRotation(prepared)
                    }
                }.onSuccess { materializedPage ->
                    if (currentDocumentRecordId == owner && documentUiSession == session && stage == ScanStage.Preview) {
                        showEditor(materializedPage)
                    }
                }.onFailure { error ->
                    Log.e("JuZiScan", "Prepare page for editing failed", error)
                    Toast.makeText(
                        context,
                        "无法打开编辑，请重试",
                        Toast.LENGTH_LONG
                    ).show()
                }
                if (currentDocumentRecordId == owner && documentUiSession == session) {
                    isProcessingPreview = false
                    processingMessage = "正在处理…"
                }
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                screenActive.set(false)
                documentSaver.detach(currentDocumentRecordId)
                analysisExecutor.shutdown()
                captureExecutor.shutdown()
            }
        }

        fun pushBitmapToEdgeReview(bitmap: Bitmap) {
            isCapturing = true
            isProcessingPreview = true
            processingMessage = if (currentTask == CaptureTask.Ocr) {
                "正在准备文字识别…"
            } else {
                "正在识别文档边缘…"
            }
            scope.launch {
                runCatching {
                    withContext(Dispatchers.Default) {
                        if (currentTask == CaptureTask.Ocr) {
                            // OCR does not need the ONNX edge model. Avoid making
                            // recognition depend on vendor-specific native runtime
                            // behavior before ML Kit even receives the image.
                            val workingBitmap = prepareWorkingBitmap(bitmap)
                            if (workingBitmap !== bitmap && !bitmap.isRecycled) {
                                bitmap.recycle()
                            }
                            SourceBitmapProcessingResult(
                                sourceBitmap = workingBitmap,
                                reviewBitmap = workingBitmap,
                                quad = defaultDocumentQuad()
                            )
                        } else {
                            processSourceBitmap(context, bitmap, preserveOriginal = currentTask == CaptureTask.Document)
                        }
                    }
                }.onSuccess { result ->
                    sourceBitmap = result.sourceBitmap
                    reviewBitmap = result.reviewBitmap
                    editableQuad = result.quad
                    correctionMode = SimpleDocumentScanner.CorrectionMode.AUTO
                    stage = ScanStage.EdgeReview
                    if (currentTask != CaptureTask.Ocr && result.quad.size != 4) {
                        Toast.makeText(
                            context,
                            "模型未识别到边缘，请手动调整",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }.onFailure { error ->
                    Log.e("JuZiScan", "Document edge detection failed", error)
                    Toast.makeText(context, "图片处理失败，请重试", Toast.LENGTH_LONG).show()
                }
                isCapturing = false
                isProcessingPreview = false
                processingMessage = "正在处理…"
            }
        }

        fun refreshDocumentStateIfNeeded(): Boolean {
            val saved = documentSaver.documents.value[currentDocumentRecordId] ?: return false
            if (saved.updatedAtMillis <= documentVersion) return false
            pages = saved.pages
            documentVersion = saved.updatedAtMillis
            activeIndex = activeIndex.coerceIn(0, pages.lastIndex)
            return true
        }

        fun commitPageEdits(updated: List<ScannedPage>): Boolean {
            val id = ensureDocumentId()
            if (refreshDocumentStateIfNeeded()) return false
            if (documentSaver.hasPendingChanges(id)) {
                Toast.makeText(context, "请先重试未保存的修改", Toast.LENGTH_SHORT).show()
                return false
            }
            return try {
                val record = documentSaver.commit(id, updated, documentVersion)
                documentVersion = record.updatedAtMillis
                pages = updated
                true
            } catch (error: Exception) {
                Toast.makeText(context, "修改未保存，请检查存储空间后重试", Toast.LENGTH_LONG).show()
                false
            }
        }

        fun idCardCaptureTargetCount(): Int = selectedIdCardLayout.captureCount

        fun idCardCaptureHint(): String {
            val nextIndex = (idCardCapturedPages.size + 1).coerceAtMost(idCardCaptureTargetCount())
            return "请拍摄${selectedIdCardDocumentType.label}  第 ${nextIndex}/${idCardCaptureTargetCount()} 张"
        }

        suspend fun processIdCardPage(bitmap: Bitmap): ScannedPage {
            return withContext(Dispatchers.Default) {
                val workingBitmap = prepareWorkingBitmap(bitmap)
                val quad = SimpleDocumentScanner.detectDocumentQuad(context, workingBitmap) ?: emptyList()
                val corrected = if (quad.size == 4) {
                    SimpleDocumentScanner.perspectiveCorrect(
                        bitmap,
                        quad,
                        selectedIdCardDocumentType.normalizedAspect
                    )
                } else {
                    bitmap
                }
                val processed = SimpleDocumentScanner.enhanceDocument(corrected)
                ScannedPage(
                    sourceBitmap = bitmap,
                    reviewBitmap = workingBitmap,
                    editableQuad = quad,
                    correctionMode = selectedIdCardDocumentType.correctionMode,
                    processedBitmap = processed,
                    previewBitmap = preparePreviewBitmap(processed)
                )
            }
        }

        fun handleIdCardCapture(bitmap: Bitmap) {
            isCapturing = true
            isProcessingPreview = true
            processingMessage = "正在处理第 ${idCardCapturedPages.size + 1}/${idCardCaptureTargetCount()} 张…"
            scope.launch {
                runCatching {
                    processIdCardPage(bitmap)
                }.onSuccess { page ->
                    val updatedPages = idCardCapturedPages + page
                    idCardCapturedPages = updatedPages
                    if (updatedPages.size >= idCardCaptureTargetCount()) {
                        idCardCompositePage = buildIdCardCompositePage(
                            pages = updatedPages,
                            documentType = selectedIdCardDocumentType,
                            layout = selectedIdCardLayout
                        )
                        stage = ScanStage.IdCardResult
                    } else {
                        Toast.makeText(
                            context,
                            "已完成第 ${updatedPages.size}/${idCardCaptureTargetCount()} 张",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }.onFailure { error ->
                    Log.e("JuZiScan", "ID capture process failed", error)
                    Toast.makeText(
                        context,
                        "证件识别失败，请重试",
                        Toast.LENGTH_LONG
                    ).show()
                }
                isCapturing = false
                isProcessingPreview = false
                processingMessage = "正在处理…"
            }
        }

        fun openPreviewAt(index: Int) {
            if (pages.isEmpty() || isCapturing) return
            val targetIndex = index.coerceIn(0, pages.lastIndex)
            activeIndex = targetIndex
            // 立即进入预览：已就绪页面直接浏览，未处理页面后台逐页补处理并自动更新，
            // 不再等待全部页面处理完成。
            stage = ScanStage.Preview
            submitPendingPages()
            promoteActivePage()
        }

        fun shareSinglePage(page: ScannedPage, fileType: ExportFileType = ExportFileType.JPG) {
            runCatching {
                executeShare(
                    context = context,
                    pages = listOf(page),
                    fileName = "萤火Scan_${System.currentTimeMillis()}",
                    fileType = fileType,
                    fileSize = ExportFileSize.Actual,
                    watermarkSettings = ExportWatermarkSettings()
                )
            }.onFailure { error ->
                Log.e("JuZiScan", "Single page share failed", error)
                Toast.makeText(
                    context,
                    "导出失败，请重试",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        fun startOcrRecognition(page: ScannedPage) {
            val bitmap = page.processedBitmap ?: page.previewBitmap ?: page.sourceBitmap
            ocrUiState = OcrUiState(isRecognizing = true)
            scope.launch {
                runCatching {
                    withContext(Dispatchers.Default) {
                        recognizeChineseText(context, bitmap)
                    }
                }.onSuccess { result ->
                    ocrUiState = OcrUiState(
                        isRecognizing = false,
                        recognizedText = result.text,
                        engineName = result.engineName,
                        blockCount = result.blockCount,
                        lineCount = result.lineCount,
                        paragraphCount = result.paragraphCount
                    )
                }.onFailure { error ->
                    Log.e("JuZiScan", "OCR recognition failed", error)
                    ocrUiState = OcrUiState(
                        isRecognizing = false,
                        errorMessage = if (error is TimeoutCancellationException) {
                            "识别等待超时，请重新识别或重新拍摄"
                        } else {
                            "文字识别失败，请重新识别或重新拍摄"
                        }
                    )
                }
            }
        }

        fun copyOcrText() {
            val text = ocrUiState.recognizedText.trim()
            if (text.isBlank()) {
                Toast.makeText(context, "当前没有可复制的识别文字", Toast.LENGTH_SHORT).show()
                return
            }
            val clipboardManager = context.getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboardManager == null) {
                Toast.makeText(context, "复制失败，剪贴板不可用", Toast.LENGTH_SHORT).show()
                return
            }
            clipboardManager.setPrimaryClip(ClipData.newPlainText("OCR Text", text))
            Toast.makeText(context, "识别文字已复制", Toast.LENGTH_SHORT).show()
        }

        fun updateOcrText(text: String) {
            ocrUiState = ocrUiState.copy(
                recognizedText = text,
                errorMessage = null
            )
        }

        fun retakeOcrCapture() {
            pendingSingleResult = null
            ocrUiState = OcrUiState()
            currentOcrRecordId = null
            clearEditorAfterUse()
            currentTask = CaptureTask.Ocr
            stage = ScanStage.Capture
        }

        fun exportOcrWord() {
            val text = ocrUiState.recognizedText.trim()
            if (text.isBlank()) {
                Toast.makeText(context, "当前没有可导出的识别文字", Toast.LENGTH_SHORT).show()
                return
            }
            runCatching {
                executeShareWordDocument(
                    context = context,
                    fileName = "萤火Scan_文字识别_${System.currentTimeMillis()}",
                    text = text
                )
            }.onFailure { error ->
                Log.e("JuZiScan", "Export OCR Word failed", error)
                Toast.makeText(
                    context,
                    "导出 Word 失败，请重试",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        fun exportOcrTxt() {
            val text = ocrUiState.recognizedText.trim()
            if (text.isBlank()) {
                Toast.makeText(context, "当前没有可导出的识别文字", Toast.LENGTH_SHORT).show()
                return
            }
            runCatching {
                executeSharePlainText(
                    context = context,
                    fileName = "萤火Scan_文字识别_${System.currentTimeMillis()}",
                    text = text
                )
            }.onFailure { error ->
                Log.e("JuZiScan", "Export OCR TXT failed", error)
                Toast.makeText(
                    context,
                    "导出 TXT 失败，请重试",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        fun triggerCapture() {
            val capture = imageCapture ?: return
            if (currentTask == CaptureTask.Document) refreshDocumentStateIfNeeded()
            if (currentTask == CaptureTask.Document && documentSaver.hasPendingChanges(currentDocumentRecordId)) {
                Toast.makeText(context, "请先重试未保存的照片", Toast.LENGTH_SHORT).show()
                return
            }
            if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
            if (!tryBeginCapture()) return
            val taskAtCapture = currentTask
            val styleAtCapture = defaultImageStyle.id
            if (taskAtCapture == CaptureTask.Document) {
                val documentId = ensureDocumentId()
                val capturedPages = pages.toList()
                val replacement = replacingIndex
                val pageId = replacement?.let { capturedPages.getOrNull(it)?.id } ?: java.util.UUID.randomUUID().toString()
                val revision = java.util.UUID.randomUUID().toString()
                try {
                    val file = historyRepository.captureFile(documentId, pageId, revision)
                    capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), captureExecutor,
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                try {
                                    java.io.FileOutputStream(file, true).use { it.fd.sync() }
                                    check(file.length() > 0) { "原图保存失败" }
                                    val thumbnail = requireNotNull(decodeBitmapFromFileBounded(file.path, 640))
                                    val page = ScannedPage(id = pageId, contentRevision = revision,
                                        sourceBitmap = thumbnail, previewBitmap = thumbnail, sourceImagePath = file.path,
                                        imageStyle = styleAtCapture)
                                    val saved = documentSaver.addPages(documentId, listOf(page),
                                        replacement?.let { capturedPages.getOrNull(it)?.id })
                                    val persisted = saved.pages
                                    mainExecutor.execute {
                                        if (screenActive.get() && currentDocumentRecordId == documentId && stage == ScanStage.Capture) {
                                            pages = persisted
                                            documentVersion = saved.updatedAtMillis
                                            documentSaver.attach(documentId, saved)
                                            activeIndex = replacement?.coerceIn(0, persisted.lastIndex) ?: persisted.lastIndex
                                            replacingIndex = null
                                            isCapturing = false
                                            autoCaptureUiState = AutoCaptureUiState(hintText = "已拍摄 ${persisted.size} 页，请放入下一页")
                                            Toast.makeText(context, "已拍摄 ${persisted.size} 页", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } catch (error: Exception) {
                                    documentSaver.reportFailure(documentId, error)
                                    mainExecutor.execute {
                                        if (currentDocumentRecordId == documentId) isCapturing = false
                                        Toast.makeText(context, "照片未保存，请检查存储空间后重试", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                            override fun onError(exception: ImageCaptureException) {
                                documentSaver.reportFailure(documentId, exception)
                                mainExecutor.execute {
                                    if (currentDocumentRecordId == documentId) isCapturing = false
                                    Toast.makeText(context, "照片未保存，请重试", Toast.LENGTH_LONG).show()
                                }
                            }
                        })
                } catch (error: Exception) {
                    isCapturing = false
                    documentSaver.reportFailure(documentId, error)
                }
                return
            }
            try {
                capture.takePicture(
                    captureExecutor,
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            try {
                                val rotationDegrees = image.imageInfo.rotationDegrees.toFloat()
                                val bitmap = imageProxyToBitmap(image,
                                    if (taskAtCapture == CaptureTask.Document) DOCUMENT_INPUT_MAX_SIDE else SCAN_INPUT_MAX_SIDE,
                                    jpegQuality = if (taskAtCapture == CaptureTask.Document) 95 else 75)
                                if (bitmap == null) {
                                    mainExecutor.execute {
                                        isCapturing = false
                                        Toast.makeText(context, "拍照失败，请重试", Toast.LENGTH_SHORT).show()
                                    }
                                    return
                                }

                                val rotated = SimpleDocumentScanner.rotate(bitmap, rotationDegrees)
                                if (rotated !== bitmap && !bitmap.isRecycled) {
                                    bitmap.recycle()
                                }
                                val thumbnail = if (taskAtCapture == CaptureTask.Document) {
                                    preparePreviewBitmap(rotated)
                                } else null
                                mainExecutor.execute {
                                    isCapturing = false
                                    if (stage != ScanStage.Capture || currentTask != taskAtCapture) {
                                        thumbnail?.takeIf { it !== rotated && !it.isRecycled }?.recycle()
                                        if (!rotated.isRecycled) rotated.recycle()
                                    } else if (taskAtCapture.entry == ScanEntry.IdCard) {
                                        handleIdCardCapture(rotated)

                                    } else {
                                        pushBitmapToEdgeReview(rotated)
                                    }
                                }
                            } catch (t: Throwable) {
                                Log.e("JuZiScan", "onCaptureSuccess failed", t)
                                mainExecutor.execute {
                                    isCapturing = false
                                    Toast.makeText(
                                        context,
                                        "拍照失败，请重试",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            } finally {
                                runCatching { image.close() }
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Log.e("JuZiScan", "ImageCapture onError", exception)
                            mainExecutor.execute {
                                isCapturing = false
                                Toast.makeText(
                                    context,
                                    "拍照失败，请重试",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                )
            } catch (error: Exception) {
                isCapturing = false
                Log.e("JuZiScan", "Unable to start capture", error)
                Toast.makeText(context, "拍照失败，请重试", Toast.LENGTH_SHORT).show()
            }
        }

        val multiPhotoPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickMultipleVisualMedia()
        ) { uris ->
            if (uris.isEmpty()) return@rememberLauncherForActivityResult
            val taskAtImport = currentTask
            val styleAtImport = defaultImageStyle.id
            val importId = if (taskAtImport == CaptureTask.Document) ensureDocumentId() else null
            val importSession = documentUiSession
            val existingPages = pages.toList()
            val replacementAtImport = replacingIndex
            isCapturing = true
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        uris.mapNotNull { uri ->
                            loadBitmapFromUri(context, uri,
                                if (taskAtImport == CaptureTask.Document) DOCUMENT_INPUT_MAX_SIDE else SCAN_INPUT_MAX_SIDE)
                                ?.let { bitmap -> uri to bitmap }
                        }
                    }
                }.onSuccess { results ->
                    if (results.isEmpty()) {
                        isCapturing = false
                        Toast.makeText(context, "选图失败，请重试", Toast.LENGTH_SHORT).show()
                        return@onSuccess
                    }

                    if (taskAtImport == CaptureTask.Document) {
                        val id = requireNotNull(importId)
                        val replacement = replacementAtImport
                        val incoming = results.mapIndexed { index, (_, bitmap) -> ScannedPage(
                            id = if (index == 0) replacement?.let { existingPages.getOrNull(it)?.id }
                                ?: java.util.UUID.randomUUID().toString() else java.util.UUID.randomUUID().toString(),
                            sourceBitmap = bitmap, previewBitmap = preparePreviewBitmap(bitmap, 640), imageStyle = styleAtImport) }
                        scope.launch {
                            try {
                                val restored = withContext(NonCancellable + Dispatchers.IO) {
                                    documentSaver.addPages(id, incoming, replacement?.let { existingPages.getOrNull(it)?.id })
                                }
                                if (screenActive.get() && currentDocumentRecordId == id && documentUiSession == importSession) {
                                    pages = restored.pages
                                    documentVersion = restored.updatedAtMillis
                                    documentSaver.attach(id, restored)
                                    isCapturing = false
                                    replacingIndex = null
                                    openPreviewAt(restored.pages.lastIndex)
                                }
                            } catch (error: Exception) {
                                if (error is CancellationException) throw error
                                if (currentDocumentRecordId == id && documentUiSession == importSession) isCapturing = false
                                Toast.makeText(context, "图片未保存，请检查存储空间后重试", Toast.LENGTH_LONG).show()
                            }
                        }
                        return@onSuccess
                    }

                    if (currentTask.entry == ScanEntry.IdCard) {
                        isCapturing = false
                        handleIdCardCapture(results.first().second)
                    } else if (results.size == 1) {
                        pushBitmapToEdgeReview(results.first().second)
                    } else {
                        val newPages = results.map { (_, bitmap) ->
                            ScannedPage(
                                sourceBitmap = bitmap,
                                previewBitmap = preparePreviewBitmap(bitmap)
                            )
                        }
                        val updatedPages = pages + newPages
                        pages = updatedPages
                        activeIndex = updatedPages.lastIndex
                        isCapturing = false
                        if (results.size < uris.size) {
                            Toast.makeText(
                                context,
                                "已导入 ${results.size} 张图片",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        openPreviewAt(activeIndex)
                    }
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    if (documentUiSession != importSession) return@onFailure
                    isCapturing = false
                    Log.e("JuZiScan", "Multi photo picker decode failed", error)
                    Toast.makeText(
                        context,
                        "选图失败，请重试",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        val singlePhotoPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            isCapturing = true
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        loadBitmapFromUri(context, uri,
                            if (currentTask == CaptureTask.Document) DOCUMENT_INPUT_MAX_SIDE else SCAN_INPUT_MAX_SIDE)
                    }
                }.onSuccess { bitmap ->
                    if (bitmap == null) {
                        isCapturing = false
                        Toast.makeText(context, "选图失败，请重试", Toast.LENGTH_SHORT).show()
                    } else {
                        isCapturing = false
                        if (currentTask.entry == ScanEntry.IdCard) {
                            handleIdCardCapture(bitmap)
                        } else {
                            pushBitmapToEdgeReview(bitmap)
                        }
                    }
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    isCapturing = false
                    Log.e("JuZiScan", "Single photo picker decode failed", error)
                    Toast.makeText(
                        context,
                        "选图失败，请重试",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        val avatarPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri == null || currentSession == null) return@rememberLauncherForActivityResult
            profileBusy = true
            profileErrorMessage = null
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val avatarBytes = prepareAvatarJpeg(context, uri)
                            ?: throw IllegalArgumentException("无法读取所选图片")
                        backendClient.uploadAvatar(avatarBytes)
                    }
                }.onSuccess { user ->
                    authSessionStore.session?.let { session ->
                        authSessionStore.save(session.copy(user = user))
                    }
                    profileBusy = false
                    Toast.makeText(context, "头像已更新", Toast.LENGTH_SHORT).show()
                }.onFailure { error ->
                    profileBusy = false
                    Toast.makeText(context, error.message ?: "头像上传失败", Toast.LENGTH_LONG).show()
                }
            }
        }

        fun finishDocument() {
            val origin = documentReturnStage
            documentSaver.detach(currentDocumentRecordId)
            returnToHome()
            stage = origin
            refreshHistoryRecords()
        }

        fun documentActionBlocked(): Boolean = isProcessingPreview ||
            documentSaver.hasPendingChanges(currentDocumentRecordId) || refreshDocumentStateIfNeeded()

        fun leaveDocumentPreview() {
            if (openedFromHistory) finishDocument() else showExitDialog = true
        }

        fun closeCapture() {
            if (isCapturing || isProcessingPreview) return
            when {
                replacingIndex != null -> {
                    replacingIndex = null
                    clearEditorAfterUse()
                    stage = ScanStage.Preview
                }
                currentTask.entry == ScanEntry.IdCard && !showIdCardGuideOverlay -> showIdCardGuideOverlay = true
                currentTask.entry == ScanEntry.IdCard && idCardCapturedPages.isNotEmpty() && idCardCompositePage != null -> stage = ScanStage.IdCardResult
                currentTask == CaptureTask.Document && pages.isNotEmpty() -> openPreviewAt(pages.lastIndex)
                currentTask == CaptureTask.Document -> finishDocument()
                else -> returnToEntry()
            }
        }

        fun deleteCurrentDocument() {
            val id = currentDocumentRecordId
            if (id == null) { finishDocument(); return }
            isProcessingPreview = true
            processingMessage = "正在删除文档…"
            scope.launch {
                try {
                    withContext(NonCancellable + Dispatchers.IO) { historyRepository.deleteRecord(id) }
                    finishDocument()
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    Log.e("JuZiScan", "Delete document failed", error)
                    Toast.makeText(context, "删除失败，请重试", Toast.LENGTH_LONG).show()
                } finally {
                    isProcessingPreview = false
                    processingMessage = "正在处理…"
                }
            }
        }

        BackHandler(enabled = isProcessingPreview || stage in listOf(
            ScanStage.Capture, ScanStage.EdgeReview, ScanStage.ShareSave,
            ScanStage.OcrResult, ScanStage.IdCardResult, ScanStage.TableResult,
            ScanStage.WatermarkTool, ScanStage.ImageStitchTool, ScanStage.ImageToPdfTool,
            ScanStage.ImageCompressTool, ScanStage.QrCodeTool, ScanStage.WiFiTransferTool, ScanStage.AllTools
        ) || (currentTask == CaptureTask.Document && stage in listOf(
            ScanStage.Preview, ScanStage.Capture, ScanStage.EdgeReview, ScanStage.ImageProcessing, ScanStage.Reorder, ScanStage.ShareSave
        ))) {
            if (stage == ScanStage.Preview || !isProcessingPreview) {
                when (stage) {
                    ScanStage.Preview -> leaveDocumentPreview()
                    ScanStage.Capture -> closeCapture()
                    ScanStage.OcrResult, ScanStage.IdCardResult, ScanStage.TableResult -> showExitDialog = true
                    ScanStage.WatermarkTool, ScanStage.ImageStitchTool, ScanStage.ImageToPdfTool,
                    ScanStage.ImageCompressTool, ScanStage.QrCodeTool, ScanStage.WiFiTransferTool -> returnToEntry()
                    ScanStage.AllTools -> stage = ScanStage.Home
                    ScanStage.ImageProcessing -> stage = ScanStage.EdgeReview
                    ScanStage.EdgeReview -> {
                        clearEditorAfterUse()
                        stage = if (replacingIndex != null) ScanStage.Preview else ScanStage.Capture
                        replacingIndex = null
                    }
                    ScanStage.Reorder -> stage = ScanStage.Preview
                    ScanStage.ShareSave -> stage = shareSaveReturnStage
                    else -> Unit
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            DisposableEffect(imageCapture, boundCamera, flashMode) {
                imageCapture?.flashMode = flashMode.imageCaptureMode
                val camera = boundCamera ?: return@DisposableEffect onDispose { }
                if (!camera.cameraInfo.hasFlashUnit()) {
                    flashMode = FlashModeOption.Off
                    return@DisposableEffect onDispose { }
                }
                var requestActive = true
                val requestedMode = flashMode
                val request = camera.cameraControl.enableTorch(requestedMode == FlashModeOption.On)
                request.addListener({
                    runCatching { request.get() }.onFailure { error ->
                        if (requestActive && boundCamera === camera && cameraResumed &&
                            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                            stage == ScanStage.Capture && flashMode == requestedMode) {
                            Log.e("JuZiScan", "Flash control failed", error)
                            flashMode = FlashModeOption.Off
                            Toast.makeText(context, "闪光灯切换失败，请重试", Toast.LENGTH_SHORT).show()
                        }
                    }
                }, mainExecutor)
                onDispose { requestActive = false }
            }

            when (stage) {
                ScanStage.Home -> {
                    ScanHomeScreen(
                        currentTab = currentHomeTab,
                        isLoggedIn = currentSession != null,
                        accountDisplayName = currentSession?.user?.displayName ?: "点击登录/注册",
                        accountAvatarUrl = backendClient.resolveMediaUrl(currentSession?.user?.avatarUrl),
                        accountSubtitle = currentSession?.user?.secondaryLabel ?: "登录后可管理账号及会员权益",
                        membershipStatus = membershipStatus,
                        recentRecords = historyRecords,
                        onTabChange = { currentHomeTab = it },
                        onEntryClick = { entry ->
                            startEntry(entry)
                        },
                        onImportClick = {
                            startEntry(ScanEntry.Document, openCapture = false)
                            multiPhotoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onLegalDocClick = { docType ->
                            activeLegalDoc = docType
                            legalReturnStage = ScanStage.Home
                            stage = ScanStage.LegalDocument
                        },
                        onViewAllDocsClick = {
                            stage = ScanStage.AllDocuments
                        },
                        onSettingsClick = {
                            stage = ScanStage.Settings
                        },
                        onAccountSecurityClick = {
                            if (currentSession == null) {
                                showAuthDialog = true
                            } else {
                                stage = ScanStage.AccountSecurity
                            }
                        },
                        onMoreToolsClick = {
                            stage = ScanStage.AllTools
                        },
                        onVipClick = {
                            vipReturnStage = ScanStage.Home
                            stage = ScanStage.Vip
                        },
                        onAvatarClick = {
                            if (!profileBusy) {
                                avatarPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        },
                        onNicknameClick = {
                            profileErrorMessage = null
                            showNicknameDialog = true
                        },
                        onAccountClick = {
                            if (currentSession == null) {
                                showAuthDialog = true
                            }
                        },
                        onLogoutClick = {
                            authSessionStore.clear()
                            membershipStatus = null
                            vipPurchaseResult = null
                            Toast.makeText(context, "已退出登录", Toast.LENGTH_SHORT).show()
                        },
                        onRecentOpen = { record -> openHistoryRecord(record) },
                        onRecentShare = { record -> shareHistoryRecord(record) },
                        onFeedbackClick = { showFeedbackDialog = true }
                    )
                }
                
                ScanStage.LegalDocument -> {
                    val docType = activeLegalDoc
                    if (docType != null) {
                        cn.yuandianai.juziscan.scan.ui.LegalDocumentScreen(
                            title = docType.title,
                            filename = docType.filename,
                            onBack = { stage = legalReturnStage }
                        )
                    }
                }

                ScanStage.AllDocuments -> {
                    AllDocumentsScreen(
                        records = historyRecords,
                        onBack = { stage = ScanStage.Home },
                        onOpenRecord = { record -> openHistoryRecord(record) },
                        onShareRecord = { record -> shareHistoryRecord(record) },
                        onDeleteRecord = { record -> deleteHistoryRecord(record) }
                    )
                }

                ScanStage.AllTools -> {
                    AllToolsScreen(
                        onBack = { stage = ScanStage.Home },
                        onToolClick = { entry ->
                            startEntry(entry, returnStage = ScanStage.AllTools)
                        }
                    )
                }

                ScanStage.Settings -> {
                    SettingsScreen(
                        scanQuality = scanQuality,
                        defaultImageStyle = defaultImageStyle,
                        onDefaultImageStyleChange = { style ->
                            defaultImageStyle = style
                            settingsPrefs.edit().putString(DocumentImageStyle.PREFERENCE_KEY, style.id).apply()
                        },
                        defaultExportFormat = defaultExportFormat,
                        cacheSize = cacheSize,
                        appVersion = BuildConfig.VERSION_NAME,
                        onBack = { stage = ScanStage.Home },
                        onScanQualityClick = {
                            scanQuality = when (scanQuality) {
                                "高清" -> "标准"
                                "标准" -> "节省空间"
                                else -> "高清"
                            }
                            settingsPrefs.edit().putString("scan_quality", scanQuality).apply()
                        },
                        onDefaultExportFormatClick = {
                            defaultExportFormat = when (defaultExportFormat) {
                                "PDF" -> "图片"
                                "图片" -> "TXT"
                                else -> "PDF"
                            }
                            settingsPrefs.edit().putString("default_export_format", defaultExportFormat).apply()
                        },
                        onClearCacheClick = {
                            context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                            cacheSize = formatCacheSize(cacheBytes())
                            Toast.makeText(context, "缓存已清理", Toast.LENGTH_SHORT).show()
                        },
                        onPermissionClick = onOpenAppSettings,
                        onLegalDocClick = { docType ->
                            if (docType == LegalDocType.AboutUs) {
                                Toast.makeText(context, "正在检查更新…", Toast.LENGTH_SHORT).show()
                                scope.launch {
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            backendClient.checkLatestVersion(BuildConfig.VERSION_CODE)
                                        }
                                    }.onSuccess { versionInfo ->
                                        if (versionInfo != null) {
                                            latestVersionInfo = versionInfo
                                        } else {
                                            Toast.makeText(context, "当前已是最新版本", Toast.LENGTH_SHORT).show()
                                        }
                                    }.onFailure { error ->
                                        Toast.makeText(context, "检查更新失败，请稍后重试", Toast.LENGTH_SHORT).show()
                                        Log.w("JuZiScan", "Manual check app version failed", error)
                                    }
                                }
                            } else {
                                activeLegalDoc = docType
                                legalReturnStage = ScanStage.Settings
                                stage = ScanStage.LegalDocument
                            }
                        }
                    )
                }

                ScanStage.AccountSecurity -> {
                    AccountSecurityScreen(
                        phone = currentSession?.user?.phone,
                        onBack = { stage = ScanStage.Home },
                        onBindPhoneClick = { showBindPhoneDialog = true },
                        onChangePassword = { oldPassword, newPassword ->
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        backendClient.changePassword(oldPassword, newPassword)
                                    }
                                }.onSuccess {
                                    Toast.makeText(context, "密码修改成功", Toast.LENGTH_SHORT).show()
                                }.onFailure { error ->
                                    Toast.makeText(context, error.message ?: "密码修改失败", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        onLogoutClick = {
                            authSessionStore.clear()
                            membershipStatus = null
                            vipPurchaseResult = null
                            stage = ScanStage.Home
                            Toast.makeText(context, "已退出登录", Toast.LENGTH_SHORT).show()
                        },
                        onDeleteAccount = { password ->
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        backendClient.deleteAccount(password)
                                    }
                                }.onSuccess {
                                    authSessionStore.clear()
                                    membershipStatus = null
                                    vipPurchaseResult = null
                                    stage = ScanStage.Home
                                    Toast.makeText(context, "账号已注销", Toast.LENGTH_SHORT).show()
                                }.onFailure { error ->
                                    Toast.makeText(context, error.message ?: "账号注销失败", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    )
                }

                ScanStage.Capture -> {
                    if (hasCameraPermission) {
                        CameraCaptureScreen(
                            onPreviewViewReady = { previewView = it },
                            captureTask = currentTask,
                            screenTitle = currentTask.title,
                            screenSubtitle = if (currentTask.entry == ScanEntry.IdCard) {
                                idCardCaptureHint()
                            } else {
                                currentTask.guidance
                            },
                            captureMode = captureMode,
                            flashMode = flashMode,
                            isCapturing = isCapturing || imageCapture == null,
                            autoCaptureUiState = autoCaptureUiState,
                            latestPreviewBitmap = pages.lastOrNull()?.previewBitmap ?: pages.lastOrNull()?.sourceBitmap,
                            latestPreviewRotationDegrees = pages.lastOrNull()?.rotationDegrees ?: 0f,
                            capturedCount = pages.size,
                            allowModeToggle = currentTask.supportsAutoCapture,
                            showCapturedPreview = currentTask == CaptureTask.Document,
                            showIdCardGuideOverlay = showIdCardGuideOverlay,
                            selectedIdCardDocumentType = selectedIdCardDocumentType,
                            selectedIdCardLayout = selectedIdCardLayout,
                            onModeSelected = {
                                if (!currentTask.supportsAutoCapture || isCapturing || isProcessingPreview) return@CameraCaptureScreen
                                captureMode = it
                                resetAutoCaptureHint()
                            },
                            onFlashModeClick = {
                                val camera = boundCamera ?: return@CameraCaptureScreen
                                if (isCapturing || isProcessingPreview) return@CameraCaptureScreen
                                if (!camera.cameraInfo.hasFlashUnit()) {
                                    Toast.makeText(context, "当前相机不支持闪光灯", Toast.LENGTH_SHORT).show()
                                    return@CameraCaptureScreen
                                }
                                val nextMode = flashMode.next()
                                flashMode = nextMode
                                imageCapture?.flashMode = nextMode.imageCaptureMode
                                Toast.makeText(context, nextMode.announcement, Toast.LENGTH_SHORT).show()
                            },
                            onSourceClick = {
                                if (isCapturing) return@CameraCaptureScreen
                                val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                if (currentTask == CaptureTask.Document) {
                                    multiPhotoPickerLauncher.launch(request)
                                } else {
                                    singlePhotoPickerLauncher.launch(request)
                                }
                            },
                            onCloseClick = { closeCapture() },
                            onCaptureClick = {
                                if (captureMode == CaptureModeOption.Manual || !currentTask.supportsAutoCapture) {
                                    triggerCapture()
                                }
                            },
                            onPreviewClick = {
                                if (currentTask == CaptureTask.Document && pages.isNotEmpty()) {
                                    openPreviewAt(pages.lastIndex)
                                }
                            },
                            onIdCardGuideDismiss = {
                                showIdCardGuideOverlay = false
                            },
                            onIdCardDocumentTypeSelected = {
                                selectedIdCardDocumentType = it
                            },
                            onIdCardLayoutSelected = {
                                selectedIdCardLayout = it
                            }
                        )
                    } else {
                        PermissionScreen(
                            isPermanentlyDenied = isCameraPermissionPermanentlyDenied,
                            onGrant = onRequestCameraPermission,
                            onOpenSettings = onOpenAppSettings,
                            onBack = {
                                replacingIndex = null
                                if (currentTask == CaptureTask.Document && pages.isNotEmpty()) {
                                    openPreviewAt(activeIndex)
                                } else if (currentTask == CaptureTask.Document) {
                                    finishDocument()
                                } else returnToEntry()
                            }
                        )
                    }
                }

                ScanStage.ImageProcessing -> {
                    val original = editingDocumentPage
                    val bitmap = reviewBitmap
                    if (original != null && bitmap != null) {
                        DocumentStyleScreen(bitmap, editableQuad, correctionMode, editingImageStyle,
                            onSelected = { editingImageStyle = it },
                            onBack = { stage = ScanStage.EdgeReview },
                            onDone = {
                                var quad = editableQuad.map { PointF(it.x, it.y) }
                                val turns = (normalizePageRotation(original.rotationDegrees) / 90f).toInt()
                                repeat((4 - turns) % 4) { quad = rotateQuadClockwise(quad) }
                                val edited = original.copy(editableQuad = quad, correctionMode = correctionMode,
                                    imageStyle = editingImageStyle.id, processedImagePath = null,
                                    processedBitmap = null, previewBitmap = null)
                                if (commitPageEdits(pages.map { if (it.id == original.id) edited else it })) {
                                    replacingIndex = null
                                    editingDocumentPage = null
                                    clearEditorAfterUse()
                                    stage = ScanStage.Preview
                                }
                            })
                    }
                }

                ScanStage.EdgeReview -> {
                    val originalBitmap = sourceBitmap
                    val reviewBmp = reviewBitmap
                    if (originalBitmap != null && reviewBmp != null) {
                        EdgeReviewScreen(
                            bitmap = reviewBmp,
                            quad = editableQuad,
                            onQuadChange = { editableQuad = it },
                            onBack = {
                                clearEditorAfterUse()
                                if (replacingIndex != null) {
                                    replacingIndex = null
                                    stage = ScanStage.Preview
                                } else {
                                    stage = ScanStage.Capture
                                    resetAutoCaptureHint()
                                }
                            },
                            onAuto = {
                                editableQuad = SimpleDocumentScanner.detectDocumentQuad(context, reviewBmp)
                                    ?: emptyList()
                                if (editableQuad.size != 4) {
                                    Toast.makeText(context, "模型未识别到边缘，请手动调整", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onFill = {
                                editableQuad = defaultDocumentQuad()
                            },
                            onNext = {
                                if (isProcessingPreview) return@EdgeReviewScreen
                                if (currentTask == CaptureTask.Document && editingDocumentPage != null) {
                                    stage = ScanStage.ImageProcessing
                                    return@EdgeReviewScreen
                                }
                                isProcessingPreview = true
                                processingMessage = "正在处理…"
                                scope.launch {
                                    runCatching {
                                        withContext(Dispatchers.Default) {
                                            if (currentTask == CaptureTask.Ocr) {
                                                // Preserve the camera image for OCR. Document
                                                // enhancement is intentionally aggressive and
                                                // can turn low-light vendor camera frames nearly
                                                // black, leaving ML Kit with no usable text.
                                                originalBitmap
                                            } else {
                                                val corrected = SimpleDocumentScanner.perspectiveCorrect(
                                                    originalBitmap,
                                                    editableQuad,
                                                    correctionMode,
                                                    maxOutputSide = if (currentTask == CaptureTask.Document) DOCUMENT_INPUT_MAX_SIDE else SCAN_INPUT_MAX_SIDE
                                                )
                                                var enhanced: Bitmap? = null
                                                try {
                                                    SimpleDocumentScanner.enhanceDocument(corrected).also { enhanced = it }
                                                } finally {
                                                    if (corrected !== originalBitmap && corrected !== enhanced && !corrected.isRecycled) corrected.recycle()
                                                }
                                            }
                                        }
                                    }.onSuccess { processed ->
                                        val previewBmp = preparePreviewBitmap(processed)
                                        val newPage = ScannedPage(
                                            id = replacingIndex?.let { pages.getOrNull(it)?.id } ?: java.util.UUID.randomUUID().toString(),
                                            sourceBitmap = originalBitmap,
                                            reviewBitmap = reviewBmp,
                                            editableQuad = editableQuad,
                                            correctionMode = correctionMode,
                                            processedBitmap = processed,
                                            previewBitmap = previewBmp
                                        )

                                        when {
                                            replacingIndex != null -> {
                                                val indexToReplace = replacingIndex!!
                                                pages = pages.toMutableList().apply {
                                                    set(indexToReplace, newPage)
                                                }
                                                activeIndex = indexToReplace
                                                replacingIndex = null
                                                stage = ScanStage.Preview
                                            }
                                            currentTask == CaptureTask.Document -> {
                                                pages = pages + newPage
                                                activeIndex = pages.lastIndex
                                                stage = ScanStage.Preview
                                            }
                                            currentTask == CaptureTask.Ocr -> {
                                                pendingSingleResult = newPage
                                                stage = ScanStage.OcrResult
                                                startOcrRecognition(newPage)
                                            }
                                            currentTask == CaptureTask.Table -> {
                                                pendingSingleResult = newPage
                                                stage = ScanStage.TableResult
                                            }
                                            currentTask.entry == ScanEntry.IdCard -> {
                                                idCardCapturedPages = idCardCapturedPages + newPage
                                                idCardCompositePage = buildIdCardCompositePage(
                                                    pages = idCardCapturedPages,
                                                    documentType = selectedIdCardDocumentType,
                                                    layout = selectedIdCardLayout
                                                )
                                                stage = ScanStage.IdCardResult
                                            }
                                        }

                                        clearEditorAfterUse()
                                    }.onFailure { error ->
                                        Log.e("JuZiScan", "Preview generation failed", error)
                                        Toast.makeText(
                                            context,
                                            "生成预览失败，请重试",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                    isProcessingPreview = false
                                }
                            }
                        )
                    }
                }

                ScanStage.Preview -> {
                    if (pages.isNotEmpty()) {
                        PreviewResultScreen(
                            pages = pages,
                            activeIndex = activeIndex,
                            pagePhases = pagePhases,
                            onActiveIndexChange = {
                                activeIndex = it
                                // 用户切换页面时提升该页处理优先级。
                                promoteActivePage()
                            },
                            onRetryPage = { currentDocumentRecordId?.let { id -> documentSaver.retry(id) } },
                            onBack = { leaveDocumentPreview() },
                            onAdd = {
                                if (documentActionBlocked()) return@PreviewResultScreen
                                clearEditorAfterUse()
                                currentTask = CaptureTask.Document
                                stage = ScanStage.Capture
                            },
                            onRotate = {
                                if (documentActionBlocked()) return@PreviewResultScreen
                                val currentIndex = activeIndex
                                val pageToRotate = pages.getOrNull(currentIndex) ?: return@PreviewResultScreen
                                commitPageEdits(pages.mapIndexed { idx, page ->
                                    if (idx == currentIndex) {
                                        pageToRotate.copy(
                                            rotationDegrees = normalizePageRotation(pageToRotate.rotationDegrees + 90f)
                                        )
                                    } else {
                                        page
                                    }
                                })
                            },
                            onPrint = {
                                val id = ensureDocumentId()
                                val session = documentUiSession
                                val printJobName = buildPrintJobName()
                                scope.launch {
                                    var snapshot: File? = null
                                    try {
                                        val pdf = withContext(Dispatchers.IO) {
                                            val source = historyRepository.ensureDocumentPdf(id)
                                            File.createTempFile("document-", "-print.pdf", context.cacheDir).also {
                                                snapshot = it
                                                source.copyTo(it, overwrite = true)
                                            }
                                        }
                                        if (currentDocumentRecordId == id && documentUiSession == session && stage == ScanStage.Preview) {
                                            executePrintFile(context, pdf, printJobName)
                                            snapshot = null // The system print adapter now owns this cache snapshot.
                                        }
                                    } catch (error: Exception) {
                                        if (error is CancellationException) throw error
                                        Toast.makeText(context, "打开打印预览失败，请重试", Toast.LENGTH_LONG).show()
                                    } finally { snapshot?.delete() }
                                }
                            },
                            onShare = {
                                shareSavePages = pages
                                shareSaveReturnStage = ScanStage.Preview
                                shareSaveDefaultWatermarkText = "仅供本人使用"
                                stage = ScanStage.ShareSave
                            },
                            onEdit = { if (!documentActionBlocked()) openEdgeReviewForPage(activeIndex) },
                            onReplace = {
                                if (documentActionBlocked()) return@PreviewResultScreen
                                replacingIndex = activeIndex
                                clearEditorAfterUse()
                                currentTask = CaptureTask.Document
                                stage = ScanStage.Capture
                            },
                            onDelete = {
                                if (documentActionBlocked()) return@PreviewResultScreen
                                if (pages.size == 1) {
                                    deleteCurrentDocument()
                                    return@PreviewResultScreen
                                }
                                val newPages = pages.toMutableList()
                                newPages.removeAt(activeIndex)
                                if (!commitPageEdits(newPages)) return@PreviewResultScreen
                                if (pages.isEmpty()) {
                                    finishDocument()
                                } else {
                                    activeIndex = activeIndex.coerceIn(0, pages.lastIndex)
                                }
                            },
                            onReorder = {
                                refreshDocumentStateIfNeeded()
                                // 拖动排序只改页面 ID 的顺序元数据：页面列表一出现即可进入，
                                // 不必等待增强、缓存写入或 PDF，保存中的事务也不阻塞排序。
                                stage = ScanStage.Reorder
                            }
                        )
                    } else if (isProcessingPreview) {
                        PreviewLoadingScreen()
                    } else {
                        finishDocument()
                    }
                }

                ScanStage.Reorder -> {
                    ReorderScreen(
                        pages = pages,
                        onCancel = {
                            stage = ScanStage.Preview
                        },
                        onDone = { reordered ->
                            if (reordered.isEmpty()) {
                                deleteCurrentDocument()
                            } else {
                                if (!commitPageEdits(reordered)) return@ReorderScreen
                                activeIndex = activeIndex.coerceIn(0, pages.lastIndex)
                                stage = ScanStage.Preview
                            }
                        }
                    )
                }

                ScanStage.ShareSave -> {
                    val exportPages = shareSavePages
                    if (exportPages.isEmpty()) {
                        if (currentTask == CaptureTask.Document) finishDocument() else returnToEntry()
                    } else {
                        ShareSaveScreen(
                            pages = exportPages,
                            isVip = isVipMember,
                            initialWatermarkText = shareSaveDefaultWatermarkText,
                            onBack = {
                                stage = shareSaveReturnStage
                            },
                            onRemoveBrandMarkClick = {
                                vipReturnStage = ScanStage.ShareSave
                                stage = ScanStage.Vip
                            },
                            onShare = { fileName, fileType, fileSize, watermarkSettings ->
                                val id = currentDocumentRecordId
                                val session = documentUiSession
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            val ready = if (id != null && currentTask == CaptureTask.Document)
                                                historyRepository.prepareDocumentPages(id) else exportPages
                                            executeShare(context, ready, fileName, fileType, fileSize, watermarkSettings)
                                        }
                                        if (currentDocumentRecordId == id && documentUiSession == session && stage == ScanStage.ShareSave)
                                            stage = shareSaveReturnStage
                                    } catch (error: Exception) {
                                        if (error is CancellationException) throw error
                                        Toast.makeText(context, "分享失败，请重试", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            onFeedbackClick = { showFeedbackDialog = true }
                        )
                    }
                }

                ScanStage.OcrResult -> {
                    val page = pendingSingleResult
                    if (page != null) {
                        LaunchedEffect(page.id, ocrUiState.recognizedText) {
                            val text = ocrUiState.recognizedText.trim()
                            if (text.isNotBlank()) {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        historyRepository.saveOrUpdateOcrRecord(
                                            recordId = currentOcrRecordId,
                                            page = page,
                                            text = text
                                        )
                                    }
                                }.onSuccess { record ->
                                    currentOcrRecordId = record.id
                                    refreshHistoryRecords()
                                }.onFailure { error ->
                                    Log.e("JuZiScan", "Save OCR history failed", error)
                                }
                            }
                        }
                        OcrResultScreen(
                            page = page,
                            ocrUiState = ocrUiState,
                            onBack = { showExitDialog = true },
                            onRetake = { retakeOcrCapture() },
                            onCopyText = { copyOcrText() },
                            onRetryRecognition = { startOcrRecognition(page) },
                            onTextChange = { updateOcrText(it) },
                            onExportWord = { exportOcrWord() },
                            onExportTxt = { exportOcrTxt() }
                        )
                    } else {
                        returnToEntry()
                    }
                }

                ScanStage.IdCardGuide -> {
                    stage = ScanStage.Capture
                }

                ScanStage.IdCardResult -> {
                    val compositePage = idCardCompositePage
                    if (compositePage != null && idCardCapturedPages.isNotEmpty()) {
                        LaunchedEffect(compositePage.id, currentIdCardRecordId) {
                            if (currentIdCardRecordId == null) {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        historyRepository.saveIdCardRecord(
                                            documentType = selectedIdCardDocumentType,
                                            compositePage = compositePage,
                                            sourcePageCount = idCardCapturedPages.size
                                        )
                                    }
                                }.onSuccess { record ->
                                    currentIdCardRecordId = record.id
                                    refreshHistoryRecords()
                                }.onFailure { error ->
                                    Log.e("JuZiScan", "Save ID card history failed", error)
                                }
                            }
                        }
                        IdCardResultScreen(
                            documentType = selectedIdCardDocumentType,
                            layout = selectedIdCardLayout,
                            pages = idCardCapturedPages,
                            compositePage = compositePage,
                            onBack = { showExitDialog = true },
                            onRotate = {
                                idCardCompositePage = compositePage.copy(
                                    rotationDegrees = normalizePageRotation(compositePage.rotationDegrees + 90f)
                                )
                            },
                            onRetakeAll = {
                                idCardCapturedPages = emptyList()
                                idCardCompositePage = null
                                currentIdCardRecordId = null
                                currentTask = CaptureTask.IdCardFront
                                showIdCardGuideOverlay = false
                                stage = ScanStage.Capture
                            },
                            onExport = {
                                shareSavePages = listOf(compositePage)
                                shareSaveReturnStage = ScanStage.IdCardResult
                                shareSaveDefaultWatermarkText = "仅供办理业务使用"
                                stage = ScanStage.ShareSave
                            }
                        )
                    } else {
                        returnToEntry()
                    }
                }

                ScanStage.TableResult -> {
                    val page = pendingSingleResult
                    if (page != null) {
                        TableResultScreen(
                            page = page,
                            onBack = { showExitDialog = true },
                            onRetake = {
                                pendingSingleResult = null
                                clearEditorAfterUse()
                                currentTask = CaptureTask.Table
                                stage = ScanStage.Capture
                            },
                            onSaveImage = { shareSinglePage(page, ExportFileType.PNG) }
                        )
                    } else {
                        returnToEntry()
                    }
                }
                
                ScanStage.WatermarkTool -> {
                    cn.yuandianai.juziscan.scan.ui.WatermarkToolScreen(onBack = { returnToEntry() }, openPickerOnEntry = entryReturnStage == ScanStage.AllTools)
                }
                ScanStage.ImageStitchTool -> {
                    cn.yuandianai.juziscan.scan.ui.ImageStitchToolScreen(onBack = { returnToEntry() }, openPickerOnEntry = entryReturnStage == ScanStage.AllTools)
                }
                ScanStage.ImageToPdfTool -> {
                    cn.yuandianai.juziscan.scan.ui.ImageToPdfToolScreen(onBack = { returnToEntry() }, openPickerOnEntry = entryReturnStage == ScanStage.AllTools)
                }
                ScanStage.ImageCompressTool -> {
                    cn.yuandianai.juziscan.scan.ui.ImageCompressToolScreen(onBack = { returnToEntry() }, openPickerOnEntry = entryReturnStage == ScanStage.AllTools)
                }
                ScanStage.QrCodeTool -> {
                    cn.yuandianai.juziscan.scan.ui.QrCodeToolScreen(onBack = { returnToEntry() })
                }
                ScanStage.WiFiTransferTool -> {
                    cn.yuandianai.juziscan.scan.ui.WiFiTransferToolScreen(onBack = { returnToEntry() })
                }
                ScanStage.Vip -> {
                    cn.yuandianai.juziscan.scan.ui.VipScreen(
                        onBack = { stage = vipReturnStage },
                        onLegalDocClick = { docType ->
                            activeLegalDoc = docType
                            stage = ScanStage.LegalDocument
                        },
                        plans = membershipPlans,
                        // The current client only implements Alipay. Do not expose a server-side
                        // channel that this build cannot actually complete.
                        paymentChannels = membershipPaymentChannels.filter { it == "alipay" },
                        membershipStatus = membershipStatus,
                        isPurchaseInFlight = vipPurchaseInFlight,
                        purchaseMessage = vipPurchaseMessage,
                        onPurchaseClick = { planCode ->
                            if (currentSession == null) {
                                pendingPurchasePlanCode = planCode
                                showAuthDialog = true
                            } else {
                                startVipPurchase(planCode)
                            }
                        }
                    )
                }
            }

            DisposableEffect(previewView, lifecycleOwner, stage, captureMode, currentTask, hasCameraPermission, cameraResumed) {
                val view = previewView
                val bindingActive = AtomicBoolean(true)
                val boundMode = captureMode
                val boundTask = currentTask
                val cameraProviderFuture = if (view != null && stage == ScanStage.Capture &&
                    hasCameraPermission && cameraResumed
                ) {
                    ProcessCameraProvider.getInstance(context)
                } else {
                    null
                }

                if (view != null && cameraProviderFuture != null) {
                    val listener = Runnable {
                        view.doOnLayout {
                            if (!bindingActive.get()) return@doOnLayout
                            resetAutoCaptureHint()
                            runCatching {
                                imageCapture = bindCameraUseCases(
                                    context = context,
                                    lifecycleOwner = lifecycleOwner,
                                    previewView = view,
                                    flashMode = flashMode,
                                    captureMode = boundMode,
                                    captureTask = boundTask,
                                    analysisExecutor = analysisExecutor,
                                    onAutoCaptureState = { state ->
                                        if (bindingActive.get() && stage == ScanStage.Capture &&
                                            captureMode == boundMode && currentTask == boundTask &&
                                            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                                        ) autoCaptureUiState = state
                                    },
                                    shouldAnalyze = {
                                        bindingActive.get() && stage == ScanStage.Capture &&
                                            currentTask == CaptureTask.Document && currentTask == boundTask &&
                                            captureMode == boundMode && !isCapturing && !isProcessingPreview &&
                                            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                                    },
                                    onCameraBound = { boundCamera = it },
                                    onAutoCaptureRequest = {
                                        if (bindingActive.get() && stage == ScanStage.Capture &&
                                            boundMode == CaptureModeOption.Auto &&
                                            currentTask == CaptureTask.Document &&
                                            captureMode == CaptureModeOption.Auto &&
                                            !isCapturing && !isProcessingPreview
                                        ) {
                                            triggerCapture()
                                        }
                                    }
                                )
                            }.onFailure { error ->
                                boundCamera = null
                                imageCapture = null
                                Log.e("JuZiScan", "Camera binding failed", error)
                                Toast.makeText(context, "相机启动失败，请返回后重试", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    cameraProviderFuture.addListener(listener, mainExecutor)
                }

                onDispose {
                    bindingActive.set(false)
                    boundCamera?.cameraControl?.enableTorch(false)
                    boundCamera = null
                    imageCapture = null
                    if (cameraProviderFuture != null) {
                        runCatching { cameraProviderFuture.get().unbindAll() }
                    }
                }
            }

            if (isProcessingPreview) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.42f))
                        .clickable { },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .background(Color.White, RoundedCornerShape(28.dp))
                            .fillMaxWidth(0.78f)
                            .padding(horizontal = 28.dp, vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        ProcessingSpinner()
                        Text(
                            text = processingMessage,
                            color = HpSmartColors.TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            if (showExitDialog) {
                ExitConfirmationDialog(
                    returnLabel = if (entryReturnStage == ScanStage.AllTools) "暂存并返回全部工具" else "暂存并返回首页",
                    onDismiss = { showExitDialog = false },
                    onRescan = {
                        showExitDialog = false
                        documentSaver.detach(currentDocumentRecordId)
                        startEntry(currentTask.entry, openCapture = true, returnStage = entryReturnStage)
                    },
                    onReturnHome = {
                        showExitDialog = false
                        if (currentTask == CaptureTask.Document) finishDocument() else returnToEntry()
                    }
                )
            }
            saveFailures.entries.firstOrNull()?.let { failure ->
                Column(modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                    .padding(16.dp).background(Color.White, RoundedCornerShape(16.dp)).padding(16.dp)) {
                    Text("文档未保存", color = HpSmartColors.TextPrimary, fontWeight = FontWeight.Bold)
                    Text("${failure.value}。已保存的页面和修改会保留。", color = HpSmartColors.TextSecondary)
                    Row {
                        TextButton(onClick = { documentSaver.retry(failure.key) }) { Text("重试") }
                        TextButton(onClick = { documentSaver.dismissFailure(failure.key) }) { Text("稍后处理") }
                    }
                }
            }
            if (showAuthDialog) {
                Dialog(
                    onDismissRequest = {
                        if (!authBusy) {
                            showAuthDialog = false
                            authErrorMessage = null
                            pendingPurchasePlanCode = null
                        }
                    },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        AuthDialog(
                            isBusy = authBusy,
                            errorMessage = authErrorMessage,
                            onDismiss = {
                                showAuthDialog = false
                                authErrorMessage = null
                                pendingPurchasePlanCode = null
                            },
                            onPasswordLogin = { identifier, password ->
                                authBusy = true
                                authErrorMessage = null
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        val session = backendClient.loginWithPassword(identifier, password)
                                        val latestUser = runCatching { backendClient.getCurrentUser() }
                                            .getOrDefault(session.user)
                                        session.copy(user = latestUser)
                                    }
                                }.onSuccess { session ->
                                    authSessionStore.save(session)
                                    authBusy = false
                                    showAuthDialog = false
                                    refreshMembershipStatus()
                                    refreshMembershipPlans()
                                    Toast.makeText(context, "登录成功", Toast.LENGTH_SHORT).show()
                                    pendingPurchasePlanCode?.let { planCode ->
                                        pendingPurchasePlanCode = null
                                        startVipPurchase(planCode)
                                    }
                                }.onFailure { error ->
                                    authBusy = false
                                    authErrorMessage = error.message ?: "登录失败"
                                }.map {}
                            },
                            onRegister = { username, password ->
                                authBusy = true
                                authErrorMessage = null
                                runCatching { withContext(Dispatchers.IO) { backendClient.register(username, password) } }
                                    .onSuccess { session ->
                                        authSessionStore.save(session)
                                        authBusy = false
                                        showAuthDialog = false
                                        Toast.makeText(context, "注册成功", Toast.LENGTH_SHORT).show()
                                        pendingPurchasePlanCode?.let { planCode ->
                                            pendingPurchasePlanCode = null
                                            startVipPurchase(planCode)
                                        }
                                    }.onFailure { authBusy = false; authErrorMessage = it.message ?: "注册失败" }
                                    .map {}
                            },
                            onSmsLogin = { phone, code ->
                                authBusy = true
                                authErrorMessage = null
                                runCatching { withContext(Dispatchers.IO) { backendClient.loginWithSms(phone, code) } }
                                    .onSuccess { session ->
                                        authSessionStore.save(session)
                                        authBusy = false
                                        showAuthDialog = false
                                        refreshMembershipStatus()
                                        pendingPurchasePlanCode?.let { planCode -> pendingPurchasePlanCode = null; startVipPurchase(planCode) }
                                    }.onFailure { authBusy = false; authErrorMessage = it.message ?: "登录失败" }
                                    .map {}
                            },
                            onSendSms = { phone ->
                                authBusy = true
                                runCatching { withContext(Dispatchers.IO) { backendClient.sendSmsCode(phone, "login") } }
                                    .onSuccess { authBusy = false; Toast.makeText(context, "验证码已发送", Toast.LENGTH_SHORT).show() }
                                    .onFailure { authBusy = false; authErrorMessage = it.message }
                                    .map {}
                            }
                        )
                    }
                }
            }
            if (showNicknameDialog && currentSession != null) {
                NicknameEditDialog(
                    currentNickname = currentSession.user.nickname.orEmpty(),
                    isBusy = profileBusy,
                    errorMessage = profileErrorMessage,
                    onDismiss = {
                        showNicknameDialog = false
                        profileErrorMessage = null
                    },
                    onConfirm = { nickname ->
                        profileBusy = true
                        profileErrorMessage = null
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    backendClient.updateNickname(nickname)
                                }
                            }.onSuccess { user ->
                                authSessionStore.session?.let { session ->
                                    authSessionStore.save(session.copy(user = user))
                                }
                                profileBusy = false
                                showNicknameDialog = false
                                Toast.makeText(context, "昵称已更新", Toast.LENGTH_SHORT).show()
                            }.onFailure { error ->
                                profileBusy = false
                                profileErrorMessage = error.message ?: "昵称修改失败"
                            }
                        }
                    }
                )
            }
            if (showBindPhoneDialog) {
                Dialog(
                    onDismissRequest = { if (!authBusy) { showBindPhoneDialog = false; pendingPurchasePlanCode = null } },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                        BindPhoneDialog(
                            isBusy = authBusy,
                            errorMessage = authErrorMessage,
                            onDismiss = { showBindPhoneDialog = false; pendingPurchasePlanCode = null },
                            onSendCode = { phone ->
                                authBusy = true
                                runCatching { withContext(Dispatchers.IO) { backendClient.sendSmsCode(phone, "bind_phone") } }
                                    .onSuccess { authBusy = false }.onFailure { authBusy = false; authErrorMessage = it.message }.map {}
                            },
                            onBind = { phone, code ->
                                authBusy = true
                                runCatching { withContext(Dispatchers.IO) { backendClient.bindPhone(phone, code) } }
                                    .onSuccess { user ->
                                        authSessionStore.session?.let { authSessionStore.save(it.copy(user = user)) }
                                        authBusy = false
                                        showBindPhoneDialog = false
                                        Toast.makeText(context, "手机号绑定成功", Toast.LENGTH_SHORT).show()
                                        pendingPurchasePlanCode?.let { planCode -> pendingPurchasePlanCode = null; startVipPurchase(planCode) }
                                    }.onFailure { authBusy = false; authErrorMessage = it.message }.map {}
                            }
                        )
                    }
                }
            }
            if (showFeedbackDialog) {
                Dialog(
                    onDismissRequest = { if (!feedbackBusy) showFeedbackDialog = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                        FeedbackDialog(
                            isBusy = feedbackBusy,
                            onDismiss = { showFeedbackDialog = false },
                            onSubmit = { content, contact ->
                                feedbackBusy = true
                                val result = runCatching {
                                    withContext(Dispatchers.IO) {
                                        backendClient.submitFeedback(content, contact)
                                    }
                                }
                                feedbackBusy = false
                                if (result.isSuccess) {
                                    Result.success(Unit)
                                } else {
                                    Result.failure(result.exceptionOrNull() ?: Exception("提交失败，请重试"))
                                }
                            }
                        )
                    }
                }
            }

            val versionInfo = latestVersionInfo
            if (versionInfo != null) {
                AlertDialog(
                    onDismissRequest = {
                        if (!versionInfo.isForceUpdate) {
                            latestVersionInfo = null
                        }
                    },
                    title = {
                        Text(
                            text = "发现新版本 v${versionInfo.versionName}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = HpSmartColors.TextPrimary
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "更新内容：",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = HpSmartColors.TextPrimary
                            )
                            Text(
                                text = versionInfo.updateLog,
                                fontSize = 14.sp,
                                color = HpSmartColors.TextSecondary,
                                lineHeight = 20.sp
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                runCatching {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(versionInfo.downloadUrl))
                                    context.startActivity(intent)
                                }.onFailure {
                                    Toast.makeText(context, "无法打开下载链接，请手动前往浏览器下载", Toast.LENGTH_LONG).show()
                                }
                            }
                        ) {
                            Text("立即更新", color = Color(0xFF0B63FF), fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = if (versionInfo.isForceUpdate) null else {
                        {
                            TextButton(
                                onClick = { latestVersionInfo = null }
                            ) {
                                Text("稍后再说", color = HpSmartColors.TextSecondary)
                            }
                        }
                    },
                    properties = DialogProperties(
                        dismissOnBackPress = !versionInfo.isForceUpdate,
                        dismissOnClickOutside = !versionInfo.isForceUpdate
                    )
                )
            }
        }
    }
}

private fun android.content.Context.findComponentActivity(): ComponentActivity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is ComponentActivity) return current
        current = current.baseContext
    }
    return null
}
@Composable
private fun PreviewLoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HpSmartColors.Background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ProcessingSpinner()
            Text(
                text = "正在整理扫描结果…",
                color = HpSmartColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ExitConfirmationDialog(
    returnLabel: String,
    onDismiss: () -> Unit,
    onRescan: () -> Unit,
    onReturnHome: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .background(Color.White, RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color(0xFFFFECEB), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    FireflyIcon(FireflyIconName.Warning, size = 28.dp, tint = androidx.compose.material3.MaterialTheme.colorScheme.error, contentDescription = null)
                }

                Text(
                    text = "退出当前扫描？",
                    color = HpSmartColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "退出后，当前已扫描的页面/识别结果将会被保存在最近文档。",
                    color = HpSmartColors.TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                ActionPillButton(
                    text = "继续浏览",
                    filled = true,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                )

                ActionPillButton(
                    text = "新的扫描",
                    filled = false,
                    onClick = onRescan,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clickable { onReturnHome() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = returnLabel,
                        color = Color(0xFFFF4D4F),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
