package cn.yuandianai.juziscan.scan

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.camera.core.ImageCapture
import cn.yuandianai.juziscan.scanner.SimpleDocumentScanner
import java.io.File

enum class ScanStage {
    Home,
    Capture,
    EdgeReview,
    Preview,
    Reorder,
    ShareSave,
    OcrResult,
    IdCardGuide,
    IdCardResult,
    TableResult,
    WatermarkTool,
    ImageStitchTool,
    ImageToPdfTool,
    ImageCompressTool,
    QrCodeTool,
    WiFiTransferTool,
    LegalDocument,
    AllDocuments,
    AllTools,
    Settings,
    AccountSecurity,
    Vip
}

enum class HomeTab {
    Home, Mine
}

enum class LegalDocType(val title: String, val filename: String) {
    PrivacyPolicy("隐私政策", "privacy_policy.txt"),
    UserAgreement("用户服务协议", "user_agreement.txt"),
    MemberServiceAgreement("会员服务协议", "member_service_agreement.txt"),
    AboutUs("关于我们", "about_us.txt")
}

enum class ScanEntry(
    val title: String,
    val subtitle: String
) {
    Document("文件扫描", "快速生成PDF扫描件"),
    Ocr("文字识别", "提取图片中的文字"),
    IdCard("证件扫描", "生成清晰电子复印件"),
    Table("表格识别", "识别并导出Excel"),
    Watermark("添加水印", "保护图片隐私"),
    Stitch("长图拼接", "多图合并为一长图"),
    ImageToPdf("图片转PDF", "图片快速打包PDF"),
    Compress("图片压缩", "缩小图片文件体积"),
    QrCode("二维码工具", "扫码与二维码生成"),
    WiFiTransfer("WiFi传图", "电脑免数据线下载")
}

enum class CaptureTask(
    val entry: ScanEntry,
    val title: String,
    val guidance: String,
    val supportsAutoCapture: Boolean
) {
    Document(
        entry = ScanEntry.Document,
        title = "文件扫描",
        guidance = "将文档放入取景框内",
        supportsAutoCapture = true
    ),
    Ocr(
        entry = ScanEntry.Ocr,
        title = "文字识别",
        guidance = "将文字放入取景框内",
        supportsAutoCapture = false
    ),
    IdCardFront(
        entry = ScanEntry.IdCard,
        title = "证件扫描",
        guidance = "请将证件正面放入取景框内",
        supportsAutoCapture = false
    ),
    IdCardBack(
        entry = ScanEntry.IdCard,
        title = "证件扫描",
        guidance = "请将证件背面放入取景框内",
        supportsAutoCapture = false
    ),
    Table(
        entry = ScanEntry.Table,
        title = "表格识别",
        guidance = "将表格放入取景框内",
        supportsAutoCapture = false
    )
}

data class ScannedPage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sourceBitmap: Bitmap,
    val reviewBitmap: Bitmap? = null,
    val editableQuad: List<PointF> = emptyList(),
    val correctionMode: SimpleDocumentScanner.CorrectionMode = SimpleDocumentScanner.CorrectionMode.AUTO,
    val processedBitmap: Bitmap? = null,
    val previewBitmap: Bitmap? = null,
    val rotationDegrees: Float = 0f,
    // File-backed pages retain only a small display bitmap until editing/export.
    val sourceImagePath: String? = null,
    val processedImagePath: String? = null,
    val detectedQuad: List<PointF> = emptyList(),
    val contentRevision: String = java.util.UUID.randomUUID().toString(),
    val imageStyle: String = "default"
) {
    val isProcessed: Boolean get() = processedBitmap != null || processedImagePath != null

    /**
     * 处理结果是否真的可取用：内存位图存在，或处理图文件仍在磁盘上。
     * 处理图属于可重建缓存，被清理后页面必须退回「待处理」，由后台队列重新补齐，
     * 而不是永远停在一个指向空文件的「已处理」状态。
     */
    val hasProcessedOutput: Boolean
        get() = processedBitmap != null || processedImagePath?.let { File(it).isFile } == true
}

