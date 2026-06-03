package com.adischat.app.ui.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adischat.app.R
import com.adischat.app.ui.theme.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.rememberInfiniteTransition

// ── Page enum ─────────────────────────────────────────────────────────────────

private enum class AuthPage { WELCOME, SIGNUP, EMAIL_OTP, LOGIN }

// ── Root ──────────────────────────────────────────────────────────────────────

@Composable
fun AuthScreen(onAuthSuccess: () -> Unit) {
    val vm: AuthViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var page by remember { mutableStateOf(AuthPage.WELCOME) }

    // Lifted sign-up state (preserved when navigating back from OTP page)
    var signUpName     by remember { mutableStateOf("") }
    var signUpPhone    by remember { mutableStateOf("") }
    var signUpEmail    by remember { mutableStateOf("") }
    var signUpPassword by remember { mutableStateOf("") }

    LaunchedEffect(state) {
        when (state) {
            is AuthState.Success      -> onAuthSuccess()
            is AuthState.EmailOtpSent -> if (page == AuthPage.SIGNUP) page = AuthPage.EMAIL_OTP
            else                      -> Unit
        }
    }

    val googleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                .getResult(ApiException::class.java)
            account.idToken?.let { vm.signInWithGoogle(it) }
        } catch (_: ApiException) {}
    }

    fun launchGoogle() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleLauncher.launch(GoogleSignIn.getClient(context, gso).signInIntent)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                (slideInHorizontally { it } + fadeIn())
                    .togetherWith(slideOutHorizontally { -it } + fadeOut())
            },
            label = "auth_page",
        ) { target ->
            when (target) {
                AuthPage.WELCOME -> WelcomePage(
                    loading         = state is AuthState.Loading,
                    error           = (state as? AuthState.Error)?.message,
                    onSignUp        = { page = AuthPage.SIGNUP; vm.resetState() },
                    onGoogle        = { launchGoogle() },
                    onSubmit        = { id, pw -> vm.login(id, pw) },
                    onPasswordReset = { email, cb -> vm.sendPasswordReset(email, cb) },
                )
                AuthPage.SIGNUP -> SignUpPage(
                    loading          = state is AuthState.Loading,
                    error            = (state as? AuthState.Error)?.message,
                    name             = signUpName,
                    phone            = signUpPhone,
                    email            = signUpEmail,
                    password         = signUpPassword,
                    onNameChange     = { signUpName = it },
                    onPhoneChange    = { signUpPhone = it },
                    onEmailChange    = { signUpEmail = it },
                    onPasswordChange = { signUpPassword = it },
                    onBack           = { page = AuthPage.WELCOME; vm.resetState() },
                    onSubmit         = { vm.signUp(signUpName, signUpPhone, signUpEmail, signUpPassword) },
                    onLoginClick     = { page = AuthPage.WELCOME; vm.resetState() },
                )
                AuthPage.EMAIL_OTP -> EmailOtpPage(
                    email        = (state as? AuthState.EmailOtpSent)?.email ?: signUpEmail,
                    devOtp       = (state as? AuthState.EmailOtpSent)?.devOtp,
                    loading      = state is AuthState.Loading,
                    error        = (state as? AuthState.Error)?.message,
                    onBack       = { page = AuthPage.SIGNUP; vm.resetState() },
                    onVerify     = { otp -> vm.verifySignUpOtp(otp) },
                    onResend     = { vm.resendEmailOtp() },
                    onLoginClick = { page = AuthPage.WELCOME; vm.resetState() },
                )
                AuthPage.LOGIN -> LoginPage(
                    loading         = state is AuthState.Loading,
                    error           = (state as? AuthState.Error)?.message,
                    onBack          = { page = AuthPage.WELCOME; vm.resetState() },
                    onSubmit        = { id, pw -> vm.login(id, pw) },
                    onPasswordReset = { email, cb -> vm.sendPasswordReset(email, cb) },
                    onSignUpClick   = { page = AuthPage.SIGNUP; vm.resetState() },
                )
            }
        }
    }
}

// ── Welcome ──────────────────────────────────────────────────────────────────

