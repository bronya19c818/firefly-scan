package cn.yuandianai.juziscan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import cn.yuandianai.juziscan.privacy.PrivacyConsentDialog
import cn.yuandianai.juziscan.privacy.PrivacyConsentStore
import cn.yuandianai.juziscan.scan.HomeTab
import cn.yuandianai.juziscan.scan.LegalDocType
import cn.yuandianai.juziscan.scan.ScanFlow
import cn.yuandianai.juziscan.scan.ui.LegalDocumentScreen
import cn.yuandianai.juziscan.scan.ui.ScanHomeScreen
import cn.yuandianai.juziscan.ui.theme.FireflyColors
import cn.yuandianai.juziscan.ui.theme.FireflyTheme
import com.google.mlkit.common.MlKit

private object MlKitInitializer {
    @Volatile
    private var initialized = false

    fun ensureInitialized(context: android.content.Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            try {
                MlKit.initialize(context.applicationContext)
            } catch (error: IllegalStateException) {
                // Activity recreation can reach here after ML Kit is ready.
                if (!error.message.orEmpty().contains("already initialized", ignoreCase = true)) {
                    throw error
                }
            }
            initialized = true
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val consentStore = PrivacyConsentStore(applicationContext)
        if (consentStore.hasAcceptedCurrentPolicy()) {
            MlKitInitializer.ensureInitialized(applicationContext)
        }
        setContent {
            // 深色主题将在后续阶段随各页面改造一并启用，本轮保持浅色（与现网一致）
            FireflyTheme(darkTheme = false) {
                var privacyConsentGranted by remember {
                    mutableStateOf(consentStore.hasAcceptedCurrentPolicy())
                }
                var consentLegalDocument by remember { mutableStateOf<LegalDocType?>(null) }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = FireflyColors.Background
                ) {
                    val legalDocument = consentLegalDocument
                    if (legalDocument != null) {
                        LegalDocumentScreen(
                            title = legalDocument.title,
                            filename = legalDocument.filename,
                            onBack = { consentLegalDocument = null }
                        )
                    } else if (!privacyConsentGranted) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            PrivacyConsentHomeBackground()
                            PrivacyConsentDialog(
                                onAccept = {
                                    consentStore.acceptCurrentPolicy()
                                    MlKitInitializer.ensureInitialized(applicationContext)
                                    privacyConsentGranted = true
                                },
                                onExit = { finishAffinity() },
                                onOpenLegalDocument = { consentLegalDocument = it }
                            )
                        }
                    } else {
                        ScanFlow()
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacyConsentHomeBackground() {
    ScanHomeScreen(
        currentTab = HomeTab.Home,
        isLoggedIn = false,
        accountDisplayName = "点击登录/注册",
        accountAvatarUrl = null,
        accountSubtitle = "登录后可管理账号及会员权益",
        membershipStatus = null,
        recentRecords = emptyList(),
        onTabChange = {},
        onEntryClick = {},
        onImportClick = {},
        onLegalDocClick = {},
        onViewAllDocsClick = {},
        onSettingsClick = {},
        onAccountSecurityClick = {},
        onMoreToolsClick = {},
        onVipClick = {},
        onAvatarClick = {},
        onNicknameClick = {},
        onAccountClick = {},
        onLogoutClick = {},
        onRecentOpen = {},
        onRecentShare = {},
        onFeedbackClick = {}
    )
}
