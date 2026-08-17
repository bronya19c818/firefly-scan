package cn.yuandianai.juziscan.scan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.yuandianai.juziscan.common.BlueCheckIcon
import cn.yuandianai.juziscan.common.ChevronRightIcon
import cn.yuandianai.juziscan.common.CloseCrossIcon
import cn.yuandianai.juziscan.scan.ExportFileSize
import cn.yuandianai.juziscan.scan.ExportFileType
import cn.yuandianai.juziscan.scan.ExportWatermarkSettings
import cn.yuandianai.juziscan.scan.ScannedPage
import cn.yuandianai.juziscan.scan.WatermarkStrengthOption
import cn.yuandianai.juziscan.scan.WatermarkStyleOption
import cn.yuandianai.juziscan.ui.theme.HpSmartColors

@Composable
fun ShareSaveScreen(
    pages: List<ScannedPage>,
    isVip: Boolean,
    initialWatermarkText: String = "仅供本人使用",
    onBack: () -> Unit,
    onRemoveBrandMarkClick: () -> Unit,
    onShare: (
        fileName: String,
        fileType: ExportFileType,
        fileSize: ExportFileSize,
        watermarkSettings: ExportWatermarkSettings
    ) -> Unit,
    onFeedbackClick: () -> Unit
) {
    var subStage by remember { mutableStateOf(0) } // 0: Main, 1: File Type, 2: File Size
    var fileName by remember {
        val format = java.text.SimpleDateFormat("yyyy-MM-dd_HHmmss", java.util.Locale.getDefault())
        mutableStateOf("桔子扫描_${format.format(java.util.Date())}")
    }
    var selectedType by remember { mutableStateOf(ExportFileType.PDF) }
    var selectedSize by remember { mutableStateOf(ExportFileSize.Actual) }
    var watermarkEnabled by remember { mutableStateOf(false) }
    var watermarkText by remember(initialWatermarkText) { mutableStateOf(initialWatermarkText) }
    var watermarkStyle by remember { mutableStateOf(WatermarkStyleOption.DiagonalRepeat) }
    var watermarkStrength by remember { mutableStateOf(WatermarkStrengthOption.Medium) }

    when (subStage) {
        0 -> {
            ShareSaveMain(
                fileName = fileName,
                onFileNameChange = { fileName = it },
                isVip = isVip,
                fileType = selectedType,
                fileSize = selectedSize,
                watermarkEnabled = watermarkEnabled,
                onWatermarkEnabledChange = { watermarkEnabled = it },
                watermarkText = watermarkText,
                onWatermarkTextChange = { watermarkText = it },
                watermarkStyle = watermarkStyle,
                onWatermarkStyleChange = { watermarkStyle = it },
                watermarkStrength = watermarkStrength,
                onWatermarkStrengthChange = { watermarkStrength = it },
                onSelectTypeClick = { subStage = 1 },
                onSelectSizeClick = { subStage = 2 },
                onBack = onBack,
                onRemoveBrandMarkClick = onRemoveBrandMarkClick,
                onContinue = {
                    val resolvedName = fileName.trim().ifBlank { "桔子扫描_扫描文档" }
                    onShare(
                        resolvedName,
                        selectedType,
                        selectedSize,
                        ExportWatermarkSettings(
                            enabled = watermarkEnabled,
                            text = watermarkText,
                            style = watermarkStyle,
                            strength = watermarkStrength,
                            brandMarkEnabled = !isVip
                        )
                    )
                },
                onFeedbackClick = onFeedbackClick
            )
        }
        1 -> {
            FileTypeSelector(
                selectedType = selectedType,
                onSelect = { selectedType = it },
                onBack = { subStage = 0 }
            )
        }
        2 -> {
            FileSizeSelector(
                selectedSize = selectedSize,
                onSelect = { selectedSize = it },
                onBack = { subStage = 0 }
            )
        }
    }
}