@Composable
private fun WelcomePage(
    loading:         Boolean = false,
    error:           String? = null,
    onSignUp:        () -> Unit,
    onGoogle:        () -> Unit,
    onSubmit:        (email: String, password: String) -> Unit,
    onPasswordReset: (String, (Boolean, String) -> Unit) -> Unit,
) {
    var email           by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    val focusManager    = LocalFocusManager.current
    val canSubmit       = !loading && email.isNotBlank() && password.isNotBlank()
    val isDark          = isSystemInDarkTheme()

    // ── Theme tokens ──────────────────────────────────────────────────────────
    val bgColor      = if (isDark) Color(0xFF0D1117) else Color.White
    val accent       = if (isDark) Color(0xFF3FB950) else Color(0xFF16A34A)
    val accentAlt    = if (isDark) Color(0xFF238636) else Color(0xFF22C55E)
    val textPrimary  = if (isDark) Color(0xFFE6EDF3) else Color(0xFF111827)
    val textMuted    = if (isDark) Color(0xFF8B949E) else Color(0xFF6B7280)
    val cardBg       = if (isDark) Color(0xFF161B22) else Color.White
    val cardBorder   = if (isDark) Color(0xFF30363D)  else Color(0xFFE5E7EB)
    val dividerColor = if (isDark) Color(0xFF30363D)  else Color(0xFFE5E7EB)
    val googleBg     = if (isDark) Color(0xFF21262D)  else Color.White
    val googleBorder = if (isDark) Color(0xFF30363D)  else Color(0xFFD1D5DB)
    val logoBoxBg    = if (isDark) Color(0xFF161B22)  else Color(0xFFF0FDF4)
    val orb1Color    = if (isDark) Color(0xFF238636).copy(alpha = 0.18f) else Color(0xFF22C55E).copy(alpha = 0.10f)
    val orb2Color    = if (isDark) Color(0xFF1F6FEB).copy(alpha = 0.13f) else Color(0xFF10B981).copy(alpha = 0.07f)

    // ── Animations ────────────────────────────────────────────────────────────
    val inf   = rememberInfiniteTransition(label = "bg")
    val pulse by inf.animateFloat(
        initialValue  = 0.6f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = EaseInOutSine), RepeatMode.Reverse),
        label         = "pulse",
    )
    val orb1 by inf.animateFloat(
        initialValue  = 0.85f,
        targetValue   = 1.18f,
        animationSpec = infiniteRepeatable(tween(3600), RepeatMode.Reverse),
        label         = "orb1",
    )
    val orb2 by inf.animateFloat(
        initialValue  = 1.1f,
        targetValue   = 0.82f,
        animationSpec = infiniteRepeatable(tween(4800), RepeatMode.Reverse),
        label         = "orb2",
    )

    Box(Modifier.fillMaxSize().background(bgColor)) {
        // Background orbs
        Box(
            Modifier
                .size(360.dp).offset((-90).dp, (-90).dp).scale(orb1)
                .background(Brush.radialGradient(listOf(orb1Color, Color.Transparent)), CircleShape)
        )
        Box(
            Modifier
                .size(300.dp).align(Alignment.BottomEnd).offset(70.dp, 90.dp).scale(orb2)
                .background(Brush.radialGradient(listOf(orb2Color, Color.Transparent)), CircleShape)
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(52.dp))

            // ── Logo ──────────────────────────────────────────────────────────
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(108.dp).scale(pulse).alpha(if (isDark) 0.55f else 0.40f)
                        .background(
                            Brush.radialGradient(listOf(accent.copy(alpha = 0.50f), Color.Transparent)),
                            CircleShape,
                        )
                )
                Box(
                    Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(logoBoxBg)
                        .border(
                            1.5.dp,
                            Brush.linearGradient(listOf(accent, accentAlt)),
                            RoundedCornerShape(22.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter            = painterResource(R.drawable.logo),
                        contentDescription = "AdisChat",
                        contentScale       = ContentScale.Fit,
                        modifier           = Modifier.size(52.dp),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Brand name
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp)) {
                        append("Adis")
                    }
                    withStyle(SpanStyle(color = accent, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp)) {
                        append("Chat")
                    }
                }
            )
            Spacer(Modifier.height(6.dp))
            Text("Sign in to continue", fontSize = 14.sp, color = textMuted, fontWeight = FontWeight.Medium)

            Spacer(Modifier.height(36.dp))

            // ── Form card ─────────────────────────────────────────────────────
            Surface(
                shape    = RoundedCornerShape(20.dp),
                color    = cardBg,
                border   = BorderStroke(1.dp, cardBorder),
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = if (isDark) 0.dp else 2.dp,
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    AuthField(
                        value         = email,
                        onValueChange = { email = it },
                        placeholder   = "Email or username",
                        icon          = Icons.Default.Email,
                        keyboardType  = KeyboardType.Email,
                        imeAction     = ImeAction.Next,
                        isDark        = isDark,
                        accent        = accent,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        AuthPasswordField(
                            value         = password,
                            onValueChange = { password = it },
                            visible       = passwordVisible,
                            onToggle      = { passwordVisible = !passwordVisible },
                            onDone        = { focusManager.clearFocus(); if (canSubmit) onSubmit(email.trim(), password) },
                            isDark        = isDark,
                            accent        = accent,
                        )
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                            Text(
                                "Forgot password?",
                                color      = accent,
                                fontSize   = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier   = Modifier.clickable { showResetDialog = true },
                            )
                        }
                    }
                }
            }

            // Error
            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFEF4444).copy(alpha = 0.10f))
                        .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.30f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Default.Warning, null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                    Text(error, color = Color(0xFFEF4444), fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Sign In button ────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth().height(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (canSubmit)
                            Brush.horizontalGradient(listOf(accentAlt, accent))
                        else
                            Brush.horizontalGradient(listOf(
                                accentAlt.copy(alpha = if (isDark) 0.25f else 0.35f),
                                accent.copy(alpha = if (isDark) 0.25f else 0.35f),
                            ))
                    )
                    .then(if (canSubmit) Modifier.clickable { onSubmit(email.trim(), password) } else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                if (loading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                } else {
                    Text(
                        "Sign In",
                        color         = if (canSubmit) Color.White else Color.White.copy(alpha = 0.4f),
                        fontWeight    = FontWeight.ExtraBold,
                        fontSize      = 16.sp,
                        letterSpacing = 0.5.sp,
                    )
                }
            }

            Spacer(Modifier.height(22.dp))

            // ── Divider ───────────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(Modifier.weight(1f), color = dividerColor)
                Text("  or continue with  ", color = textMuted, fontSize = 12.sp)
                HorizontalDivider(Modifier.weight(1f), color = dividerColor)
            }

            Spacer(Modifier.height(22.dp))

            // ── Google button ─────────────────────────────────────────────────
            Surface(
                onClick  = onGoogle,
                shape    = RoundedCornerShape(16.dp),
                color    = googleBg,
                border   = BorderStroke(1.dp, googleBorder),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shadowElevation = if (isDark) 0.dp else 1.dp,
            ) {
                Row(
                    Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Continue with Google",
                        color      = textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 15.sp,
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── No account ────────────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("No account? ", color = textMuted, fontSize = 14.sp)
                Text(
                    "Create Account",
                    color      = accent,
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier   = Modifier.clickable { onSignUp() },
                )
            }
            Spacer(Modifier.height(10.dp))
            run {
                val uriHandler = LocalUriHandler.current
                val legal = buildAnnotatedString {
                    append("By continuing you agree to our ")
                    pushStringAnnotation("url", "https://www.adischat.com/terms")
                    withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append("Terms") }
                    pop()
                    append(" & ")
                    pushStringAnnotation("url", "https://www.adischat.com/privacy")
                    withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append("Privacy Policy") }
                    pop()
                }
                ClickableText(
                    text = legal,
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 11.sp,
                        color = textMuted.copy(alpha = 0.55f),
                        textAlign = TextAlign.Center,
                    ),
                    onClick = { offset ->
                        legal.getStringAnnotations("url", offset, offset).firstOrNull()
                            ?.let { uriHandler.openUri(it.item) }
                    },
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    if (showResetDialog) {
        PasswordResetDialog(onDismiss = { showResetDialog = false }, onSend = onPasswordReset)
    }
}

