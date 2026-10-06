package com.furflix.app.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.stringResource
import com.furflix.app.R

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.furflix.app.ui.components.HazeTopAppBar
import com.furflix.app.ui.theme.*
import com.furflix.app.viewmodel.MainViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onLoginSuccess: () -> Unit,
    viewModel: MainViewModel = viewModel()
) {
    val hazeState = remember { HazeState() }

    Scaffold(
        topBar = {
            HazeTopAppBar(
                title = { Text(stringResource(R.string.login_title)) },
                hazeState = hazeState,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        LoginContent(
            modifier = Modifier
                .padding(padding)
                .hazeSource(state = hazeState),
            onLoginSuccess = onLoginSuccess,
            viewModel = viewModel
        )
    }
}

@Composable
fun LoginContent(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onLoginSuccess: () -> Unit,
    viewModel: MainViewModel = viewModel()
) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoggingIn by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isPageReady by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var loginWebView by remember { mutableStateOf<WebView?>(null) }
    // 连接/验证状态文案在组合层解析成值，回调里只捕获结果（lint 口径）
    val statusConnecting = stringResource(R.string.login_status_connecting)
    val statusWaiting = stringResource(R.string.login_status_waiting)
    val statusChallenge = stringResource(R.string.login_status_challenge)
    val statusTimeout = stringResource(R.string.login_status_timeout)
    val errorFormNotReady = stringResource(R.string.login_form_not_ready)
    val browserTitle = stringResource(R.string.login_browser_title)
    var connectingStatus by remember { mutableStateOf(statusConnecting) }
    var showWebLogin by remember { mutableStateOf(false) }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) onLoginSuccess()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
        ) {
            // Invisible WebView handles Cloudflare, loads login page.
            HybridLoginWebView(
                onPageReady = { isPageReady = true },
                onLoginDetected = { cookies, detectedUsername ->
                    viewModel.loginWithCookies(cookies, detectedUsername)
                },
                onError = { raw ->
                    // 原始描述传回，i18n 模板在组合层拼装
                    errorMessage = raw
                },
                onStatusUpdate = { connectingStatus = it },
                onChallengeDetected = {
                    // 交互式 CF 挑战在 1dp 隐形 WebView 里无法点击：自动浮出
                    // 全屏可交互 WebView 让用户完成验证
                    connectingStatus = statusChallenge
                    showWebLogin = true
                },
                statusConnecting = statusConnecting,
                statusWaiting = statusWaiting,
                statusChallenge = statusChallenge,
                statusTimeout = statusTimeout,
                webViewRef = { loginWebView = it }
            )

            // Native login UI overlay
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(DarkBackground, Color(0xFF0A0A0C))))
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp)
                    .padding(
                        top = 60.dp + contentPadding.calculateTopPadding(),
                        bottom = 24.dp + contentPadding.calculateBottomPadding()
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Pets,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "FurFlix",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.login_sign_in),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SubtleGray
                )
                Spacer(modifier = Modifier.height(40.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; errorMessage = null },
                    label = { Text(stringResource(R.string.login_username)) },
                    singleLine = true,
                    enabled = !isLoggingIn,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = BorderDark,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMessage = null },
                    label = { Text(stringResource(R.string.login_password)) },
                    singleLine = true,
                    enabled = !isLoggingIn,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = stringResource(R.string.cd_toggle_password),
                                tint = SubtleGray
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = BorderDark,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (errorMessage != null) {
                    Text(
                        text = stringResource(R.string.login_error_connection, errorMessage ?: ""),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))



                Button(
                    onClick = {
                        if (username.isNotBlank() && password.isNotBlank()) {
                            isLoggingIn = true
                            errorMessage = null
                            val safeUser = username.replace("\\", "\\\\").replace("\"", "\\\"")
                            val safePass = password.replace("\\", "\\\\").replace("\"", "\\\"")
                            val js = """
                                (function() {
                                    if (document.readyState !== 'complete') return 'PAGE_NOT_READY';
                                    var u = document.querySelector('input[name="name"]');
                                    var p = document.querySelector('input[name="pass"]');
                                    if (!u || !p) return 'FORM_NOT_FOUND';
                                    
                                    var tsWidget = document.querySelector('.cf-turnstile');
                                    var tsResponse = document.querySelector('[name="cf-turnstile-response"]');
                                    if (tsWidget && (!tsResponse || !tsResponse.value)) {
                                        u.value = "$safeUser";
                                        p.value = "$safePass";
                                        return 'FORM_NOT_FOUND';
                                    }

                                    u.value = "$safeUser";
                                    p.value = "$safePass";
                                    u.dispatchEvent(new Event('input', {bubbles: true}));
                                    p.dispatchEvent(new Event('input', {bubbles: true}));
                                    var btn = document.querySelector('button[type="submit"], input[type="submit"], #login-button, .login-button, button[name="login"]');
                                    if (btn) { btn.click(); return 'SUBMITTED'; }
                                    var form = u.closest('form');
                                    if (form) { form.submit(); return 'SUBMITTED'; }
                                    return 'NO_SUBMIT';
                                })();
                            """.trimIndent()
                            Log.d("FurFlixLogin", "User clicked Sign In. Injecting script...")
                            tryInject(loginWebView, js, 5, 800) { result ->
                                val r = result?.removeSurrounding("\"")?.trim() ?: ""
                                Log.d("FurFlixLogin", "Invisible WebView inject result: $r")
                                if (r == "FORM_NOT_FOUND" || r == "PAGE_NOT_READY") {
                                    isLoggingIn = false
                                    showWebLogin = true
                                } else if (r == "NO_SUBMIT") {
                                    isLoggingIn = false
                                    errorMessage = errorFormNotReady
                                } else if (r == "SUBMITTED") {
                                    pollPostSubmit(loginWebView) { state ->
                                        Log.d("FurFlixLogin", "Post-submit state: $state")
                                        if (state == "CHALLENGE" || state == "ERROR" || state == "TIMEOUT") {
                                            isLoggingIn = false
                                            showWebLogin = true
                                        }
                                    }
                                }
                            }
                        }
                    },
                    enabled = !isLoggingIn && username.isNotBlank() && password.isNotBlank() && isPageReady,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = MaterialTheme.shapes.medium
                ) {
                    if (isLoggingIn) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.Black,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            stringResource(R.string.login_button),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black
                        )
                    }
                }

                if (!isPageReady && !isLoggingIn) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = SubtleGray,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(connectingStatus, color = SubtleGray, style = MaterialTheme.typography.bodySmall)
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Fallback: open WebView login in a full-screen dialog
                OutlinedButton(
                    onClick = { showWebLogin = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SubtleGray),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark)
                ) {
                    Text(stringResource(R.string.login_browser), color = SubtleGray)
                }
            }

            if (showWebLogin) {
                Dialog(
                    onDismissRequest = { showWebLogin = false },
                    properties = DialogProperties(
                        usePlatformDefaultWidth = false,
                        decorFitsSystemWindows = false
                    )
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = DarkBackground
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { showWebLogin = false }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.cd_close),
                                        tint = SubtleGray
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    browserTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .navigationBarsPadding()
                            ) {
                                FaLoginWebView(
                                    onLoginDetected = { cookies, detectedUsername ->
                                        viewModel.loginWithCookies(cookies, detectedUsername)
                                        showWebLogin = false
                                    },
                                    initialCredentials = if (username.isNotEmpty() && password.isNotEmpty())
                                        Pair(username, password) else null,
                                    overlayTitle = stringResource(R.string.login_overlay_title),
                                    overlayDesc = stringResource(R.string.login_overlay_desc)
                                )
                            }
                        }
                    }
                }
            }
        }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HybridLoginWebView(
    onPageReady: () -> Unit,
    onLoginDetected: (String, String) -> Unit,
    onError: (String) -> Unit,
    onStatusUpdate: (String) -> Unit,
    onChallengeDetected: () -> Unit,
    statusConnecting: String,
    statusWaiting: String,
    statusChallenge: String,
    statusTimeout: String,
    webViewRef: (WebView) -> Unit
) {
    var hasDetected by remember { mutableStateOf(false) }
    var pageLoaded by remember { mutableStateOf(false) }

    val onDetectRef = rememberUpdatedState(onLoginDetected)
    val onChallengeRef = rememberUpdatedState(onChallengeDetected)

    fun checkLogin(view: WebView?, url: String?) {
        if (hasDetected) return

        val cm = CookieManager.getInstance()
        val c1 = cm.getCookie("https://furaffinity.net") ?: ""
        val c2 = cm.getCookie("https://www.furaffinity.net") ?: ""
        val allCookies = listOf(c1, c2).filter { it.isNotEmpty() }.joinToString("; ")

        val hasAuthCookie = allCookies.split(";").any { part ->
            part.trim().startsWith("a=") && part.trim().length > 3
        }
        val isOnLoginPage = url?.contains("/login") == true

        if (!isOnLoginPage && hasAuthCookie) {
            hasDetected = true
            view?.evaluateJavascript(
                """
                (function() {
                    var el = document.querySelector('.classic-header a[href*="/user/"], .user-nav a[href*="/user/"], a[href*="/~"]');
                    if (el) return el.textContent.trim();
                    var el2 = document.querySelector('a[href*="/user/"]');
                    if (el2) return el2.textContent.trim();
                    return '';
                })();
                """.trimIndent()
            ) { result ->
                val uname = result?.removeSurrounding("\"")?.trim() ?: ""
                onDetectRef.value(allCookies, uname.ifEmpty { "Logged in" })
            }
        }
    }

    AndroidView(
        modifier = Modifier.size(1.dp),
        factory = { ctx ->
            WebView(ctx).apply {
                webViewRef(this)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    // UA 单一来源（设备真实 WebView 内核版本）：Turnstile 校验
                    // UA 声称版本与内核指纹的一致性，硬编码版本会拒绝静默签发
                    userAgentString = com.furflix.app.data.remote.UserAgent.desktop()
                }
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        checkLogin(view, url)
                        if (!pageLoaded) {
                            pageLoaded = true
                            onStatusUpdate(statusWaiting)
                            if (view != null) {
                                pollForForm(
                                    view,
                                    onPageReady,
                                    onStatusUpdate,
                                    onChallengeDetected = { onChallengeRef.value() },
                                    statusWaiting = statusWaiting,
                                    statusChallenge = statusChallenge,
                                    statusTimeout = statusTimeout
                                )
                            }
                        }
                    }

                    override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                        onError(description ?: "unknown")
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        super.onProgressChanged(view, newProgress)
                        if (newProgress >= 90) checkLogin(view, view?.url)
                    }
                }

                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                loadUrl("https://www.furaffinity.net/login/")
            }.also { webViewRef(it) }
        }
    )
}