@Composable
private fun ShareSaveMain(
    fileName: String,
    onFileNameChange: (String) -> Unit,
    isVip: Boolean,
    fileType: ExportFileType,
    fileSize: ExportFileSize,
    watermarkEnabled: Boolean,
    onWatermarkEnabledChange: (Boolean) -> Unit,
    watermarkText: String,
    onWatermarkTextChange: (String) -> Unit,
    watermarkStyle: WatermarkStyleOption,
    onWatermarkStyleChange: (WatermarkStyleOption) -> Unit,
    watermarkStrength: WatermarkStrengthOption,
    onWatermarkStrengthChange: (WatermarkStrengthOption) -> Unit,
    onSelectTypeClick: () -> Unit,
    onSelectSizeClick: () -> Unit,
    onBack: () -> Unit,
    onRemoveBrandMarkClick: () -> Unit,
    onContinue: () -> Unit,
    onFeedbackClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HpSmartColors.Background)
            .statusBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            TopSoftButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart)
            )
            Text(
                text = "导出分享",
                color = HpSmartColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // File name card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .padding(18.dp)
            ) {
                Text(
                    text = "文件名",
                    color = HpSmartColors.TextSecondary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFDCDDF2), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = fileName,
                        onValueChange = onFileNameChange,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = TextStyle(
                            color = HpSmartColors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        cursorBrush = SolidColor(HpSmartColors.Accent)
                    )
                    if (fileName.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(Color(0xFFE2E4FA), CircleShape)
                                .pointerInput(Unit) {
                                    detectTapGesturesCompat { onFileNameChange("") }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            CloseCrossIcon(
                                color = HpSmartColors.TextPrimary,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                }
            }

            // Settings card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "文件设置",
                    color = HpSmartColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                ShareDivider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) { detectTapGesturesCompat(onSelectTypeClick) }
                        .padding(vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "文件类型",
                        color = HpSmartColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = fileType.label,
                        color = HpSmartColors.TextSecondary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    ChevronRightIcon(
                        color = HpSmartColors.TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }

                ShareDivider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) { detectTapGesturesCompat(onSelectSizeClick) }
                        .padding(vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "文件大小",
                        color = HpSmartColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = fileSize.label,
                        color = HpSmartColors.TextSecondary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    ChevronRightIcon(
                        color = HpSmartColors.TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                if (!isVip) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "免费版固定水印 🍊桔子扫描",
                            color = HpSmartColors.Accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .background(
                                    brush = Brush.horizontalGradient(
                                        listOf(Color(0xFFFFEAAB), Color(0xFFFFD76E))
                                    ),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .clickable(onClick = onRemoveBrandMarkClick)
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "移除标识",
                                color = Color(0xFF5A3B00),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    ShareDivider()
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "自定义水印",
                            color = HpSmartColors.TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Switch(
                        checked = watermarkEnabled,
                        onCheckedChange = onWatermarkEnabledChange,
                        enabled = true
                    )
                }

                if (watermarkEnabled) {
                    ShareDivider()
                    Text(
                        text = "水印内容",
                        color = HpSmartColors.TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    BasicTextField(
                            value = watermarkText,
                            onValueChange = onWatermarkTextChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFFDCDDF2), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            textStyle = TextStyle(
                                color = HpSmartColors.TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            cursorBrush = SolidColor(HpSmartColors.Accent),
                            decorationBox = { innerTextField ->
                                if (watermarkText.isBlank()) {
                                    Text(
                                        text = "例如：仅供办理业务使用",
                                        color = Color(0xFF9AA2BF),
                                        fontSize = 15.sp
                                    )
                                }
                                innerTextField()
                            }
                    )

                    Text(
                            text = "样式",
                            color = HpSmartColors.TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    OptionChipRow(
                            options = WatermarkStyleOption.entries,
                            selected = watermarkStyle,
                            label = { it.label },
                            onSelected = onWatermarkStyleChange
                        )

                    Text(
                            text = "透明度",
                            color = HpSmartColors.TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    OptionChipRow(
                            options = WatermarkStrengthOption.entries,
                            selected = watermarkStrength,
                            label = { it.label },
                            onSelected = onWatermarkStrengthChange
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "遇到问题？",
                    color = HpSmartColors.TextSecondary,
                    fontSize = 13.sp
                )
                Text(
                    text = "点击反馈",
                    color = Color(0xFFFF6B00),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onFeedbackClick() }
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            ActionPillButton(
                text = "导出",
                filled = true,
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun <T> OptionChipRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .background(
                        color = if (isSelected) Color(0xFFEAF2FF) else Color(0xFFF6F7FB),
                        shape = RoundedCornerShape(999.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSelected) HpSmartColors.Accent.copy(alpha = 0.32f) else Color.Transparent,
                        shape = RoundedCornerShape(999.dp)
                    )
                    .clickable { onSelected(option) }
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Text(
                    text = label(option),
                    color = if (isSelected) HpSmartColors.Accent else HpSmartColors.TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun FileTypeSelector(
    selectedType: ExportFileType,
    onSelect: (ExportFileType) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HpSmartColors.Background)
            .statusBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            BackPillButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "文件类型",
                    color = HpSmartColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                ShareDivider()
                ExportFileType.entries.forEachIndexed { index, type ->
                    val isSelected = type == selectedType
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(type) { detectTapGesturesCompat { onSelect(type) } }
                            .padding(vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = type.label,
                            color = if (isSelected) HpSmartColors.Accent else HpSmartColors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            BlueCheckIcon(modifier = Modifier.size(22.dp))
                        }
                    }
                    if (index < ExportFileType.entries.size - 1) {
                        ShareDivider()
                    }
                }
            }
            Text(
                text = selectedType.hint,
                color = HpSmartColors.TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }
    }
}

@Composable
private fun FileSizeSelector(
    selectedSize: ExportFileSize,
    onSelect: (ExportFileSize) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HpSmartColors.Background)
            .statusBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            BackPillButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "文件大小",
                    color = HpSmartColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                ShareDivider()
                ExportFileSize.entries.forEachIndexed { index, size ->
                    val isSelected = size == selectedSize
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(size) { detectTapGesturesCompat { onSelect(size) } }
                            .padding(vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = size.label,
                            color = if (isSelected) HpSmartColors.Accent else HpSmartColors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            BlueCheckIcon(modifier = Modifier.size(22.dp))
                        }
                    }
                    if (index < ExportFileSize.entries.size - 1) {
                        ShareDivider()
                    }
                }
            }
            Text(
                text = "文件越大，质量越高，导出或分享耗时也会更长。",
                color = HpSmartColors.TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }
    }
}

@Composable
private fun ShareDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0xFFE5E6F2))
    )
}
