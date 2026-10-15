package cn.yuandianai.juziscan.scan

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.camera.core.ImageCapture
import androidx.camera.view.PreviewView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cn.yuandianai.juziscan.payment.MembershipPlan
import cn.yuandianai.juziscan.payment.MembershipStatus
import cn.yuandianai.juziscan.payment.VipPurchaseResult
import cn.yuandianai.juziscan.scanner.SimpleDocumentScanner

class ScanViewModel {
    var stage by mutableStateOf(ScanStage.Home)
    var currentHomeTab by mutableStateOf(HomeTab.Home)
    var currentTask by mutableStateOf(CaptureTask.Document)
    var captureMode by mutableStateOf(CaptureModeOption.Manual)
    var flashMode by mutableStateOf(FlashModeOption.Auto)
    var previewView by mutableStateOf<PreviewView?>(null)
    var imageCapture by mutableStateOf<ImageCapture?>(null)

    var sourceBitmap by mutableStateOf<Bitmap?>(null)
    var reviewBitmap by mutableStateOf<Bitmap?>(null)
    var editableQuad by mutableStateOf<List<PointF>>(emptyList())
    var correctionMode by mutableStateOf(SimpleDocumentScanner.CorrectionMode.AUTO)

    var isCapturing by mutableStateOf(false)
    var isProcessingPreview by mutableStateOf(false)
    var processingMessage by mutableStateOf("正在处理…")
    var autoCaptureUiState by mutableStateOf(AutoCaptureUiState())

    var pages by mutableStateOf<List<ScannedPage>>(emptyList())
    var activeIndex by mutableStateOf(0)
    var replacingIndex by mutableStateOf<Int?>(null)
    var pendingSingleResult by mutableStateOf<ScannedPage?>(null)
    var ocrUiState by mutableStateOf(OcrUiState())
    var shareSavePages by mutableStateOf<List<ScannedPage>>(emptyList())
    var shareSaveReturnStage by mutableStateOf(ScanStage.Preview)
    var shareSaveDefaultWatermarkText by mutableStateOf("仅供本人使用")
    var idCardCapturedPages by mutableStateOf<List<ScannedPage>>(emptyList())
    var idCardCompositePage by mutableStateOf<ScannedPage?>(null)
    var showIdCardGuideOverlay by mutableStateOf(true)
    var selectedIdCardDocumentType by mutableStateOf(IdCardDocumentType.IdentityCard)
    var selectedIdCardLayout by mutableStateOf(IdCardLayoutOption.VerticalPair)
    var activeLegalDoc by mutableStateOf<LegalDocType?>(null)
    var currentDocumentRecordId by mutableStateOf<String?>(null)
    var currentOcrRecordId by mutableStateOf<String?>(null)
    var currentIdCardRecordId by mutableStateOf<String?>(null)
    var vipPurchaseInFlight by mutableStateOf(false)
    var vipPurchaseMessage by mutableStateOf<String?>(null)
    var vipPurchaseResult by mutableStateOf<VipPurchaseResult?>(null)
    var membershipStatus by mutableStateOf<MembershipStatus?>(null)
    var membershipPlans by mutableStateOf<List<MembershipPlan>>(emptyList())
    var membershipPaymentChannels by mutableStateOf<List<String>>(emptyList())

    val isVipMember: Boolean
        get() = membershipStatus?.is_vip == true

    // Called on the main thread before submitting a CameraX request.
    fun tryBeginCapture(): Boolean {
        if (stage != ScanStage.Capture || isCapturing || isProcessingPreview) return false
        isCapturing = true
        return true
    }

    fun clearEditingState() {
        sourceBitmap = null
        reviewBitmap = null
        editableQuad = emptyList()
        correctionMode = SimpleDocumentScanner.CorrectionMode.AUTO
    }

    fun resetToCapture() {
        stage = ScanStage.Capture
        clearEditingState()
        replacingIndex = null
        pendingSingleResult = null
        ocrUiState = OcrUiState()
        shareSavePages = emptyList()
        shareSaveReturnStage = ScanStage.Preview
        shareSaveDefaultWatermarkText = "仅供本人使用"
        currentDocumentRecordId = null
        currentOcrRecordId = null
        currentIdCardRecordId = null
    }

    fun resetAutoCaptureHint() {
        autoCaptureUiState = if (currentTask == CaptureTask.Document) {
            AutoCaptureUiState(
                hintText = "将文档放入取景框内",
                stableProgress = 0f,
                documentDetected = false
            )
        } else {
            AutoCaptureUiState()
        }
    }

    fun startEntry(entry: ScanEntry, openCapture: Boolean = true) {
        pages = emptyList()
        activeIndex = 0
        replacingIndex = null
        pendingSingleResult = null
        ocrUiState = OcrUiState()
        shareSavePages = emptyList()
        shareSaveReturnStage = ScanStage.Preview
        shareSaveDefaultWatermarkText = "仅供本人使用"
        idCardCapturedPages = emptyList()
        idCardCompositePage = null
        showIdCardGuideOverlay = entry == ScanEntry.IdCard
        selectedIdCardDocumentType = IdCardDocumentType.IdentityCard
        selectedIdCardLayout = IdCardLayoutOption.VerticalPair
        currentDocumentRecordId = null
        currentOcrRecordId = null
        currentIdCardRecordId = null
        clearEditingState()
        currentTask = when (entry) {
            ScanEntry.Document -> CaptureTask.Document
            ScanEntry.Ocr -> CaptureTask.Ocr
            ScanEntry.IdCard -> CaptureTask.IdCardFront
            ScanEntry.Table -> CaptureTask.Table
            else -> CaptureTask.Document
        }
        captureMode = CaptureModeOption.Manual
        resetAutoCaptureHint()
        stage = when (entry) {
            ScanEntry.Watermark -> ScanStage.WatermarkTool
            ScanEntry.Stitch -> ScanStage.ImageStitchTool
            ScanEntry.ImageToPdf -> ScanStage.ImageToPdfTool
            ScanEntry.Compress -> ScanStage.ImageCompressTool
            ScanEntry.QrCode -> ScanStage.QrCodeTool
            ScanEntry.WiFiTransfer -> ScanStage.WiFiTransferTool
            else -> if (openCapture) ScanStage.Capture else ScanStage.Home
        }
    }

    fun returnToHome() {
        pages = emptyList()
        activeIndex = 0
        replacingIndex = null
        pendingSingleResult = null
        ocrUiState = OcrUiState()
        shareSavePages = emptyList()
        shareSaveReturnStage = ScanStage.Preview
        shareSaveDefaultWatermarkText = "仅供本人使用"
        idCardCapturedPages = emptyList()
        idCardCompositePage = null
        showIdCardGuideOverlay = true
        selectedIdCardDocumentType = IdCardDocumentType.IdentityCard
        selectedIdCardLayout = IdCardLayoutOption.VerticalPair
        currentDocumentRecordId = null
        currentOcrRecordId = null
        currentIdCardRecordId = null
        clearEditingState()
        currentTask = CaptureTask.Document
        captureMode = CaptureModeOption.Manual
        autoCaptureUiState = AutoCaptureUiState()
        stage = ScanStage.Home
    }
}