// ── Auth input fields (theme-aware) ─────────────────────────────────────────

@Composable
private fun authFieldColors(isDark: Boolean, accent: Color, isError: Boolean = false) =
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor      = accent,
        unfocusedBorderColor    = if (isError) Color(0xFFEF4444).copy(alpha = 0.7f)
                                  else if (isDark) Color(0xFF30363D) else Color(0xFFE5E7EB),
        focusedContainerColor   = accent.copy(alpha = 0.06f),
        unfocusedContainerColor = if (isDark) Color(0xFF0D1117) else Color(0xFFF9FAFB),
        focusedTextColor        = if (isDark) Color(0xFFE6EDF3) else Color(0xFF111827),
        unfocusedTextColor      = if (isDark) Color(0xFFE6EDF3) else Color(0xFF111827),
        cursorColor             = accent,
        errorBorderColor        = Color(0xFFEF4444),
        errorContainerColor     = Color(0xFFEF4444).copy(alpha = 0.06f),
    )

@Composable
private fun AuthField(
    value:          String,
    onValueChange:  (String) -> Unit,
    placeholder:    String,
    icon:           ImageVector,
    isDark:         Boolean,
    accent:         Color,
    keyboardType:   KeyboardType = KeyboardType.Text,
    imeAction:      ImeAction    = ImeAction.Next,
    isError:        Boolean      = false,
    supportingText: String?      = null,
) {
    val focused = remember { mutableStateOf(false) }
    val iconTint = if (isError) Color(0xFFEF4444)
                  else if (focused.value) accent
                  else if (isDark) Color(0xFF8B949E) else Color(0xFF9CA3AF)
    OutlinedTextField(
        value          = value,
        onValueChange  = onValueChange,
        modifier       = Modifier.fillMaxWidth().onFocusChanged { focused.value = it.isFocused },
        shape          = RoundedCornerShape(14.dp),
        singleLine     = true,
        isError        = isError,
        supportingText = if (supportingText != null) ({
            Text(supportingText, color = Color(0xFFEF4444), fontSize = 11.sp)
        }) else null,
        placeholder    = {
            Text(placeholder, color = if (isDark) Color(0xFF6E7681) else Color(0xFF9CA3AF), fontSize = 14.sp)
        },
        leadingIcon    = { Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp)) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        colors          = authFieldColors(isDark, accent, isError),
    )
}

@Composable
private fun AuthPasswordField(
    value:       String,
    onValueChange: (String) -> Unit,
    visible:     Boolean,
    onToggle:    () -> Unit,
    onDone:      () -> Unit,
    isDark:      Boolean,
    accent:      Color,
    placeholder: String    = "Password",
    imeAction:   ImeAction = ImeAction.Done,
) {
    val focused = remember { mutableStateOf(false) }
    val iconTint = if (focused.value) accent else if (isDark) Color(0xFF8B949E) else Color(0xFF9CA3AF)
    OutlinedTextField(
        value         = value,
        onValueChange = onValueChange,
        modifier      = Modifier.fillMaxWidth().onFocusChanged { focused.value = it.isFocused },
        shape         = RoundedCornerShape(14.dp),
        singleLine    = true,
        placeholder   = {
            Text(placeholder, color = if (isDark) Color(0xFF6E7681) else Color(0xFF9CA3AF), fontSize = 14.sp)
        },
        leadingIcon   = { Icon(Icons.Default.Lock, null, tint = iconTint, modifier = Modifier.size(20.dp)) },
        trailingIcon  = {
            IconButton(onClick = onToggle) {
                Icon(
                    if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    null,
                    tint = if (isDark) Color(0xFF8B949E) else Color(0xFF9CA3AF),
                    modifier = Modifier.size(20.dp),
                )
            }
        },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        colors = authFieldColors(isDark, accent),
    )
}

