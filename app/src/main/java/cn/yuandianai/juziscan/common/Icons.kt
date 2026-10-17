package cn.yuandianai.juziscan.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import cn.yuandianai.juziscan.scan.FlashModeOption

// Existing call sites keep their signatures while all drawing goes through FireflyIcon.

@Composable
fun BackArrowIcon(color: Color, modifier: Modifier = Modifier) {
    FireflyIcon(FireflyIconName.Back, modifier = modifier, tint = color)
}

@Composable
fun CloseCrossIcon(color: Color, modifier: Modifier = Modifier) {
    FireflyIcon(FireflyIconName.Close, modifier = modifier, tint = color)
}

@Composable
fun SettingsGearIcon(color: Color, modifier: Modifier = Modifier) {
    FireflyIcon(FireflyIconName.Settings, modifier = modifier, tint = color, contentDescription = null)
}

@Composable
fun GalleryPhotoIcon(color: Color, modifier: Modifier = Modifier) {
    FireflyIcon(FireflyIconName.Gallery, modifier = modifier, tint = color, contentDescription = null)
}

@Composable
fun PlusIcon(color: Color, modifier: Modifier = Modifier) {
    FireflyIcon(FireflyIconName.Add, modifier = modifier, tint = color, contentDescription = null)
}

@Composable
fun RotateArrowIcon(color: Color, modifier: Modifier = Modifier) {
    FireflyIcon(FireflyIconName.Rotate, modifier = modifier, tint = color, contentDescription = null)
}

@Composable
fun CropIcon(color: Color, modifier: Modifier = Modifier) {
    FireflyIcon(FireflyIconName.Crop, modifier = modifier, tint = color, contentDescription = null)
}

@Composable
fun ExpandIcon(color: Color, modifier: Modifier = Modifier) {
    FireflyIcon(FireflyIconName.Expand, modifier = modifier, tint = color, contentDescription = null)
}

@Composable
fun ShareUploadIcon(color: Color, modifier: Modifier = Modifier) {
    FireflyIcon(FireflyIconName.Share, modifier = modifier, tint = color, contentDescription = null)
}

@Composable
fun RetakeRefreshIcon(color: Color, modifier: Modifier = Modifier) {
    FireflyIcon(FireflyIconName.Refresh, modifier = modifier, tint = color, contentDescription = null)
}

@Composable
fun ChevronRightIcon(color: Color, modifier: Modifier = Modifier) {
    FireflyIcon(FireflyIconName.ChevronRight, modifier = modifier, tint = color, contentDescription = null)
}

@Composable
fun PrintIcon(color: Color, modifier: Modifier = Modifier) {
    FireflyIcon(FireflyIconName.Print, modifier = modifier, tint = color, contentDescription = null)
}

@Composable
fun ReorderIcon(color: Color, modifier: Modifier = Modifier) {
    FireflyIcon(FireflyIconName.Reorder, modifier = modifier, tint = color, contentDescription = null)
}

@Composable
fun PencilIcon(color: Color, modifier: Modifier = Modifier) {
    FireflyIcon(FireflyIconName.Edit, modifier = modifier, tint = color, contentDescription = null)
}

@Composable
fun ReplaceArrowsIcon(color: Color, modifier: Modifier = Modifier) {
    FireflyIcon(FireflyIconName.Replace, modifier = modifier, tint = color, contentDescription = null)
}

@Composable
fun TrashBinIcon(color: Color, modifier: Modifier = Modifier) {
    FireflyIcon(FireflyIconName.Delete, modifier = modifier, tint = color, contentDescription = null)
}

@Composable
fun BlueCheckIcon(modifier: Modifier = Modifier) {
    FireflyIcon(FireflyIconName.CheckCircle, modifier = modifier, tint = MaterialTheme.colorScheme.primary)
}

@Composable
fun FlashModeIcon(mode: FlashModeOption, color: Color, modifier: Modifier = Modifier) {
    FireflyIcon(
        icon = when (mode) {
            FlashModeOption.Auto -> FireflyIconName.FlashAuto
            FlashModeOption.On -> FireflyIconName.FlashOn
            FlashModeOption.Off -> FireflyIconName.FlashOff
        },
        modifier = modifier,
        tint = color
    )
}