private fun pollForForm(
    view: WebView,
    onReady: () -> Unit,
    onStatusUpdate: (String) -> Unit,
    onChallengeDetected: () -> Unit,
    statusWaiting: String,
    statusChallenge: String,
    statusTimeout: String,
    attempt: Int = 0
) {
    val maxAttempts = 25

    view.evaluateJavascript(
        "(function(){if(document.readyState!=='complete')return'LOADING';var u=document.querySelector('input[name=\"name\"]');var p=document.querySelector('input[name=\"pass\"]');if(u&&p)return'READY';var t=(document.title||'').toLowerCase();if(t.indexOf('just a moment')>=0||t.indexOf('checking')>=0)return'CHALLENGE';return'WAITING';})();"
    ) { result ->
        val r = result?.removeSurrounding("\"")?.trim() ?: ""
        Log.d("FurFlixLogin", "pollForForm attempt $attempt: result=$r")
        when {
            r == "READY" -> onReady()
            r == "CHALLENGE" -> {
                // 交互式挑战：隐形 WebView 无法点击，交给全屏 WebView 处理
                onStatusUpdate(statusChallenge)
                onChallengeDetected()
                onReady()
            }
            attempt >= maxAttempts -> {
                onStatusUpdate(statusTimeout)
                onReady()
            }
            else -> {
                onStatusUpdate(statusWaiting)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    pollForForm(
                        view,
                        onReady,
                        onStatusUpdate,
                        onChallengeDetected,
                        statusWaiting,
                        statusChallenge,
                        statusTimeout,
                        attempt + 1
                    )
                }, 1000)
            }
        }
    }
}