@Composable
private fun AuthPhoneField(
    value:         String,
    onValueChange: (String) -> Unit,
    isDark:        Boolean,
    accent:        Color,
    isError:       Boolean = false,
    errorText:     String? = null,
) {
    OutlinedTextField(
        value         = value,
        onValueChange = { raw ->
            val d = raw.filter { it.isDigit() }
            if (d.length <= 9) onValueChange(d)
        },
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        singleLine = true,
        isError    = isError,
        supportingText = if (errorText != null) ({
            Text(errorText, color = Color(0xFFEF4444), fontSize = 11.sp)
        }) else null,
        placeholder = {
            Text("9XX XXX XXX", color = if (isDark) Color(0xFF6E7681) else Color(0xFF9CA3AF), fontSize = 14.sp)
        },
        prefix = {
            Text("+251 ", color = accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
        colors = authFieldColors(isDark, accent, isError),
    )
}

// ── Legacy glass field wrappers (kept for LoginPage / other pages) ─────────────

@Composable
private fun GlassField(
    value:          String,
    onValueChange:  (String) -> Unit,
    placeholder:    String,
    icon:           ImageVector,
    keyboardType:   KeyboardType = KeyboardType.Text,
    imeAction:      ImeAction    = ImeAction.Next,
    isError:        Boolean      = false,
    supportingText: String?      = null,
) {
    val isDark = isSystemInDarkTheme()
    val accent = if (isDark) Color(0xFF3FB950) else Color(0xFF16A34A)
    AuthField(value, onValueChange, placeholder, icon, isDark, accent, keyboardType, imeAction, isError, supportingText)
}

@Composable
private fun GlassPasswordField(
    value:       String,
    onValueChange: (String) -> Unit,
    visible:     Boolean,
    onToggle:    () -> Unit,
    onDone:      () -> Unit,
    placeholder: String    = "Password",
    imeAction:   ImeAction = ImeAction.Done,
) {
    val isDark = isSystemInDarkTheme()
    val accent = if (isDark) Color(0xFF3FB950) else Color(0xFF16A34A)
    AuthPasswordField(value, onValueChange, visible, onToggle, onDone, isDark, accent, placeholder, imeAction)
}

@Composable
private fun GlassPhoneField(
    value:         String,
    onValueChange: (String) -> Unit,
    isError:       Boolean = false,
    errorText:     String? = null,
) {
    val isDark = isSystemInDarkTheme()
    val accent = if (isDark) Color(0xFF3FB950) else Color(0xFF16A34A)
    AuthPhoneField(value, onValueChange, isDark, accent, isError, errorText)
}

// ── Sign Up ───────────────────────────────────────────────────────────────────

@Composable
private fun SignUpPage(
    loading: Boolean, error: String?,
    name: String, phone: String, email: String, password: String,
    onNameChange: (String) -> Unit, onPhoneChange: (String) -> Unit,
    onEmailChange: (String) -> Unit, onPasswordChange: (String) -> Unit,
    onBack: () -> Unit, onSubmit: () -> Unit, onLoginClick: () -> Unit,
) {
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmVisible  by remember { mutableStateOf(false) }
    var agreedTerms     by remember { mutableStateOf(false) }
    val focusManager    = LocalFocusManager.current
    val uriHandler      = LocalUriHandler.current
    val isDark          = isSystemInDarkTheme()

    val phoneError      = if (phone.isNotEmpty()) validatePhone(phone) else null
    val emailError      = if (email.isNotEmpty() && !email.contains('@')) "Enter a valid email address" else null
    val pwdErrors       = if (password.isNotEmpty()) passwordRequirements(password) else emptyList()
    val pwdOk           = pwdErrors.isEmpty() && password.isNotEmpty()
    val confirmMismatch = confirmPassword.isNotEmpty() && confirmPassword != password
    val confirmOk       = confirmPassword.isNotEmpty() && confirmPassword == password

    val canSubmit = !loading
            && name.trim().length >= 2
            && phoneError == null && phone.length == 9
            && emailError == null && email.contains('@')
            && pwdOk && confirmOk
            && agreedTerms

    // ── Theme tokens ──────────────────────────────────────────────────────────
    val bgColor    = if (isDark) Color(0xFF0D1117) else Color.White
    val accent     = if (isDark) Color(0xFF3FB950) else Color(0xFF16A34A)
    val accentAlt  = if (isDark) Color(0xFF238636) else Color(0xFF22C55E)
    val textPrimary = if (isDark) Color(0xFFE6EDF3) else Color(0xFF111827)
    val textMuted  = if (isDark) Color(0xFF8B949E) else Color(0xFF6B7280)
    val cardBg     = if (isDark) Color(0xFF161B22) else Color.White
    val cardBorder = if (isDark) Color(0xFF30363D)  else Color(0xFFE5E7EB)
    val orb1Color  = if (isDark) Color(0xFF238636).copy(alpha = 0.18f) else Color(0xFF22C55E).copy(alpha = 0.09f)
    val orb2Color  = if (isDark) Color(0xFF1F6FEB).copy(alpha = 0.12f) else Color(0xFF10B981).copy(alpha = 0.06f)

    // ── Animations ────────────────────────────────────────────────────────────
    val inf  = rememberInfiniteTransition(label = "su_bg")
    val orb1 by inf.animateFloat(
        initialValue  = 0.9f,
        targetValue   = 1.2f,
        animationSpec = infiniteRepeatable(tween(3800), RepeatMode.Reverse),
        label         = "su_orb1",
    )
    val orb2 by inf.animateFloat(
        initialValue  = 1.15f,
        targetValue   = 0.8f,
        animationSpec = infiniteRepeatable(tween(5000), RepeatMode.Reverse),
        label         = "su_orb2",
    )

    Box(Modifier.fillMaxSize().background(bgColor)) {
        Box(
            Modifier
                .size(320.dp).align(Alignment.TopEnd).offset(100.dp, (-80).dp).scale(orb1)
                .background(Brush.radialGradient(listOf(orb2Color, Color.Transparent)), CircleShape)
        )
        Box(
            Modifier
                .size(380.dp).align(Alignment.BottomStart).offset((-100).dp, 120.dp).scale(orb2)
                .background(Brush.radialGradient(listOf(orb1Color, Color.Transparent)), CircleShape)
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            // Back arrow
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 12.dp)) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack, "Back",
                        tint = textPrimary.copy(alpha = 0.8f),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Column(Modifier.padding(horizontal = 24.dp)) {
                Text("Create Account", color = textPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(4.dp))
                Text("Join AdisChat — it only takes a moment", color = textMuted, fontSize = 13.sp)
            }

            Spacer(Modifier.height(24.dp))

            // Form card
            Surface(
                shape           = RoundedCornerShape(20.dp),
                color           = cardBg,
                border          = BorderStroke(1.dp, cardBorder),
                modifier        = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                shadowElevation = if (isDark) 0.dp else 2.dp,
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

                    AuthField(
                        value         = name,
                        onValueChange = onNameChange,
                        placeholder   = "Full name",
                        icon          = Icons.Default.Person,
                        isDark        = isDark,
                        accent        = accent,
                        imeAction     = ImeAction.Next,
                    )

                    AuthPhoneField(
                        value         = phone,
                        onValueChange = onPhoneChange,
                        isDark        = isDark,
                        accent        = accent,
                        isError       = phoneError != null,
                        errorText     = phoneError,
                    )

                    AuthField(
                        value          = email,
                        onValueChange  = onEmailChange,
                        placeholder    = "Email address",
                        icon           = Icons.Default.Email,
                        isDark         = isDark,
                        accent         = accent,
                        keyboardType   = KeyboardType.Email,
                        imeAction      = ImeAction.Next,
                        isError        = emailError != null,
                        supportingText = emailError,
                    )

                    // Password + strength
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AuthPasswordField(
                            value         = password,
                            onValueChange = onPasswordChange,
                            visible       = passwordVisible,
                            onToggle      = { passwordVisible = !passwordVisible },
                            placeholder   = "Password (min. 8 characters)",
                            imeAction     = ImeAction.Next,
                            isDark        = isDark,
                            accent        = accent,
                            onDone        = {},
                        )
                        if (password.isNotEmpty()) {
                            // Strength bar
                            val met   = (5 - passwordRequirements(password).size).coerceIn(0, 5)
                            val ratio = met / 5f
                            val barColor = when (met) {
                                0, 1 -> Color(0xFFEF4444)
                                2    -> Color(0xFFF97316)
                                3    -> Color(0xFFEAB308)
                                4    -> Color(0xFF84CC16)
                                else -> Color(0xFF22C55E)
                            }
                            val strengthLabel = when (met) {
                                0, 1 -> "Very Weak"; 2 -> "Weak"; 3 -> "Fair"; 4 -> "Good"; else -> "Strong"
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp))
                                        .background(if (isDark) Color(0xFF30363D) else Color(0xFFE5E7EB))
                                ) {
                                    Box(
                                        Modifier.fillMaxWidth(ratio).fillMaxHeight()
                                            .clip(RoundedCornerShape(3.dp)).background(barColor)
                                    )
                                }
                                Text(strengthLabel, fontSize = 11.sp, color = barColor, fontWeight = FontWeight.SemiBold)
                            }
                            // Requirements
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isDark) Color(0xFF0D1117) else Color(0xFFF9FAFB),
                                border = BorderStroke(1.dp, cardBorder),
                            ) {
                                Column(
                                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    listOf(
                                        "At least 8 characters"       to (password.length >= 8),
                                        "Uppercase letter (A–Z)"      to password.any { it.isUpperCase() },
                                        "Lowercase letter (a–z)"      to password.any { it.isLowerCase() },
                                        "Number (0–9)"                to password.any { it.isDigit() },
                                        "Special character (!@#\$…)" to password.any { !it.isLetterOrDigit() },
                                    ).forEach { (label, metReq) ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                if (metReq) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                                null,
                                                tint     = if (metReq) Color(0xFF22C55E) else textMuted.copy(alpha = 0.5f),
                                                modifier = Modifier.size(13.dp),
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                label,
                                                fontSize   = 12.sp,
                                                color      = if (metReq) Color(0xFF22C55E) else textMuted,
                                                fontWeight = if (metReq) FontWeight.Medium else FontWeight.Normal,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Confirm password
                    val confirmFocused = remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value         = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        modifier      = Modifier.fillMaxWidth().onFocusChanged { confirmFocused.value = it.isFocused },
                        shape         = RoundedCornerShape(14.dp),
                        singleLine    = true,
                        isError       = confirmMismatch,
                        placeholder   = {
                            Text(
                                "Confirm password",
                                color    = if (isDark) Color(0xFF6E7681) else Color(0xFF9CA3AF),
                                fontSize = 14.sp,
                            )
                        },
                        leadingIcon   = {
                            Icon(
                                Icons.Default.Lock, null,
                                tint = when {
                                    confirmMismatch      -> Color(0xFFEF4444)
                                    confirmOk            -> Color(0xFF22C55E)
                                    confirmFocused.value -> accent
                                    else -> if (isDark) Color(0xFF8B949E) else Color(0xFF9CA3AF)
                                },
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        trailingIcon  = {
                            when {
                                confirmOk       -> Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF22C55E))
                                confirmMismatch -> Icon(Icons.Default.Cancel, null, tint = Color(0xFFEF4444))
                                else            -> IconButton(onClick = { confirmVisible = !confirmVisible }) {
                                    Icon(
                                        if (confirmVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        null,
                                        tint = if (isDark) Color(0xFF8B949E) else Color(0xFF9CA3AF),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        },
                        visualTransformation = if (confirmVisible || confirmOk) VisualTransformation.None else PasswordVisualTransformation(),
                        supportingText = if (confirmMismatch) ({
                            Text("Passwords don't match", color = Color(0xFFEF4444), fontSize = 11.sp)
                        }) else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        colors = authFieldColors(
                            isDark  = isDark,
                            accent  = if (confirmOk) Color(0xFF22C55E) else accent,
                            isError = confirmMismatch,
                        ).copy(
                            unfocusedIndicatorColor = when {
                                confirmMismatch -> Color(0xFFEF4444).copy(alpha = 0.7f)
                                confirmOk       -> Color(0xFF22C55E).copy(alpha = 0.6f)
                                else            -> if (isDark) Color(0xFF30363D) else Color(0xFFE5E7EB)
                            },
                        ),
                    )
                }
            }

            // Error chip
            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier
                        .fillMaxWidth().padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFEF4444).copy(alpha = 0.10f))
                        .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.30f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Default.Warning, null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                    Text(error, color = Color(0xFFEF4444), fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Terms & Privacy agreement ─────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clickable { agreedTerms = !agreedTerms },
                verticalAlignment = Alignment.Top,
            ) {
                Checkbox(
                    checked = agreedTerms,
                    onCheckedChange = { agreedTerms = it },
                    colors = CheckboxDefaults.colors(checkedColor = accent, uncheckedColor = textMuted),
                    modifier = Modifier.size(36.dp),
                )
                Spacer(Modifier.width(6.dp))
                val agreeText = buildAnnotatedString {
                    append("I agree to the ")
                    pushStringAnnotation("url", "https://www.adischat.com/terms")
                    withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append("Terms of Service") }
                    pop()
                    append(" and ")
                    pushStringAnnotation("url", "https://www.adischat.com/privacy")
                    withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append("Privacy Policy") }
                    pop()
                }
                ClickableText(
                    text = agreeText,
                    style = androidx.compose.ui.text.TextStyle(color = textMuted, fontSize = 13.sp, lineHeight = 18.sp),
                    modifier = Modifier.padding(top = 9.dp),
                    onClick = { offset ->
                        agreeText.getStringAnnotations("url", offset, offset).firstOrNull()
                            ?.let { uriHandler.openUri(it.item) }
                            ?: run { agreedTerms = !agreedTerms }
                    },
                )
            }

            Spacer(Modifier.height(16.dp))

            // Create Account button
            Box(
                modifier = Modifier
                    .fillMaxWidth().padding(horizontal = 20.dp).height(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (canSubmit)
                            Brush.horizontalGradient(listOf(accentAlt, accent))
                        else
                            Brush.horizontalGradient(listOf(
                                accentAlt.copy(alpha = if (isDark) 0.25f else 0.35f),
                                accent.copy(alpha = if (isDark) 0.25f else 0.35f),
                            ))
                    )
                    .clickable(enabled = canSubmit && !loading) { onSubmit() },
                contentAlignment = Alignment.Center,
            ) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text(
                        "Create Account",
                        color         = if (canSubmit) Color.White else Color.White.copy(alpha = 0.4f),
                        fontWeight    = FontWeight.ExtraBold,
                        fontSize      = 16.sp,
                        letterSpacing = 0.5.sp,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text("Already have an account? ", color = textMuted, fontSize = 14.sp)
                Text(
                    "Sign In",
                    color      = accent,
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier   = Modifier.clickable { onLoginClick() },
                )
            }
            Spacer(Modifier.height(36.dp))
        }
    }
}

