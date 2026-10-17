# 萤火Scan 图标系统 V1.0

固定来源：[Tabler Icons v3.49.0](https://github.com/tabler/tabler-icons/tree/bbed884d15354b5cebf2493371f20dc2d5e83eaf/icons/outline)，MIT；41 个 outline 图标。完整原始 SVG 与 SHA-256 见 [manifest.json](../third_party/tabler-icons/manifest.json)。

## 业务映射

标准名称即 `FireflyIconName` 的枚举值；资源名称统一为 `ic_tabler_官方名称`（连字符转下划线）。位置相对于 `app/src/main/java/cn/yuandianai/juziscan/`；旧公共组件的调用位置也已展开。

| 业务含义 | 标准名称 | Tabler 官方名称 | 使用页面 / 场景 |
|---|---|---|---|
| 返回 | `Back` | [`arrow-left`](../third_party/tabler-icons/icons/arrow-left.svg) | `scan/ui/CaptureScreen.kt`、`scan/ui/FireflyComponents.kt`、`scan/ui/LegalDocumentScreen.kt`、`scan/ui/ScanComponents.kt` |
| 关闭 | `Close` | [`x`](../third_party/tabler-icons/icons/x.svg) | `scan/ui/CaptureScreen.kt`、`scan/ui/ReorderScreen.kt`、`scan/ui/ScanComponents.kt`、`scan/ui/ShareSaveScreen.kt`、`scan/ui/VipScreen.kt` |
| 闪光灯：开启 | `FlashOn` | [`bolt`](../third_party/tabler-icons/icons/bolt.svg) | `scan/ui/CaptureScreen.kt` |
| 闪光灯：关闭 | `FlashOff` | [`bolt-off`](../third_party/tabler-icons/icons/bolt-off.svg) | `scan/ui/CaptureScreen.kt` |
| 闪光灯：自动 | `FlashAuto` | [`camera-bolt`](../third_party/tabler-icons/icons/camera-bolt.svg) | `scan/ui/CaptureScreen.kt` |
| 设置 | `Settings` | [`settings`](../third_party/tabler-icons/icons/settings.svg) | `scan/ui/MineScreen.kt` |
| 相册导入 | `Gallery` | [`photo`](../third_party/tabler-icons/icons/photo.svg) | `scan/ui/CaptureScreen.kt`、`scan/ui/HomeScreen.kt` |
| 添加 | `Add` | [`plus`](../third_party/tabler-icons/icons/plus.svg) | `scan/ui/CaptureScreen.kt`、`scan/ui/PreviewScreen.kt` |
| 旋转 | `Rotate` | [`rotate-clockwise`](../third_party/tabler-icons/icons/rotate-clockwise.svg) | `scan/ui/PreviewScreen.kt`、`scan/ui/TaskResultScreens.kt` |
| 裁剪 | `Crop` | [`crop`](../third_party/tabler-icons/icons/crop.svg) | `scan/ui/EdgeReviewScreen.kt` |
| 展开 | `Expand` | [`arrows-maximize`](../third_party/tabler-icons/icons/arrows-maximize.svg) | `scan/ui/EdgeReviewScreen.kt` |
| 分享 | `Share` | [`share`](../third_party/tabler-icons/icons/share.svg) | `scan/ui/HistoryDocumentComponents.kt`、`scan/ui/PreviewScreen.kt` |
| 重拍 | `Refresh` | [`refresh`](../third_party/tabler-icons/icons/refresh.svg) |  |
| 已选中 | `CheckCircle` | [`circle-check`](../third_party/tabler-icons/icons/circle-check.svg) | `scan/ui/ShareSaveScreen.kt` |
| 进入 | `ChevronRight` | [`chevron-right`](../third_party/tabler-icons/icons/chevron-right.svg) | `settings/AccountSecurityScreen.kt`、`settings/SettingsScreen.kt`、`scan/ui/FireflyComponents.kt`、`scan/ui/MineScreen.kt`、`scan/ui/ShareSaveScreen.kt`、`scan/ui/VipScreen.kt` |
| 打印 | `Print` | [`printer`](../third_party/tabler-icons/icons/printer.svg) | `scan/ui/PreviewScreen.kt` |
| 重新排序 | `Reorder` | [`arrows-sort`](../third_party/tabler-icons/icons/arrows-sort.svg) | `scan/ui/PreviewScreen.kt` |
| 编辑 | `Edit` | [`pencil`](../third_party/tabler-icons/icons/pencil.svg) | `scan/ui/PreviewScreen.kt` |
| 更换 | `Replace` | [`replace`](../third_party/tabler-icons/icons/replace.svg) | `scan/ui/PreviewScreen.kt` |
| 删除 | `Delete` | [`trash`](../third_party/tabler-icons/icons/trash.svg) | `scan/ui/HistoryDocumentComponents.kt`、`scan/ui/PreviewScreen.kt` |
| 会员 | `Crown` | [`crown`](../third_party/tabler-icons/icons/crown.svg) | `scan/ui/HomeScreen.kt`、`scan/ui/MineScreen.kt`、`scan/ui/VipScreen.kt` |
| 相机 | `Camera` | [`camera`](../third_party/tabler-icons/icons/camera.svg) | `scan/ScanFlow.kt` |
| 扫描 | `Scan` | [`scan`](../third_party/tabler-icons/icons/scan.svg) | `scan/ui/HomeScreen.kt`、`scan/ui/ToolCatalog.kt` |
| 证件扫描 | `IdCard` | [`id`](../third_party/tabler-icons/icons/id.svg) | `scan/ui/HomeScreen.kt`、`scan/ui/ToolCatalog.kt`、`scan/ui/VipScreen.kt` |
| 文字识别 | `TextScan` | [`text-scan-2`](../third_party/tabler-icons/icons/text-scan-2.svg) | `scan/ui/HomeScreen.kt`、`scan/ui/ToolCatalog.kt`、`scan/ui/VipScreen.kt` |
| 更多工具 | `Tools` | [`layout-grid`](../third_party/tabler-icons/icons/layout-grid.svg) | `scan/ui/HomeScreen.kt` |
| 首页 | `Home` | [`home`](../third_party/tabler-icons/icons/home.svg) | `scan/ui/HomeScreen.kt` |
| 我的 | `User` | [`user`](../third_party/tabler-icons/icons/user.svg) | `scan/ui/HomeScreen.kt`、`scan/ui/MineScreen.kt` |
| 文档 | `Document` | [`file-text`](../third_party/tabler-icons/icons/file-text.svg) | `scan/ui/MineScreen.kt` |
| 账号与安全 | `Shield` | [`shield-check`](../third_party/tabler-icons/icons/shield-check.svg) | `scan/ui/MineScreen.kt`、`scan/ui/VipScreen.kt` |
| 添加水印 | `Watermark` | [`rubber-stamp`](../third_party/tabler-icons/icons/rubber-stamp.svg) | `scan/ui/ToolCatalog.kt`、`scan/ui/WatermarkToolScreen.kt` |
| 长图拼接 | `Stitch` | [`layout-rows`](../third_party/tabler-icons/icons/layout-rows.svg) | `scan/ui/ImageStitchToolScreen.kt`、`scan/ui/ToolCatalog.kt` |
| 图片转PDF | `ImageToPdf` | [`file-type-pdf`](../third_party/tabler-icons/icons/file-type-pdf.svg) | `scan/ui/ImageToPdfToolScreen.kt`、`scan/ui/ToolCatalog.kt` |
| 图片压缩 | `Compress` | [`arrows-minimize`](../third_party/tabler-icons/icons/arrows-minimize.svg) | `scan/ui/ImageCompressToolScreen.kt`、`scan/ui/ToolCatalog.kt` |
| 二维码工具 | `QrCode` | [`qrcode`](../third_party/tabler-icons/icons/qrcode.svg) | `scan/ui/QrCodeToolScreen.kt`、`scan/ui/ToolCatalog.kt` |
| WiFi传图 | `Wifi` | [`wifi`](../third_party/tabler-icons/icons/wifi.svg) | `scan/ui/ToolCatalog.kt`、`scan/ui/WiFiTransferToolScreen.kt` |
| 更多操作 | `More` | [`dots`](../third_party/tabler-icons/icons/dots.svg) | `scan/ui/ScanComponents.kt` |
| 已开启 | `Check` | [`check`](../third_party/tabler-icons/icons/check.svg) | `scan/ScanFlow.kt`、`scan/ui/VipScreen.kt` |
| 提示 | `Warning` | [`alert-triangle`](../third_party/tabler-icons/icons/alert-triangle.svg) | `scan/ScanFlow.kt` |
| 支付 | `Wallet` | [`wallet`](../third_party/tabler-icons/icons/wallet.svg) | `scan/ui/VipScreen.kt` |
| 表格识别 | `Table` | [`table`](../third_party/tabler-icons/icons/table.svg) | `scan/ui/ToolCatalog.kt`、`scan/ui/VipScreen.kt` |

自动闪光灯使用官方 `camera-bolt`，开启用 `bolt`，关闭用 `bolt-off`，三态均有独立读屏标签；不用私绘的 A/闪电拼接轮廓。`BenefitValue` 将会员表格的状态数据渲染为标准勾选/关闭图标。支付方式保留实际名称，以通用钱包图标辅助，不模拟支付品牌标志。

全部工具采用四列等宽网格：52dp 底座、24dp 图标、12sp 标签、8dp 列间距。扫描识别四项和图片处理四项分别保持一行，效率工具的不足四项以空列占位。

## 全部 Compose 文件巡检

共检查 35 个既有 Compose 源文件，并检查新增的 `common/FireflyIcon.kt` 注册与渲染组件；修改前命中记录完整保存在 [icon-inventory-before.json](icon-inventory-before.json)。无通用图标的页面也列入检查。

| 文件 | 检查处理 |
|---|---|
| `cn/yuandianai/juziscan/common/FireflyIcon.kt` | 新增统一注册、尺寸、主题颜色、选中与禁用状态。 |
| `cn/yuandianai/juziscan/MainActivity.kt` | 无独立通用功能图标；沿用共用顶栏/工具卡片，或仅展示业务文本与图片。 |
| `cn/yuandianai/juziscan/common/Icons.kt` | 18 个旧组件统一委托 FireflyIcon，删除 Canvas 笔画。 |
| `cn/yuandianai/juziscan/history/HistoryScreen.kt` | 无独立通用功能图标；沿用共用顶栏/工具卡片，或仅展示业务文本与图片。 |
| `cn/yuandianai/juziscan/privacy/PrivacyConsent.kt` | 无独立通用功能图标；沿用共用顶栏/工具卡片，或仅展示业务文本与图片。 |
| `cn/yuandianai/juziscan/scan/ScanFlow.kt` | 统一资源：`Camera`、`Check`、`Warning` |
| `cn/yuandianai/juziscan/settings/AccountSecurityScreen.kt` | 统一资源：`ChevronRight` |
| `cn/yuandianai/juziscan/settings/SettingsScreen.kt` | 统一资源：`ChevronRight` |
| `cn/yuandianai/juziscan/ui/theme/Theme.kt` | 无独立通用功能图标；沿用共用顶栏/工具卡片，或仅展示业务文本与图片。 |
| `cn/yuandianai/juziscan/scan/ui/AllDocumentsScreen.kt` | 无独立通用功能图标；沿用共用顶栏/工具卡片，或仅展示业务文本与图片。 |
| `cn/yuandianai/juziscan/scan/ui/AllToolsScreen.kt` | 无独立通用功能图标；沿用共用顶栏/工具卡片，或仅展示业务文本与图片。 |
| `cn/yuandianai/juziscan/scan/ui/AuthDialog.kt` | 无独立通用功能图标；沿用共用顶栏/工具卡片，或仅展示业务文本与图片。 |
| `cn/yuandianai/juziscan/scan/ui/BindPhoneDialog.kt` | 无独立通用功能图标；沿用共用顶栏/工具卡片，或仅展示业务文本与图片。 |
| `cn/yuandianai/juziscan/scan/ui/CaptureScreen.kt` | 统一资源：`BackArrowIcon`、`CloseCrossIcon`、`FlashModeIcon`、`GalleryPhotoIcon`、`PlusIcon` 保留取景框、识别多边形、证件版式示意和证件原图。 |
| `cn/yuandianai/juziscan/scan/ui/DocumentStyleScreen.kt` | 无独立通用功能图标；沿用共用顶栏/工具卡片，或仅展示业务文本与图片。 |
| `cn/yuandianai/juziscan/scan/ui/EdgeReviewScreen.kt` | 统一资源：`CropIcon`、`ExpandIcon` 保留可拖动裁剪多边形和控制点。 |
| `cn/yuandianai/juziscan/scan/ui/FeedbackDialog.kt` | 无独立通用功能图标；沿用共用顶栏/工具卡片，或仅展示业务文本与图片。 |
| `cn/yuandianai/juziscan/scan/ui/FireflyComponents.kt` | 分区箭头统一；空状态引用定稿品牌徽章。 |
| `cn/yuandianai/juziscan/scan/ui/HistoryDocumentComponents.kt` | 统一资源：`ShareUploadIcon`、`TrashBinIcon` |
| `cn/yuandianai/juziscan/scan/ui/HomeScreen.kt` | 统一资源：`Gallery`、`Crown`、`Scan`、`IdCard`、`TextScan`、`Tools`、`Home`、`User` |
| `cn/yuandianai/juziscan/scan/ui/ImageCompressToolScreen.kt` | 统一资源：`Compress` |
| `cn/yuandianai/juziscan/scan/ui/ImageStitchToolScreen.kt` | 统一资源：`Stitch` |
| `cn/yuandianai/juziscan/scan/ui/ImageToPdfToolScreen.kt` | 统一资源：`ImageToPdf` |
| `cn/yuandianai/juziscan/scan/ui/LegalDocumentScreen.kt` | 统一资源：`BackArrowIcon` |
| `cn/yuandianai/juziscan/scan/ui/MineScreen.kt` | 统一资源：`ChevronRight`、`Crown`、`User`、`Document`、`Shield`、`SettingsGearIcon` |
| `cn/yuandianai/juziscan/scan/ui/PreviewScreen.kt` | 统一资源：`PlusIcon`、`RotateArrowIcon`、`ShareUploadIcon`、`PrintIcon`、`ReorderIcon`、`PencilIcon`、`ReplaceArrowsIcon`、`TrashBinIcon` |
| `cn/yuandianai/juziscan/scan/ui/ProfileEditDialog.kt` | 无独立通用功能图标；沿用共用顶栏/工具卡片，或仅展示业务文本与图片。 |
| `cn/yuandianai/juziscan/scan/ui/QrCodeToolScreen.kt` | 统一资源：`QrCode` |
| `cn/yuandianai/juziscan/scan/ui/ReorderScreen.kt` | 统一资源：`CloseCrossIcon` |
| `cn/yuandianai/juziscan/scan/ui/ScanComponents.kt` | 统一资源：`More`、`BackArrowIcon`、`CloseCrossIcon` 保留快门及处理进度动画。 |
| `cn/yuandianai/juziscan/scan/ui/ShareSaveScreen.kt` | 统一资源：`CloseCrossIcon`、`BlueCheckIcon`、`ChevronRightIcon` |
| `cn/yuandianai/juziscan/scan/ui/TaskResultScreens.kt` | 统一资源：`RotateArrowIcon` |
| `cn/yuandianai/juziscan/scan/ui/ToolScreens.kt` | 无独立通用功能图标；沿用共用顶栏/工具卡片，或仅展示业务文本与图片。 |
| `cn/yuandianai/juziscan/scan/ui/VipScreen.kt` | 统一资源：`Close`、`ChevronRight`、`Crown`、`IdCard`、`TextScan`、`Shield`、`Check`、`Wallet`、`Table`、`CloseCrossIcon` |
| `cn/yuandianai/juziscan/scan/ui/WatermarkToolScreen.kt` | 统一资源：`Watermark` |
| `cn/yuandianai/juziscan/scan/ui/WiFiTransferToolScreen.kt` | 统一资源：`Wifi` |

本轮不删除旧 PNG/WebP 文件：运行时通用工具图标引用已移除；这些本地历史素材继续保留。工具空状态和会员轮播原先的二维码/传图/证件/OCR/表格营销示意，改用对应标准图标和现有说明文字；真实业务生成的二维码、取景叠层与证件版式内容保留。Logo、首页定稿背景与文档叠层、证件示意图不属于通用图标，继续引用现有资源。图像合成/二维码编码中的 Canvas 处理为真实业务输出。

## 可重复验证与工件

1. 执行 `python tools/import_tabler_icons.py`，离线校验全部固定源哈希并重新生成 XML。
2. 执行 `./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --console=plain`。
3. 连接一台已授权 Android 手机，执行 `./tools/run-firefly-icons-e2e.ps1 -Adb <adb.exe绝对路径>`；多设备时用 `-Serial`。安装覆盖 Debug APK，不清空应用数据。
4. 检查 `output/firefly-icons-e2e/`：设备信息、instrumentation 日志、首页/相册/工具箱/工具页/我的/设置/相机截图、浅深主题 20/24/32dp 图标表、选中/禁用图标表、每个图标 1×–4× 光栅图与透明覆盖率 JSON。固定名称重复运行覆盖本次工件，避免无限积累。

资源渲染检查直接使用 Android Drawable，并验证透明角落、可见笔画、非实心填充和跨密度覆盖率差异；Compose 图标表使用实际 FireflyIcon 与 Material 主题。真机导航测试操作实际 Activity、系统相册选择器和相机权限流程。验证产物只用于测试，不进入正式图标资源。

实际执行结果及选定截图另见 [验证记录](icon-system-v1.0-validation.md)。