/** Paths and Bitmap identities are deliberately excluded: reloading is not an edit. */
fun documentFingerprint(pages: List<ScannedPage>): String {
    val value = pages.joinToString(";") { page ->
        listOf(page.id, page.contentRevision, normalizePageRotation(page.rotationDegrees),
            page.correctionMode.name, page.imageStyle,
            page.editableQuad.joinToString(",") { "${it.x}:${it.y}" }).joinToString("|")
    }
    return java.security.MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

data class OcrUiState(
    val isRecognizing: Boolean = false,
    val recognizedText: String = "",
    val errorMessage: String? = null,
    val engineName: String? = null,
    val blockCount: Int = 0,
    val lineCount: Int = 0,
    val paragraphCount: Int = 0
)

enum class ExportFileType(
    val label: String,
    val extension: String,
    val hint: String
) {
    PDF("PDF文档", "pdf", "适合多页扫描件保存与发送，全平台兼容。"),
    JPG("图片（JPG）", "jpg", "适合日常分享，体积较小，兼容性最好。"),
    PNG("图片（PNG）", "png", "画质更完整，适合保留细节，但文件通常更大。"),
    TIF("图片（TIFF）", "tif", "专业无损存档，适合印刷与高质量保存。"),
    HEIF("图片（HEIF，更省空间）", "heif", "省空间、画质优秀，老旧设备可能不兼容。")
}

enum class ExportFileSize(val label: String, val scale: Float, val quality: Int) {
    Actual("实际", 1.0f, 95),
    Large("大", 0.85f, 80),
    Medium("中", 0.70f, 65),
    Small("小", 0.50f, 50)
}

enum class WatermarkStyleOption(val label: String) {
    Center("居中"),
    DiagonalRepeat("斜向平铺")
}

enum class WatermarkStrengthOption(val label: String, val alpha: Int) {
    Low("低", 36),
    Medium("中", 52),
    High("高", 72)
}

data class ExportWatermarkSettings(
    val enabled: Boolean = false,
    val text: String = "",
    val style: WatermarkStyleOption = WatermarkStyleOption.DiagonalRepeat,
    val strength: WatermarkStrengthOption = WatermarkStrengthOption.Medium,
    val brandMarkEnabled: Boolean = false
) {
    val resolvedText: String
        get() = text.trim().ifBlank { "仅供本人使用" }
}

enum class CaptureModeOption(val label: String) {
    Manual("智能手拍"),
    Auto("自动拍")
}

data class IdCardDocumentType(
    val key: String,
    val label: String,
    val widthMm: Float,
    val heightMm: Float,
    val category: String,
    val isCommon: Boolean = false,
    val correctionMode: SimpleDocumentScanner.CorrectionMode = SimpleDocumentScanner.CorrectionMode.ID_CARD
) {
    val normalizedAspect: Float
        get() = kotlin.math.max(widthMm, heightMm) / kotlin.math.min(widthMm, heightMm)

    val sizeLabel: String
        get() = "${formatMillimeter(widthMm)}mm × ${formatMillimeter(heightMm)}mm"

    fun matches(query: String): Boolean {
        val normalized = query.trim()
        if (normalized.isBlank()) return true
        val compact = normalized.lowercase()
        return label.lowercase().contains(compact) || category.lowercase().contains(compact)
    }

    companion object {
        private const val COMMON = "常用证件"
        private const val LIVELIHOOD = "民生与日常证件"
        private const val EDUCATION = "教育与学生证件"
        private const val CHILD = "儿童证件"
        private const val PROFESSION = "职业资格证"
        private const val OTHER = "其他证件"

        val IdentityCard = preset("identity_card", "身份证", 85.6f, 54f, COMMON, isCommon = true)
        val BankCard = preset("bank_card", "银行卡", 85.6f, 54f, COMMON, isCommon = true)
        val HouseholdRegister = preset("household_register", "户口本", 143f, 105f, COMMON, isCommon = true)
        val Invoice = preset(
            "invoice",
            "发票",
            240f,
            140f,
            COMMON,
            isCommon = true,
            correctionMode = SimpleDocumentScanner.CorrectionMode.INVOICE
        )
        val Passport = preset("passport", "护照", 125f, 176f, COMMON, isCommon = true)
        val DriverLicense = preset("driver_license", "驾照", 190f, 66f, COMMON, isCommon = true)
        val VehicleLicense = preset("vehicle_license", "行驶证", 88f, 60f, COMMON, isCommon = true)
        val PropertyCertificate = preset("property_certificate", "房产证", 165.5f, 237.5f, COMMON, isCommon = true)
        val BusinessLicense = preset(
            "business_license",
            "营业执照",
            210f,
            297f,
            COMMON,
            isCommon = true,
            correctionMode = SimpleDocumentScanner.CorrectionMode.DOCUMENT
        )
        val Diploma = preset("diploma", "毕业证", 165f, 235f, COMMON, isCommon = true)
        val DegreeCertificate = preset("degree_certificate", "学位证", 297f, 210f, COMMON, isCommon = true)

        val allEntries: List<IdCardDocumentType> = buildList {
            addAll(
                listOf(
                    IdentityCard,
                    BankCard,
                    HouseholdRegister,
                    Invoice,
                    Passport,
                    DriverLicense,
                    VehicleLicense,
                    PropertyCertificate,
                    BusinessLicense,
                    Diploma,
                    DegreeCertificate
                )
            )
            addAll(
                createGroup(
                    LIVELIHOOD,
                    "居住证" to (85.6f to 53.98f),
                    "健康证" to (85.6f to 53.98f),
                    "结婚证" to (188f to 128f),
                    "离婚证" to (188f to 128f),
                    "社保卡" to (85.6f to 53.98f),
                    "市民卡" to (85.6f to 53.98f),
                    "军官卡" to (150f to 110f),
                    "残疾人证" to (150f to 103f),
                    "存折" to (110f to 70f),
                    "医保卡" to (85.6f to 53.98f),
                    "病历本" to (210f to 168.5f),
                    "台湾通行证" to (85.6f to 53.98f),
                    "港澳通行证" to (85.6f to 53.98f),
                    "焊工证" to (85.6f to 53.98f)
                )
            )
            addAll(
                createGroup(
                    EDUCATION,
                    "学生证" to (100f to 70f),
                    "本科毕业证" to (235f to 165f),
                    "结业证" to (297f to 210f),
                    "英语四六级证书" to (203f to 133f),
                    "计算机等级证书" to (255f to 183f),
                    "普通话水平测试等级证书" to (240f to 170f),
                    "报到证" to (297f to 210f),
                    "录取通知书" to (297f to 210f),
                    "奖状" to (297f to 210f)
                )
            )
            addAll(
                createGroup(
                    CHILD,
                    "出生医学证明(新版)" to (210f to 297f),
                    "出生医学证明" to (308f to 130f),
                    "预防接种证" to (110f to 155f),
                    "儿童保健册" to (182f to 124f)
                )
            )
            addAll(
                createSameSizeGroup(
                    PROFESSION,
                    176f,
                    250f,
                    "教师资格证",
                    "导游证",
                    "医师证",
                    "注册消防工程师证",
                    "法律职业资格",
                    "律师职业资格证",
                    "中国委托公证人资格(香港、澳门)",
                    "注册会计师",
                    "民用核安全设备无损检验人员资格",
                    "民用核设施操纵人员资格",
                    "注册核安全工程师",
                    "注册建筑师",
                    "监理工程师",
                    "房地产估价师",
                    "造价工程师",
                    "注册城乡规划师",
                    "建造师",
                    "注册结构工程师",
                    "注册土木工程师",
                    "注册化工工程师",
                    "注册电气工程师",
                    "注册公用设备工程师",
                    "注册环保工程师",
                    "注册石油天然气工程师",
                    "注册冶金工程师",
                    "注册采矿/矿物工程师",
                    "注册机械工程师",
                    "注册验船师",
                    "船员资格(含船员、渔业船员)",
                    "执业兽医",
                    "乡村兽医",
                    "拍卖师",
                    "演出经纪人员资格",
                    "乡村医生",
                    "人体器官移植医师",
                    "护士执业资格",
                    "母婴保健技术服务人员资格",
                    "出入境检疫处理人员资格",
                    "注册设备监理师",
                    "注册计量师",
                    "广播电视播音员、主持人资格",
                    "新闻记者执业资格",
                    "注册安全工程师",
                    "执业药师",
                    "专利代理人",
                    "注册测绘师",
                    "空勤人员、地面人员",
                    "民用航空器外国驾驶员、领航员、飞行机械员、飞行通信员",
                    "航空安全员",
                    "民用航空电信人员、航行情报人员、气象人员",
                    "特种设备检验、检测人员资格认定",
                    "工程咨询(投资)专业技术人员职业资格",
                    "通信专业技术人员职业资格",
                    "计算机技术与软件专业技术资格",
                    "社会工作者职业资格",
                    "会计专业技术资格",
                    "资产评估师",
                    "经纪专业技术资格",
                    "土地登记代理专业人员职业资格",
                    "环境影响评价工程师",
                    "房地产经纪专业人员职业资格",
                    "机动车检测维修专业技术人员职业资格",
                    "公路水运工程试验检测专业技术人员职业资格",
                    "水利工程质量检测员资格",
                    "卫生专业技术资格",
                    "审计专业技术资格",
                    "税务师",
                    "认证人员职业资格",
                    "出版专业技术人员职业资格",
                    "统计专业技术资格",
                    "银行业专业人员职业资格",
                    "证券期货从业人员资格",
                    "文物保护工程从业资格",
                    "翻译专业资格"
                )
            )
            add(preset("death_certificate", "死亡证明", 210f, 297f, OTHER))
        }

        val commonEntries: List<IdCardDocumentType> = allEntries.filter { it.isCommon }

        val groupedEntries: List<Pair<String, List<IdCardDocumentType>>> =
            allEntries.groupBy { it.category }.toList()

        private fun preset(
            key: String,
            label: String,
            widthMm: Float,
            heightMm: Float,
            category: String,
            isCommon: Boolean = false,
            correctionMode: SimpleDocumentScanner.CorrectionMode = SimpleDocumentScanner.CorrectionMode.ID_CARD
        ) = IdCardDocumentType(key, label, widthMm, heightMm, category, isCommon, correctionMode)

        private fun createGroup(
            category: String,
            vararg items: Pair<String, Pair<Float, Float>>
        ): List<IdCardDocumentType> = items.map { (label, size) ->
            preset(
                key = buildKey(label),
                label = label,
                widthMm = size.first,
                heightMm = size.second,
                category = category
            )
        }

        private fun createSameSizeGroup(
            category: String,
            widthMm: Float,
            heightMm: Float,
            vararg labels: String
        ): List<IdCardDocumentType> = labels.map { label ->
            preset(
                key = buildKey(label),
                label = label,
                widthMm = widthMm,
                heightMm = heightMm,
                category = category
            )
        }

        private fun buildKey(label: String): String = label
            .replace("(", "_")
            .replace(")", "_")
            .replace("、", "_")
            .replace("/", "_")
            .replace(" ", "_")
    }
}

enum class IdCardLayoutOption(
    val label: String,
    val columns: Int,
    val rows: Int
) {
    VerticalPair("上下双证", 1, 2),
    Single("单证", 1, 1),
    TripleStack("三联", 1, 3),
    QuadGrid("四宫格", 2, 2);

    val captureCount: Int
        get() = columns * rows
}

private fun formatMillimeter(value: Float): String {
    val rounded = value.toInt()
    return if (value == rounded.toFloat()) rounded.toString() else value.toString()
}

enum class FlashModeOption(
    val imageCaptureMode: Int,
    val announcement: String
) {
    Auto(ImageCapture.FLASH_MODE_AUTO, "闪光灯自动"),
    On(ImageCapture.FLASH_MODE_ON, "闪光灯打开"),
    Off(ImageCapture.FLASH_MODE_OFF, "闪光灯关闭");

    fun next(): FlashModeOption = when (this) {
        Auto -> On
        On -> Off
        Off -> Auto
    }
}

data class AutoCaptureUiState(
    val hintText: String = "点击快门拍摄当前页面",
    val stableProgress: Float = 0f,
    val documentDetected: Boolean = false,
    val liveQuad: List<PointF> = emptyList(),
    val isStable: Boolean = false
)

data class AutoCaptureAnalysisResult(
    val uiState: AutoCaptureUiState,
    val shouldCapture: Boolean
)

fun normalizePageRotation(rotationDegrees: Float): Float {
    val normalized = rotationDegrees % 360f
    return if (normalized < 0f) normalized + 360f else normalized
}