// ── Email OTP ─────────────────────────────────────────────────────────────────

@Composable
private fun EmailOtpPage(
    email: String, devOtp: String?,
    loading: Boolean, error: String?,
    onBack: () -> Unit, onVerify: (String) -> Unit,
    onResend: () -> Unit, onLoginClick: () -> Unit,
) {
    var otp by remember { mutableStateOf("") }
    val focusReq = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusReq.requestFocus() }
    LaunchedEffect(otp) { if (otp.length == 6 && !loading) onVerify(otp) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
                    tint = MaterialTheme.colorScheme.onBackground)
            }
        }
        Spacer(Modifier.height(20.dp))

        Image(
            painter            = painterResource(R.drawable.logo),
            contentDescription = "AdisChat",
            contentScale       = ContentScale.Fit,
            modifier           = Modifier.size(80.dp).clip(RoundedCornerShape(22.dp)),
        )
        Spacer(Modifier.height(20.dp))

        Text(
            "Verify Your Email",
            style      = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color      = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "We sent a 6-digit code to",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            email,
            style      = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color      = Color(0xFF16A34A),
        )

        if (devOtp != null) {
            Spacer(Modifier.height(10.dp))
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                Text(
                    "🛠 Dev code: $devOtp",
                    modifier   = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }

        Spacer(Modifier.height(36.dp))

        // OTP boxes
        Surface(
            modifier       = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape          = RoundedCornerShape(20.dp),
            color          = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                // 6 boxes — weight(1f) so they share width equally and never overflow
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    repeat(6) { i ->
                        val char     = otp.getOrNull(i)?.toString() ?: ""
                        val filled   = char.isNotEmpty()
                        val isActive = i == otp.length
                        val hasError = error != null && otp.length >= 5

                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    width = if (isActive) 2.dp else 1.5.dp,
                                    color = when {
                                        hasError -> MaterialTheme.colorScheme.error
                                        isActive -> Color(0xFF16A34A)
                                        filled   -> Color(0xFF16A34A).copy(alpha = 0.6f)
                                        else     -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .background(
                                    when {
                                        hasError && filled -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                        filled -> Color(0xFF16A34A).copy(alpha = 0.1f)
                                        else   -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (loading && otp.length == 6) {
                                CircularProgressIndicator(
                                    modifier    = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color       = Color(0xFF16A34A),
                                )
                            } else {
                                Text(
                                    char,
                                    fontSize   = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color      = if (hasError && filled)
                                        MaterialTheme.colorScheme.error
                                    else
                                        MaterialTheme.colorScheme.onBackground,
                                )
                            }
                        }
                    }
                }

                if (error != null) {
                    Spacer(Modifier.height(14.dp))
                    // "email already in use" → show a Sign In prompt instead of raw error
                    if (error.contains("already in use", ignoreCase = true) ||
                        error.contains("already exists", ignoreCase = true)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "This email is already registered.",
                                color    = MaterialTheme.colorScheme.error,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                            )
                            TextButton(onClick = onLoginClick) {
                                Text("Sign In instead", color = Color(0xFF16A34A),
                                    fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    } else {
                        Text(error, color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        }

        // Hidden input — BasicTextField has no border/outline so nothing leaks visually
        BasicTextField(
            value         = otp,
            onValueChange = { raw ->
                if (!loading) { val d = raw.filter { it.isDigit() }; if (d.length <= 6) otp = d }
            },
            modifier = Modifier
                .size(1.dp)
                .focusRequester(focusReq),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        )

        Spacer(Modifier.height(32.dp))
        TextButton(onClick = { otp = ""; onResend() }) {
            Text("Didn't receive a code? ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            Text("Resend", color = Color(0xFF16A34A), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

// ── Login ─────────────────────────────────────────────────────────────────────

@Composable
private fun LoginPage(
    loading: Boolean, error: String?,
    onBack: () -> Unit,
    onSubmit: (String, String) -> Unit,
    onPasswordReset: (String, (Boolean, String) -> Unit) -> Unit,
    onSignUpClick: () -> Unit,
) {
    var identifier      by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val canSubmit = !loading && identifier.isNotBlank() && password.isNotBlank()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        PageHeader(
            title    = "Welcome Back",
            subtitle = "Sign in to your AdisChat account",
            onBack   = onBack,
        )

        // Mini logo
        Column(
            Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter            = painterResource(R.drawable.logo),
                contentDescription = "AdisChat",
                contentScale       = ContentScale.Fit,
                modifier           = Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.ExtraBold)) { append("Adis") }
                    withStyle(SpanStyle(color = Color(0xFF16A34A),     fontWeight = FontWeight.ExtraBold)) { append("Chat") }
                },
                fontSize = 17.sp,
            )
        }

        // Form card
        Surface(
            modifier       = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape          = RoundedCornerShape(20.dp),
            color          = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                FormField(
                    label         = "Email, username or phone",
                    value         = identifier,
                    onValueChange = { identifier = it },
                    placeholder   = "you@example.com / abebe / 912345678",
                    leadingIcon   = Icons.Default.Person,
                    keyboardType  = KeyboardType.Email,
                    imeAction     = ImeAction.Next,
                )

                Column {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        FieldLabel("Password")
                        TextButton(
                            onClick      = { showResetDialog = true },
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text("Forgot password?", color = Color(0xFF16A34A), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    OutlinedTextField(
                        value         = password,
                        onValueChange = { password = it },
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(12.dp),
                        singleLine    = true,
                        placeholder   = { Text("Your password", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f)) },
                        leadingIcon   = { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        trailingIcon  = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            if (canSubmit) onSubmit(identifier.trim(), password)
                        }),
                        colors = formFieldColors(),
                    )
                }
            }
        }

        if (error != null) {
            Spacer(Modifier.height(14.dp))
            ErrorCard(error, Modifier.padding(horizontal = 20.dp))
        }

        Spacer(Modifier.height(20.dp))
        GradientButton(
            text     = "Sign In",
            onClick  = { onSubmit(identifier.trim(), password) },
            loading  = loading,
            enabled  = canSubmit,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text("Don't have an account? ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            Text(
                "Create one",
                color      = Color(0xFF16A34A),
                fontSize   = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier   = Modifier.clickable { onSignUpClick() },
            )
        }
        Spacer(Modifier.height(32.dp))
    }

    if (showResetDialog) {
        PasswordResetDialog(
            onDismiss = { showResetDialog = false },
            onSend    = onPasswordReset,
        )
    }
}

// ── Password Reset Dialog ─────────────────────────────────────────────────────

@Composable
private fun PasswordResetDialog(
    onDismiss: () -> Unit,
    onSend: (String, (Boolean, String) -> Unit) -> Unit,
) {
    var resetEmail by remember { mutableStateOf("") }
    var resetMsg   by remember { mutableStateOf<String?>(null) }
    var resetOk    by remember { mutableStateOf(false) }
    var loading    by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape  = RoundedCornerShape(20.dp),
        title  = { Text("Reset Password", fontWeight = FontWeight.Bold) },
        text   = {
            Column {
                Text(
                    "Enter your email and we'll send you a reset link.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value         = resetEmail,
                    onValueChange = { resetEmail = it; resetMsg = null },
                    label         = { Text("Email address") },
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(12.dp),
                    singleLine    = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    colors        = formFieldColors(),
                )
                if (resetMsg != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        resetMsg!!,
                        color    = if (resetOk) Color(0xFF22C55E) else MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    loading = true
                    onSend(resetEmail) { ok, msg ->
                        loading  = false
                        resetOk  = ok
                        resetMsg = msg
                        if (ok) resetEmail = ""
                    }
                },
                enabled = !loading && resetEmail.contains('@'),
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF16A34A))
                else Text("Send Link", color = Color(0xFF16A34A), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

// ── Password Strength ─────────────────────────────────────────────────────────

@Composable
private fun PasswordStrengthBar(password: String) {
    val met   = (5 - passwordRequirements(password).size).coerceIn(0, 5)
    val ratio = met / 5f

    val (barColor, label) = when (met) {
        0, 1 -> Color(0xFFEF4444) to "Very Weak"
        2    -> Color(0xFFF97316) to "Weak"
        3    -> Color(0xFFEAB308) to "Fair"
        4    -> Color(0xFF84CC16) to "Good"
        else -> Color(0xFF22C55E) to "Strong"
    }

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(ratio)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(barColor)
            )
        }
        Text(label, fontSize = 11.sp, color = barColor, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PasswordRequirements(password: String) {
    val requirements = listOf(
        "At least 8 characters"        to (password.length >= 8),
        "Uppercase letter (A–Z)"       to password.any { it.isUpperCase() },
        "Lowercase letter (a–z)"       to password.any { it.isLowerCase() },
        "Number (0–9)"                 to password.any { it.isDigit() },
        "Special character (!@#\$…)"  to password.any { !it.isLetterOrDigit() },
    )
    Surface(
        shape   = RoundedCornerShape(10.dp),
        color   = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            requirements.forEach { (label, met) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (met) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        null,
                        tint     = if (met) Color(0xFF22C55E) else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        label,
                        fontSize   = 12.sp,
                        color      = if (met) Color(0xFF22C55E) else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (met) FontWeight.Medium else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

// ── Shared UI components ──────────────────────────────────────────────────────

@Composable
private fun PageHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, start = 8.dp, end = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
                tint = MaterialTheme.colorScheme.onBackground)
        }
        Spacer(Modifier.width(4.dp))
        Column {
            Text(
                title,
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color      = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style      = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color      = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun FormField(
    label: String, value: String, onValueChange: (String) -> Unit,
    placeholder: String, leadingIcon: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    isError: Boolean = false, supportingText: String? = null,
) {
    Column {
        FieldLabel(label)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value           = value,
            onValueChange   = onValueChange,
            modifier        = Modifier.fillMaxWidth(),
            shape           = RoundedCornerShape(12.dp),
            singleLine      = true,
            placeholder     = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f)) },
            leadingIcon     = { Icon(leadingIcon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            isError         = isError,
            supportingText  = if (supportingText != null) ({ Text(supportingText) }) else null,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            colors          = formFieldColors(),
        )
    }
}

@Composable
private fun formFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor      = Color(0xFF16A34A),
    unfocusedBorderColor    = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
    focusedContainerColor   = Color(0xFF16A34A).copy(alpha = 0.04f),
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
    focusedLabelColor       = Color(0xFF16A34A),
    cursorColor             = Color(0xFF16A34A),
)

@Composable
private fun GradientButton(
    text: String, onClick: () -> Unit,
    loading: Boolean = false, enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val alpha by animateFloatAsState(if (enabled && !loading) 1f else 0.45f, label = "btn_alpha")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF16A34A), Color(0xFF22C55E), Color(0xFF4ADE80))
                )
            )
            .alpha(alpha)
            .clickable(enabled = enabled && !loading) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
        } else {
            Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun ErrorCard(message: String, modifier: Modifier = Modifier) {
    Surface(
        modifier       = modifier.fillMaxWidth(),
        shape          = RoundedCornerShape(12.dp),
        color          = MaterialTheme.colorScheme.errorContainer,
        tonalElevation = 0.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Warning, null,
                tint     = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                message,
                color    = MaterialTheme.colorScheme.onErrorContainer,
                fontSize = 13.sp,
            )
        }
    }
}