private fun pollPostSubmit(
    view: WebView?,
    attempt: Int = 0,
    onResult: (String) -> Unit
) {
    if (view == null || attempt > 25) { // ~12.5s timeout
        onResult("TIMEOUT")
        return
    }
    val js = "(function(){var t=(document.title||'').toLowerCase();if(t.indexOf('just a moment')>=0||t.indexOf('checking')>=0||t.indexOf('cloudflare')>=0)return'CHALLENGE';var b=document.body.innerText.toLowerCase();if(b.indexOf('incorrect username or password')>=0||b.indexOf('invalid login')>=0)return'ERROR';var u=document.querySelector('a[href*=\"/user/\"]');if(u)return'SUCCESS';return'WAITING';})();"
    view.evaluateJavascript(js) { result ->
        val r = result?.removeSurrounding("\"")?.trim() ?: ""
        if (r == "CHALLENGE" || r == "ERROR" || r == "SUCCESS") {
            onResult(r)
        } else {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                pollPostSubmit(view, attempt + 1, onResult)
            }, 500)
        }
    }
}


private fun tryInject(
    webView: WebView?,
    js: String,
    retriesLeft: Int,
    delayMs: Long,
    onResult: (String?) -> Unit
) {
    if (webView == null || retriesLeft <= 0) {
        Log.d("FurFlixLogin", "tryInject failed: webView=$webView, retriesLeft=$retriesLeft")
        onResult(null)
        return
    }
    webView.evaluateJavascript(js) { result ->
        val r = result?.removeSurrounding("\"")?.trim() ?: ""
        Log.d("FurFlixLogin", "tryInject check (retriesLeft=$retriesLeft): $r")
        if (r == "FORM_NOT_FOUND" && retriesLeft > 1) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                tryInject(webView, js, retriesLeft - 1, delayMs, onResult)
            }, delayMs)
        } else {
            onResult(result)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun FaLoginWebView(
    onLoginDetected: (String, String) -> Unit,
    initialCredentials: Pair<String, String>? = null,
    overlayTitle: String,
    overlayDesc: String
) {
    var hasDetected by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val onDetectRef = rememberUpdatedState(onLoginDetected)

    var hasInjected by remember { mutableStateOf(false) }

    // overlay 文案在组合层解析为值，注入脚本捕获结果（lint 口径）
    val safeOverlayTitle = overlayTitle.replace("'", "\\'")
    val safeOverlayDesc = overlayDesc.replace("'", "\\'")

    LaunchedEffect(webView, initialCredentials) {
        if (hasInjected) return@LaunchedEffect
        val creds = initialCredentials ?: return@LaunchedEffect
        val wv = webView ?: return@LaunchedEffect
        hasInjected = true
        val safeUser = creds.first.replace("\\", "\\\\").replace("\"", "\\\"")
        val safePass = creds.second.replace("\\", "\\\\").replace("\"", "\\\"")
        val js = """
            (function() {
                var t = (document.title || '').toLowerCase();
                var isChallenge = t.indexOf('just a moment') >= 0 || t.indexOf('checking') >= 0 ||
                    document.body.innerHTML.indexOf('_cf_chl_opt') >= 0;

                // 挑战态：移除遮罩让 CF 页面可见可交互，turnstile 置于屏幕中央，
                // 外层轮询会在挑战通过后自动继续填表提交
                if (isChallenge) {
                    var veil = document.getElementById('ffx-veil');
                    if (veil) veil.remove();
                    var w = document.querySelector('.cf-turnstile');
                    if (w) {
                        w.style.position = 'fixed';
                        w.style.top = '55%';
                        w.style.left = '50%';
                        w.style.transform = 'translate(-50%, -50%)';
                        w.style.zIndex = '100000';
                    }
                    return 'CHALLENGE';
                }

                if (!window.__uiHidden) {
                    window.__uiHidden = true;
                    var overlay = document.createElement('div');
                    overlay.id = 'ffx-veil';
                    overlay.style.position = 'fixed';
                    overlay.style.top = '0';
                    overlay.style.left = '0';
                    overlay.style.width = '100vw';
                    overlay.style.height = '100vh';
                    overlay.style.backgroundColor = '#0A0A0C';
                    overlay.style.zIndex = '99999';
                    overlay.style.display = 'flex';
                    overlay.style.flexDirection = 'column';
                    overlay.style.alignItems = 'center';
                    overlay.style.justifyContent = 'center';
                    overlay.style.fontFamily = 'sans-serif';
                    overlay.innerHTML = '<h3 style="margin-bottom: 20px; color: #40E0D0; font-size: 20px; font-weight: 600;">$safeOverlayTitle</h3><p style="color: #A0A0A0; font-size: 14px;">$safeOverlayDesc</p>';
                    document.body.appendChild(overlay);
                }

                var u = document.querySelector('input[name="name"]');
                var p = document.querySelector('input[name="pass"]');
                if (!u || !p) return 'FORM_NOT_FOUND';

                var tsWidget = document.querySelector('.cf-turnstile');

                var tsResponse = document.querySelector('[name="cf-turnstile-response"]');
                if (tsWidget && (!tsResponse || !tsResponse.value)) {
                    // Turnstile 未签发：露出 widget 供交互（去遮罩），等待下一轮
                    var veil = document.getElementById('ffx-veil');
                    if (veil) veil.remove();
                    if (tsWidget) {
                        tsWidget.style.position = 'fixed';
                        tsWidget.style.top = '55%';
                        tsWidget.style.left = '50%';
                        tsWidget.style.transform = 'translate(-50%, -50%)';
                        tsWidget.style.zIndex = '100000';
                    }
                    u.value = "$safeUser";
                    p.value = "$safePass";
                    return 'FORM_NOT_FOUND';
                }

                var veil = document.getElementById('ffx-veil');
                if (veil) veil.remove();
                u.value = "$safeUser";
                p.value = "$safePass";
                u.dispatchEvent(new Event('input', {bubbles: true}));
                p.dispatchEvent(new Event('input', {bubbles: true}));
                var btn = document.querySelector('button[type="submit"], input[type="submit"], #login-button, .login-button, button[name="login"]');
                if (btn) { btn.click(); return 'SUBMITTED'; }
                var form = u.closest('form');
                if (form) { form.submit(); return 'SUBMITTED'; }
                return 'FILLED';
            })();
        """.trimIndent()
        Log.d("FurFlixLogin", "WebLogin visible, injecting credentials...")
        tryInject(wv, js, 60, 1000) { result ->
            Log.d("FurFlixLogin", "WebLogin inject result: $result")
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // UA 单一来源：与 invisible WebView、OkHttp 抓取链路同版本，
                    // cf_clearance 的 UA 绑定在所有链路间一致
                    settings.userAgentString = com.furflix.app.data.remote.UserAgent.desktop()
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false

                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            // 旧页面的遮罩在导航瞬间清掉：若新页是挑战页，
                            // 必须让 CF 的验证 UI 完整可见可交互
                            view?.evaluateJavascript(
                                "(function(){var v=document.getElementById('ffx-veil');if(v)v.remove();})();",
                                null
                            )
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            if (hasDetected) return
                            val cm = CookieManager.getInstance()
                            val c1 = cm.getCookie("https://furaffinity.net") ?: ""
                            val c2 = cm.getCookie("https://www.furaffinity.net") ?: ""
                            val allCookies = listOf(c1, c2).filter { it.isNotEmpty() }.joinToString("; ")
                            if (allCookies.split(";").any { p -> p.trim().startsWith("a=") && p.trim().length > 3 }) {
                                hasDetected = true
                                view?.evaluateJavascript("(function(){var el=document.querySelector('a[href*=\"/user/\"]');return el?el.textContent.trim():'';})();") { result ->
                                    val uname = result?.removeSurrounding("\"")?.trim() ?: ""
                                    onDetectRef.value(allCookies, uname.ifEmpty { "Logged in" })
                                }
                            }
                        }
                    }
                    loadUrl("https://www.furaffinity.net/login/")
                }.also { webView = it }
            }
        )
    }
}
