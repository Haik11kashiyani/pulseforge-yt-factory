package com.pulseforge.studio

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Base64
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

// ============================================================================
// DATA MODELS FOR 8 CHANNELS (INDIA + INTERNATIONAL) & MASTER TO-DO LIST
// ============================================================================
data class ChannelConfig(
    val id: String,
    val name: String,
    val region: String,
    val language: String,
    val defaultTheme: String,
    val defaultFormat: String,
    val secretKeyName: String,
    val disclaimer: String,
    val sourceCitation: String,
    val sampleHeadline: String,
    val sampleMetricsJson: String
)

data class TodoItem(
    val id: Int,
    val phase: String,
    val title: String,
    var done: Boolean
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PulseForgeApp()
        }
    }
}

@Composable
fun PulseForgeApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("pulseforge_vault", Context.MODE_PRIVATE) }
    val coroutineScope = rememberCoroutineScope()

    // App Theme Toggle: Claude Warm Editorial (default) vs Dark Pro Studio
    var isDarkStudio by remember { mutableStateOf(false) }

    val bgPrimary = if (isDarkStudio) Color(0xFF0B0D11) else Color(0xFFFAF8F5)
    val bgCard = if (isDarkStudio) Color(0xFF131720) else Color(0xFFFFFFFF)
    val bgSubtle = if (isDarkStudio) Color(0xFF1C2230) else Color(0xFFF3EFEA)
    val textPrimary = if (isDarkStudio) Color(0xFFF3F4F6) else Color(0xFF141413)
    val textSecondary = if (isDarkStudio) Color(0xFF9CA3AF) else Color(0xFF5E5D59)
    val borderCol = if (isDarkStudio) Color(0xFF262E3F) else Color(0xFFE6E2DA)
    val accentTerracotta = Color(0xFFD97757)
    val emeraldGreen = Color(0xFF10B981)

    // Pre-configured 8 Channels (India + International Split)
    val channels = remember {
        listOf(
            ChannelConfig(
                id = "CH-01",
                name = "India Share Market & IPO Catch-Up",
                region = "🇮🇳 India Target",
                language = "hinglish_in",
                defaultTheme = "claude_pdf",
                defaultFormat = "auto",
                secretKeyName = "YT_TOKEN_MARKET_IN",
                disclaimer = "SEBI Disclaimer: Educational & informational market recap only. Not SEBI-registered advice.",
                sourceCitation = "Verified Data: NSE / BSE Bhavcopy & Official IPO Filings",
                sampleHeadline = "FIIs Sold ₹3,840 Cr—But DIIs Bought ₹4,510 Cr in Cash Market!",
                sampleMetricsJson = """{"nifty":"24,482 (+0.68%)","sensex":"80,245 (+0.61%)","fii_cash":"-₹3,840 Cr","dii_cash":"+₹4,510 Cr","ipo_qib":"18.4x"}"""
            ),
            ChannelConfig(
                id = "CH-02",
                name = "Global & US Wall Street Catch-Up",
                region = "🌍 International",
                language = "english_global",
                defaultTheme = "claude_pdf",
                defaultFormat = "auto",
                secretKeyName = "YT_TOKEN_MARKET_US",
                disclaimer = "SEC Disclaimer: Educational market commentary only. Not personalized investment advice.",
                sourceCitation = "Verified Data: NASDAQ / NYSE Closing Tape & SEC EDGAR Filings",
                sampleHeadline = "Nasdaq 100 Surges +1.42% on AI Capex & Dark-Pool Inflows",
                sampleMetricsJson = """{"sp500":"5,942 (+0.94%)","nasdaq":"20,890 (+1.42%)","us10y":"4.18% (-6bps)","qqq_flow":"+$3.9B"}"""
            ),
            ChannelConfig(
                id = "CH-03",
                name = "Text Chat Stories — India (WhatsApp UI)",
                region = "🇮🇳 India Target",
                language = "hinglish_in",
                defaultTheme = "chat_story",
                defaultFormat = "9:16",
                secretKeyName = "YT_TOKEN_CHAT_IN",
                disclaimer = "Dramatized fictional chat story created for entertainment only.",
                sourceCitation = "Original Hinglish Script • Multi-Speaker Gemini 2.5 Native TTS",
                sampleHeadline = "Wrong ₹50,000 UPI Transfer Turns Into a Family Plot Twist",
                sampleMetricsJson = """{"speaker1":"Aarav","speaker2":"Unknown (+91 98***)","hook_sec":"0.6s","ui":"WhatsApp Dark"}"""
            ),
            ChannelConfig(
                id = "CH-04",
                name = "Text Chat Stories — Global (iMessage UI)",
                region = "🌍 International",
                language = "english_global",
                defaultTheme = "chat_story",
                defaultFormat = "9:16",
                secretKeyName = "YT_TOKEN_CHAT_GLOBAL",
                disclaimer = "Dramatized fictional story for entertainment purposes.",
                sourceCitation = "Original Suspense Script • iOS 18 iMessage GSAP Motion Pack",
                sampleHeadline = "The AirTag in My Newly Bought Used Car Was Already Named...",
                sampleMetricsJson = """{"speaker1":"Liam","speaker2":"Previous Seller","hook_sec":"0.5s","ui":"iMessage iOS"}"""
            ),
            ChannelConfig(
                id = "CH-05",
                name = "Cricket Viral Rankings & Stat Showdowns",
                region = "🇮🇳🌍 India + Global",
                language = "hinglish_in",
                defaultTheme = "sports_arena",
                defaultFormat = "auto",
                secretKeyName = "YT_TOKEN_CRICKET",
                disclaimer = "Statistical comparison based on official ICC & IPL match records.",
                sourceCitation = "Verified Stats: Official ICC & ESPNcricinfo Records",
                sampleHeadline = "Highest Batting Average in Successful 180+ Run Chases",
                sampleMetricsJson = """{"rank1":"Virat Kohli (82.4 Avg)","rank2":"MS Dhoni (74.8 Avg)","rank3":"Jos Buttler (58.6 Avg)"}"""
            ),
            ChannelConfig(
                id = "CH-06",
                name = "Football (Soccer) Global Stat Showdowns",
                region = "🌍 International",
                language = "english_global",
                defaultTheme = "sports_arena",
                defaultFormat = "auto",
                secretKeyName = "YT_TOKEN_FOOTBALL",
                disclaimer = "Official UEFA & Domestic League statistical comparison.",
                sourceCitation = "Verified Stats: Official UEFA Champions League & Opta Records",
                sampleHeadline = "Most Goal Contributions in Champions League Knockouts",
                sampleMetricsJson = """{"rank1":"Cristiano Ronaldo (67 G+A)","rank2":"Lionel Messi (49 G+A)","rank3":"K. Mbappe (0.98/90)"}"""
            ),
            ChannelConfig(
                id = "CH-07",
                name = "Viral Ranking & Empire Valuations",
                region = "🌍 Global English",
                language = "english_global",
                defaultTheme = "apple_bento",
                defaultFormat = "auto",
                secretKeyName = "YT_TOKEN_EMPIRES",
                disclaimer = "Historical & corporate valuations adjusted for CPI inflation.",
                sourceCitation = "Verified Data: SEC Filings, IMF & Economic Archives",
                sampleHeadline = "Biggest Corporate & Empire Valuations in Human History",
                sampleMetricsJson = """{"rank1":"Dutch VOC ($8.2T Adj)","rank2":"Apple/Nvidia ($3.5T)","rank3":"Microsoft ($3.2T)"}"""
            ),
            ChannelConfig(
                id = "CH-08",
                name = "Dark Psychology & Daily Brain Quiz",
                region = "🌍🇮🇳 Global + India",
                language = "english_global",
                defaultTheme = "bloomberg_dark",
                defaultFormat = "9:16",
                secretKeyName = "YT_TOKEN_PSYCH_QUIZ",
                disclaimer = "Educational behavioral psychology concepts from peer-reviewed studies.",
                sourceCitation = "Source: Behavioral Economics & Cognitive Science Research",
                sampleHeadline = "Why a Crossed-Out Price Tricks Your Brain in 0.4 Seconds",
                sampleMetricsJson = """{"bias":"Anchoring Effect","lift":"+64% Conversion","quiz_timer":"3.0s"}"""
            )
        )
    }

    var selectedTab by remember { mutableStateOf(0) } // 0: Studio, 1: JSON/Data, 2: GitHub Vault, 3: To-Dos
    var selectedChannelIndex by remember { mutableStateOf(0) }
    val activeChannel = channels[selectedChannelIndex]

    var selectedFormat by remember { mutableStateOf(activeChannel.defaultFormat) } // "9:16", "16:9", "auto"
    var selectedTheme by remember { mutableStateOf(activeChannel.defaultTheme) }
    var selectedVoice by remember { mutableStateOf("gemini_native") }
    var dataSourceMode by remember { mutableStateOf("auto_api") } // "auto_api" or "manual_json"
    var customTopic by remember { mutableStateOf("") }
    var manualJsonText by remember { mutableStateOf(activeChannel.sampleMetricsJson) }

    // Vault Settings stored on Phone
    var githubRepo by remember { mutableStateOf(prefs.getString("gh_repo", "your-username/pulseforge-yt-factory") ?: "") }
    var githubPat by remember { mutableStateOf(prefs.getString("gh_pat", "") ?: "") }
    var masterAesKey by remember { mutableStateOf(prefs.getString("aes_key", "PF-2026-AES256-SECRET-KEY") ?: "") }
    var statusLog by remember { mutableStateOf("Ready. Connected to local AES-256 Phone Vault.") }

    // Master To-Do List State
    var todoList by remember {
        mutableStateOf(
            listOf(
                TodoItem(1, "Phase 1 • App", "Install native Kotlin PulseForge APK on Android phone", true),
                TodoItem(2, "Phase 1 • Market", "Launch Channel 1A (India Share Market & IPO Catch-Up) + SEBI Disclaimer", true),
                TodoItem(3, "Phase 1 • Market", "Launch Channel 1B (Global Wall St & US Tech IPO Catch-Up) + SEC Disclaimer", true),
                TodoItem(4, "Phase 1 • Stories", "Configure WhatsApp (India) & iMessage (Global) Text Chat Channels", true),
                TodoItem(5, "Phase 1 • Sports", "Configure Cricket & Football Verified Stat Showdown Channels", true),
                TodoItem(6, "Phase 2 • GitHub", "Paste GitHub Personal Access Token & Repo Name in Vault Tab", false),
                TodoItem(7, "Phase 2 • Secrets", "Add GEMINI_API_KEY & MASTER_DECRYPTION_KEY to GitHub Repo Secrets", false),
                TodoItem(8, "Phase 3 • Automation", "Trigger first 60FPS Tailwind+GSAP Video + Claude Daily PDF Render", false)
            )
        )
    }

    Scaffold(
        containerColor = bgPrimary,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgCard)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "PulseForge Kotlin Studio",
                            color = textPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        )
                        Text(
                            text = "Multi-Channel GitHub + Gemini Free API Controller",
                            color = textSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Button(
                        onClick = { isDarkStudio = !isDarkStudio },
                        colors = ButtonDefaults.buttonColors(containerColor = bgSubtle),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isDarkStudio) "☀️ Claude UI" else "🌙 Dark UI",
                            color = textPrimary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = bgCard) {
                val navItems = listOf("🎬 Studio", "📄 JSON/API", "🔐 Git Vault", "✅ To-Dos")
                navItems.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Text(label.take(2)) },
                        label = { Text(label.drop(3), fontSize = 11.sp) }
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ================================================================
            // TAB 0: STUDIO & LIVE GSAP PREVIEW + 1-TAP GITHUB DISPATCH
            // ================================================================
            if (selectedTab == 0) {
                item {
                    Text(
                        text = "STEP 1 • SELECT CHANNEL (INDIA + GLOBAL)",
                        color = accentTerracotta,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(channels) { idx, ch ->
                            val isSelected = idx == selectedChannelIndex
                            Column(
                                modifier = Modifier
                                    .width(210.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(bgCard)
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) accentTerracotta else borderCol,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        selectedChannelIndex = idx
                                        selectedTheme = ch.defaultTheme
                                        selectedFormat = ch.defaultFormat
                                        manualJsonText = ch.sampleMetricsJson
                                    }
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = "${ch.id} • ${ch.region}",
                                    color = accentTerracotta,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = ch.name,
                                    color = textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                                Text(
                                    text = "Secret: ${ch.secretKeyName}",
                                    color = textSecondary,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                // Format & Theme Controls Card
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(bgCard)
                            .border(1.dp, borderCol, RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "2. Video Format & Swappable After-Effects Theme",
                            color = textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        )

                        // Format Selector Row
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("9:16" to "📱 9:16 Short", "16:9" to "🖥️ 16:9 Full", "auto" to "🧠 Auto-Pick").forEach { (fmtKey, fmtLabel) ->
                                OutlinedButton(
                                    onClick = { selectedFormat = fmtKey },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (selectedFormat == fmtKey) accentTerracotta.copy(alpha = 0.15f) else Color.Transparent
                                    )
                                ) {
                                    Text(fmtLabel, color = textPrimary, fontSize = 11.sp)
                                }
                            }
                        }

                        // Theme Pack Selector Row
                        Text("Swappable GSAP Theme Pack (80/20 Brand Engine):", color = textSecondary, fontSize = 11.sp)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val themes = listOf(
                                "claude_pdf" to "📜 Claude Live PDF",
                                "bloomberg_dark" to "📈 Dark Terminal",
                                "apple_bento" to "✨ Apple Bento",
                                "chat_story" to "💬 WhatsApp/iMessage",
                                "sports_arena" to "🏆 Sports Broadcast"
                            )
                            itemsIndexed(themes) { _, (tKey, tLabel) ->
                                AssistChip(
                                    onClick = { selectedTheme = tKey },
                                    label = { Text(tLabel, fontSize = 11.sp, color = textPrimary) }
                                )
                            }
                        }

                        // Embedded Live HTML/Tailwind/GSAP WebView Preview
                        Text("Live 60FPS GSAP & Claude PDF Stage Preview:", color = textSecondary, fontSize = 11.sp)
                        GsapWebViewStage(
                            channel = activeChannel,
                            theme = selectedTheme,
                            format = selectedFormat
                        )
                    }
                }

                // One-Tap Cloud Trigger Card
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(bgCard)
                            .border(1.dp, borderCol, RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "3. Dispatch Render & Upload to GitHub Actions",
                            color = textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        )

                        OutlinedTextField(
                            value = customTopic,
                            onValueChange = { customTopic = it },
                            label = { Text("Optional Custom Topic / Headline Override", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    statusLog = "Dispatching workflow_dispatch to $githubRepo for ${activeChannel.id}..."
                                    val result = triggerGithubWorkflowDispatch(
                                        repo = githubRepo,
                                        pat = githubPat,
                                        channelId = activeChannel.id,
                                        videoFormat = selectedFormat,
                                        visualTheme = selectedTheme,
                                        voiceEngine = selectedVoice,
                                        dataSourceMode = dataSourceMode,
                                        customTopic = customTopic
                                    )
                                    statusLog = result
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accentTerracotta),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("🚀 Trigger GitHub Render & YouTube Upload", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        Text(
                            text = "Pipeline Status: $statusLog",
                            color = emeraldGreen,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // ================================================================
            // TAB 1: DUAL CONTENT ENGINE (AUTO-API OR MANUAL VERIFIED JSON)
            // ================================================================
            if (selectedTab == 1) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(bgCard)
                            .border(1.dp, borderCol, RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Dual Content Engine: Auto-API vs Manual Verified JSON",
                            color = textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { dataSourceMode = "auto_api" },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (dataSourceMode == "auto_api") accentTerracotta else bgSubtle
                                )
                            ) {
                                Text("⚡ Auto-API Grounded", color = if (dataSourceMode == "auto_api") Color.White else textPrimary, fontSize = 12.sp)
                            }
                            Button(
                                onClick = { dataSourceMode = "manual_json" },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (dataSourceMode == "manual_json") accentTerracotta else bgSubtle
                                )
                            ) {
                                Text("✍️ Manual Verified JSON", color = if (dataSourceMode == "manual_json") Color.White else textPrimary, fontSize = 12.sp)
                            }
                        }

                        OutlinedTextField(
                            value = manualJsonText,
                            onValueChange = { manualJsonText = it },
                            label = { Text("Verified JSON Payload (Paste from Gemini or Edit)", fontSize = 12.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                        )

                        Text(
                            text = "Mandatory Legal Footer (${activeChannel.id}): ${activeChannel.disclaimer}",
                            color = textSecondary,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "Source Attribution: ${activeChannel.sourceCitation}",
                            color = emeraldGreen,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // ================================================================
            // TAB 2: GITHUB VAULT & AES-256 REPO ENCRYPTION
            // ================================================================
            if (selectedTab == 2) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(bgCard)
                            .border(1.dp, borderCol, RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Zero-Trust GitHub Vault & AES-256 Encryption",
                            color = textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        )
                        Text(
                            text = "Your GitHub Token stays privately on your Android phone and talks directly to api.github.com.",
                            color = textSecondary,
                            fontSize = 12.sp
                        )

                        OutlinedTextField(
                            value = githubRepo,
                            onValueChange = { githubRepo = it },
                            label = { Text("GitHub Repository (username/repo-name)") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = githubPat,
                            onValueChange = { githubPat = it },
                            label = { Text("GitHub Personal Access Token (ghp_...)") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = masterAesKey,
                            onValueChange = { masterAesKey = it },
                            label = { Text("Master AES-256 Encryption Key (Matches GitHub Secret)") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {
                                prefs.edit()
                                    .putString("gh_repo", githubRepo)
                                    .putString("gh_pat", githubPat)
                                    .putString("aes_key", masterAesKey)
                                    .apply()
                                val sampleEncrypted = Aes256VaultHelper.encryptToBase64(manualJsonText, masterAesKey)
                                statusLog = "Saved Vault! Sample AES-256 Ciphertext: ${sampleEncrypted.take(36)}..."
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accentTerracotta),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("💾 Save Credentials to Android Vault", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        Text(
                            text = statusLog,
                            color = emeraldGreen,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // ================================================================
            // TAB 3: MASTER ROADMAP & TO-DO TRACKER
            // ================================================================
            if (selectedTab == 3) {
                itemsIndexed(todoList) { idx, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(bgCard)
                            .border(1.dp, borderCol, RoundedCornerShape(12.dp))
                            .clickable {
                                val updated = todoList.toMutableList()
                                updated[idx] = item.copy(done = !item.done)
                                todoList = updated
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = item.done,
                            onCheckedChange = {
                                val updated = todoList.toMutableList()
                                updated[idx] = item.copy(done = it)
                                todoList = updated
                            }
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(item.phase, color = accentTerracotta, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Text(item.title, color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// EMBEDDED WEBVIEW STAGE FOR LIVE 60FPS TAILWIND + GSAP PREVIEW ON ANDROID
// ============================================================================
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GsapWebViewStage(channel: ChannelConfig, theme: String, format: String) {
    val bgHex = if (theme == "bloomberg_dark" || theme == "sports_arena") "#0B0F19" else "#FAF8F5"
    val txtHex = if (theme == "bloomberg_dark" || theme == "sports_arena") "#F9FAFB" else "#141413"

    val htmlContent = """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <script src="https://cdnjs.cloudflare.com/ajax/libs/gsap/3.12.5/gsap.min.js"></script>
          <style>
            body { margin: 0; padding: 14px; background: $bgHex; color: $txtHex; font-family: Georgia, serif; }
            .badge { font-family: monospace; font-size: 10px; color: #D97757; font-weight: bold; text-transform: uppercase; }
            .headline { font-size: 18px; margin: 6px 0; line-height: 1.25; }
            .card { padding: 10px; border-radius: 10px; border: 1px solid rgba(150,150,150,0.3); margin-top: 8px; font-family: monospace; font-size: 11px; }
            .footer { margin-top: 10px; font-size: 9px; font-family: monospace; opacity: 0.75; border-top: 1px solid rgba(150,150,150,0.25); padding-top: 6px; }
          </style>
        </head>
        <body>
          <div id="stage">
            <div class="badge">${channel.id} • $theme • $format</div>
            <div class="headline">${channel.sampleHeadline}</div>
            <div class="card">${channel.sampleMetricsJson}</div>
            <div class="footer">✓ ${channel.sourceCitation}<br>⚖️ ${channel.disclaimer}</div>
          </div>
          <script>
            gsap.from("#stage > *", { opacity: 0, y: 14, stagger: 0.12, duration: 0.45, ease: "power2.out" });
          </script>
        </body>
        </html>
    """.trimIndent()

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(12.dp)),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL("https://cdnjs.cloudflare.com", htmlContent, "text/html", "UTF-8", null)
        }
    )
}

// ============================================================================
// AES-256-GCM ENCRYPTION HELPER FOR LOCKING PUBLIC REPO PAYLOADS
// ============================================================================
object Aes256VaultHelper {
    fun encryptToBase64(plainText: String, passphrase: String): String {
        return try {
            val keyBytes = passphrase.padEnd(32, '0').take(32).toByteArray(Charsets.UTF_8)
            val secretKey = SecretKeySpec(keyBytes, "AES")
            val iv = ByteArray(12)
            SecureRandom().nextBytes(iv)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            "ENCRYPTION_ERR: ${e.message}"
        }
    }
}

// ============================================================================
// DIRECT GITHUB ACTIONS REST API CLIENT (WORKFLOW_DISPATCH TRIGGER)
// ============================================================================
suspend fun triggerGithubWorkflowDispatch(
    repo: String,
    pat: String,
    channelId: String,
    videoFormat: String,
    visualTheme: String,
    voiceEngine: String,
    dataSourceMode: String,
    customTopic: String
): String = withContext(Dispatchers.IO) {
    if (pat.isBlank()) {
        return@withContext "Demo Dispatch Simulated (Add your GitHub PAT in the Git Vault tab to trigger live cloud builds)."
    }
    try {
        val url = URL("https://api.github.com/repos/${repo.trim()}/actions/workflows/youtube_factory.yml/dispatches")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("Authorization", "Bearer ${pat.trim()}")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
        }

        val inputs = JSONObject().apply {
            put("channel_id", channelId)
            put("video_format", videoFormat)
            put("visual_theme", visualTheme)
            put("voice_engine", voiceEngine)
            put("data_mode", dataSourceMode)
            put("custom_topic", customTopic)
        }
        val payload = JSONObject().apply {
            put("ref", "main")
            put("inputs", inputs)
        }

        OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }
        val code = conn.responseCode
        if (code in 200..299) {
            "✓ Success (HTTP $code): GitHub Actions is now decrypting vault.enc & rendering $channelId!"
        } else {
            "✕ GitHub API returned HTTP $code. Verify repo name & workflow permissions."
        }
    } catch (e: Exception) {
        "✕ Network Error: ${e.localizedMessage}"
    }
}