// ── Forgot-password dialog ────────────────────────────────────────────────────

@Composable
private fun ForgotPasswordDialog(
    onDismiss: () -> Unit,
    onSend: (String, (Boolean, String) -> Unit) -> Unit,
) {
    var resetEmail by remember { mutableStateOf("") }
    var loading    by remember { mutableStateOf(false) }
    var resetOk    by remember { mutableStateOf(false) }
    var resetMsg   by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon    = { Icon(Icons.Default.LockReset, null, tint = Color(0xFF16A34A)) },
        title   = { Text("Reset Password", fontWeight = FontWeight.Bold) },
        text    = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Enter the email address linked to your account and we'll send you a reset link.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value         = resetEmail,
                    onValueChange = { resetEmail = it; resetOk = false; resetMsg = "" },
                    label         = { Text("Email address") },
                    singleLine    = true,
                    shape         = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    colors        = formFieldColors(),
                    modifier      = Modifier.fillMaxWidth(),
                )
                if (resetMsg.isNotEmpty()) {
                    Text(
                        resetMsg,
                        color = if (resetOk) Color(0xFF22C55E) else MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    loading = true
                    onSend(resetEmail) { ok, msg ->
                        loading  = false
                        resetOk  = ok
                        resetMsg = msg
                        if (ok) resetEmail = ""
                    }
                },
                enabled = !loading && resetEmail.contains('@'),
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF16A34A))
                else Text("Send Link", color = Color(0xFF16A34A), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun passwordRequirements(password: String): List<String> = buildList {
    if (password.length < 8)                          add("At least 8 characters")
    if (!password.any { it.isUpperCase() })           add("Uppercase letter")
    if (!password.any { it.isLowerCase() })           add("Lowercase letter")
    if (!password.any { it.isDigit() })               add("Number")
    if (!password.any { !it.isLetterOrDigit() })      add("Special character")
}

private fun validatePhone(phone: String): String? {
    if (phone.length != 9)     return "Must be 9 digits (e.g. 912345678)"
    if (!phone.startsWith("9") && !phone.startsWith("7"))
        return "Must start with 9 or 7"
    return null
}
