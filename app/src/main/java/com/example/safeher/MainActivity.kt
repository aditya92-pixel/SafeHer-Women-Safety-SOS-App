package com.example.safeher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.safeher.services.SafetyService
import com.example.safeher.theme.SafeHerTheme
import com.example.safeher.utils.CameraHelper
import com.example.safeher.utils.LocationHelper
import com.example.safeher.utils.PreferencesHelper

// ── Design Tokens ─────────────────────────────────────────────────────────────
private val BG       = Color(0xFF0B0B12)
private val Surf1    = Color(0xFF13131D)
private val Surf2    = Color(0xFF1A1A27)
private val Border   = Color(0xFF23233A)
private val Primary  = Color(0xFFD63651)
private val TxtHi    = Color(0xFFEEEEF5)
private val TxtSub   = Color(0xFF8888A0)
private val TxtMuted = Color(0xFF4A4A65)
private val GreenOn  = Color(0xFF30D158)
// ─────────────────────────────────────────────────────────────────────────────

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val svc = Intent(this, SafetyService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc)
            else startService(svc)
        } catch (e: Exception) { e.printStackTrace() }

        enableEdgeToEdge()
        setContent {
            SafeHerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BG) {
                    MainAppScreen()
                }
            }
        }
    }
}

// ── App Shell ─────────────────────────────────────────────────────────────────

@Composable
fun MainAppScreen() {
    var tab by remember { mutableStateOf("home") }

    Box(Modifier.fillMaxSize().background(BG)) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn(tween(280)) togetherWith fadeOut(tween(280)) },
            label = "screen_transition"
        ) { t ->
            when (t) {
                "home"    -> HomeScreen()
                "stealth" -> CamouflageScreen(onExit = { tab = "home" })
                "network" -> NetworkScreen()
            }
        }

        if (tab != "stealth") {
            AppBottomBar(tab = tab, onTab = { tab = it },
                modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

// ── Bottom Navigation Bar ─────────────────────────────────────────────────────

@Composable
fun AppBottomBar(tab: String, onTab: (String) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Bar pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(Surf2),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavTab(Icons.Rounded.Shield,  "Shield",   tab == "home")    { onTab("home") }
            NavTab(Icons.Rounded.Calculate, "Stealth", tab == "stealth") { onTab("stealth") }
            NavTab(Icons.Rounded.Group,   "Guardians",tab == "network") { onTab("network") }
        }
    }
}

@Composable
private fun NavTab(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) Primary else TxtMuted
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(20.dp))
        Text(label, fontSize = 10.sp, color = color,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

// ── Home Screen ───────────────────────────────────────────────────────────────

@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    var contacts by remember { mutableStateOf(PreferencesHelper.getContacts(ctx)) }
    var shakeOn  by remember { mutableStateOf(PreferencesHelper.isShakeEnabled(ctx)) }
    var cameraOn by remember { mutableStateOf(PreferencesHelper.isCameraOnSosEnabled(ctx)) }

    val camLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraOn = granted
        PreferencesHelper.setCameraOnSosEnabled(ctx, granted)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BG)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 56.dp, bottom = 110.dp)
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("SafeHer", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = TxtHi)
            StatusPill()
        }

        Spacer(Modifier.height(52.dp))

        // ── SOS Hero ────────────────────────────────────────────────────────
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            SosHeroButton {
                val c = PreferencesHelper.getContacts(ctx)
                LocationHelper.sendSosWithLocation(ctx, c)
                CameraHelper.capturePhotosOnSos(ctx)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = when {
                    contacts.isEmpty() -> "Add guardians to enable alerts"
                    contacts.size == 1 -> "1 guardian will be notified"
                    else               -> "${contacts.size} guardians will be notified"
                },
                fontSize = 13.sp,
                color = if (contacts.isEmpty()) Primary.copy(alpha = 0.6f) else TxtSub
            )
        }

        var batteryLowOn by remember { mutableStateOf(true) }
        var showFakeCallDialog by remember { mutableStateOf(false) }

        Spacer(Modifier.height(32.dp))

        // ── Quick Action Tools ─────────────────────────────────────────────
        SectionLabel("QUICK SAFETY TOOLS")
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // "I Am Safe" Check-In
            QuickToolCard(
                icon = Icons.Rounded.CheckCircle,
                title = "I Am Safe",
                color = GreenOn,
                modifier = Modifier.weight(1f)
            ) {
                val c = PreferencesHelper.getContacts(ctx)
                LocationHelper.sendSosWithLocation(
                    ctx,
                    c,
                    "[SAFETY CHECK-IN] I have arrived safely at my destination!"
                )
            }

            // Fake Call Simulator
            QuickToolCard(
                icon = Icons.Rounded.PhoneInTalk,
                title = "Fake Call",
                color = Color(0xFF64D2FF),
                modifier = Modifier.weight(1f)
            ) {
                showFakeCallDialog = true
            }

            // Loud Siren Alarm
            QuickToolCard(
                icon = Icons.Rounded.VolumeUp,
                title = "Panic Siren",
                color = Color(0xFFFF9F0A),
                modifier = Modifier.weight(1f)
            ) {
                val vibrator = ctx.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), 0))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(longArrayOf(0, 400, 200, 400), 0)
                }
                android.widget.Toast.makeText(ctx, "PANIC SIREN ACTIVATED! Long press to stop", android.widget.Toast.LENGTH_LONG).show()
            }
        }

        Spacer(Modifier.height(28.dp))

        // ── Emergency Helplines ─────────────────────────────────────────────
        SectionLabel("EMERGENCY HELPLINES")
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Surf1)
        ) {
            HelplineRow("Women Helpline", "1091", Icons.Rounded.Shield) {
                ctx.startActivity(Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:1091")))
            }
            HorizontalDivider(color = Border, modifier = Modifier.padding(start = 64.dp))
            HelplineRow("Police / National Emergency", "112", Icons.Rounded.LocalPolice) {
                ctx.startActivity(Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:112")))
            }
            HorizontalDivider(color = Border, modifier = Modifier.padding(start = 64.dp))
            HelplineRow("Ambulance", "102", Icons.Rounded.MedicalServices) {
                ctx.startActivity(Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:102")))
            }
            HorizontalDivider(color = Border, modifier = Modifier.padding(start = 64.dp))
            HelplineRow("Domestic Abuse Line", "181", Icons.Rounded.CallEnd) {
                ctx.startActivity(Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:181")))
            }
        }

        Spacer(Modifier.height(28.dp))

        // ── Protection Settings ──────────────────────────────────────────────
        SectionLabel("PROTECTION TRIGGERS")
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Surf1)
        ) {
            SettingRow(
                icon      = Icons.Rounded.Vibration,
                title     = "Shake Alert",
                subtitle  = if (shakeOn) "Shake phone to trigger emergency" else "Disabled",
                checked   = shakeOn,
                onToggle  = { v -> shakeOn = v; PreferencesHelper.setShakeEnabled(ctx, v) }
            )
            HorizontalDivider(color = Border, modifier = Modifier.padding(start = 64.dp, end = 0.dp))
            SettingRow(
                icon      = Icons.Rounded.PowerSettingsNew,
                title     = "Power Button Triple-Press",
                subtitle  = "Press power button 3 times rapidly",
                checked   = true,
                onToggle  = {}
            )
            HorizontalDivider(color = Border, modifier = Modifier.padding(start = 64.dp, end = 0.dp))
            SettingRow(
                icon      = Icons.Rounded.BatteryAlert,
                title     = "Low Battery Broadcast",
                subtitle  = if (batteryLowOn) "Sends SOS location when battery drops < 15%" else "Disabled",
                checked   = batteryLowOn,
                onToggle  = { v -> batteryLowOn = v }
            )
            HorizontalDivider(color = Border, modifier = Modifier.padding(start = 64.dp, end = 0.dp))
            SettingRow(
                icon      = Icons.Rounded.CameraAlt,
                title     = "Evidence Camera",
                subtitle  = if (cameraOn) "Captures front & back photos on alert" else "Disabled",
                checked   = cameraOn,
                onToggle  = { v ->
                    if (v) {
                        val ok = ContextCompat.checkSelfPermission(
                            ctx, Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (ok) { cameraOn = true; PreferencesHelper.setCameraOnSosEnabled(ctx, true) }
                        else camLauncher.launch(Manifest.permission.CAMERA)
                    } else {
                        cameraOn = false; PreferencesHelper.setCameraOnSosEnabled(ctx, false)
                    }
                }
            )
        }

        if (showFakeCallDialog) {
            FakeCallDialog(onDismiss = { showFakeCallDialog = false })
        }
    }
}

@Composable
private fun StatusPill() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Surf2)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(GreenOn))
        Text("Active", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = GreenOn)
    }
}

@Composable
private fun SosHeroButton(onClick: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "sos_rings")
    val r1a by inf.animateFloat(0.25f, 0f,
        infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart), label = "r1a")
    val r1s by inf.animateFloat(1f, 1.7f,
        infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart), label = "r1s")
    val r2a by inf.animateFloat(0.15f, 0f,
        infiniteRepeatable(tween(2200, 800, easing = LinearEasing), RepeatMode.Restart), label = "r2a")
    val r2s by inf.animateFloat(1f, 1.7f,
        infiniteRepeatable(tween(2200, 800, easing = LinearEasing), RepeatMode.Restart), label = "r2s")

    Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
        // Pulsing rings
        Box(Modifier.size(148.dp).scale(r2s).alpha(r2a).clip(CircleShape).background(Primary))
        Box(Modifier.size(148.dp).scale(r1s).alpha(r1a).clip(CircleShape).background(Primary))

        // Button
        Box(
            modifier = Modifier
                .size(148.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFFE8384F), Color(0xFF9B1B30)),
                        radius = 220f
                    )
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Rounded.Warning,
                    contentDescription = "SOS",
                    tint = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "SOS",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 5.sp
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = TxtMuted,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Surf2),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = TxtSub, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TxtHi)
            Text(subtitle, fontSize = 12.sp, color = TxtMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor   = Color.White,
                checkedTrackColor   = Primary,
                uncheckedThumbColor = Surf2,
                uncheckedTrackColor = Border,
                uncheckedBorderColor = Border
            )
        )
    }
}

// ── Network (Contacts) Screen ─────────────────────────────────────────────────

@Composable
fun NetworkScreen() {
    val ctx = LocalContext.current
    var contacts  by remember { mutableStateOf(PreferencesHelper.getContacts(ctx)) }
    var newNumber by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BG)
            .padding(horizontal = 24.dp)
            .padding(top = 56.dp, bottom = 110.dp)
    ) {
        // Header
        Text("Guardians", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = TxtHi)
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (contacts.isEmpty()) "Add trusted contacts who will be alerted in emergencies."
                   else "${contacts.size} contact${if (contacts.size != 1) "s" else ""} ready",
            fontSize = 14.sp,
            color = TxtSub
        )

        Spacer(Modifier.height(28.dp))

        // Add contact row
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newNumber,
                onValueChange = { newNumber = it },
                placeholder = { Text("+91 98765 43210", color = TxtMuted, fontSize = 14.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor     = Primary,
                    unfocusedBorderColor   = Border,
                    focusedTextColor       = TxtHi,
                    unfocusedTextColor     = TxtHi,
                    cursorColor            = Primary,
                    focusedContainerColor  = Surf1,
                    unfocusedContainerColor = Surf1
                )
            )
            val canAdd = newNumber.isNotBlank()
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (canAdd) Primary else Surf2)
                    .clickable(enabled = canAdd) {
                        val updated = contacts + newNumber.trim()
                        PreferencesHelper.saveContacts(ctx, updated)
                        contacts = updated
                        newNumber = ""
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Add, null,
                    tint = if (canAdd) Color.White else TxtMuted,
                    modifier = Modifier.size(22.dp))
            }
        }

        Spacer(Modifier.height(28.dp))

        if (contacts.isEmpty()) {
            // Empty state
            Column(
                Modifier.fillMaxWidth().padding(vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(Surf1),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Group, null, tint = TxtMuted, modifier = Modifier.size(28.dp))
                }
                Text("No guardians yet", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TxtSub)
                Text(
                    "Add a phone number above to get started.",
                    fontSize = 13.sp, color = TxtMuted, textAlign = TextAlign.Center
                )
            }
        } else {
            SectionLabel("CONTACTS")
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                items(contacts.size) { i ->
                    val contact = contacts[i]
                    val isFirst = i == 0
                    val isLast  = i == contacts.lastIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(
                                RoundedCornerShape(
                                    topStart    = if (isFirst) 16.dp else 4.dp,
                                    topEnd      = if (isFirst) 16.dp else 4.dp,
                                    bottomStart = if (isLast) 16.dp else 4.dp,
                                    bottomEnd   = if (isLast) 16.dp else 4.dp
                                )
                            )
                            .background(Surf1)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(Surf2),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Person, null, tint = TxtSub, modifier = Modifier.size(18.dp))
                        }
                        Text(
                            contact,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = TxtHi,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Primary.copy(alpha = 0.12f))
                                .clickable {
                                    val updated = contacts.filterIndexed { idx, _ -> idx != i }
                                    PreferencesHelper.saveContacts(ctx, updated)
                                    contacts = updated
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Close, null, tint = Primary, modifier = Modifier.size(15.dp))
                        }
                    }
                    if (!isLast) HorizontalDivider(color = Border, modifier = Modifier.padding(start = 66.dp))
                }
            }
        }
    }
}

// ── Stealth Calculator ─────────────────────────────────────────────────────────
// TAP = → calculate normally
// HOLD = → silent SOS (sends alert + captures photos)
// TAP AC three times quickly → exit to real app

@Composable
fun CamouflageScreen(onExit: () -> Unit) {
    val context = LocalContext.current

    var display      by remember { mutableStateOf("0") }
    var firstOperand by remember { mutableStateOf(0.0) }
    var pendingOp    by remember { mutableStateOf("") }
    var clearOnNext  by remember { mutableStateOf(false) }
    var currentInput by remember { mutableStateOf("") }
    var acTaps       by remember { mutableStateOf(0) }
    var lastAcTime   by remember { mutableStateOf(0L) }

    fun fmt(v: Double): String {
        if (v.isNaN() || v.isInfinite()) return "Error"
        val l = v.toLong()
        return if (v == l.toDouble()) l.toString() else "%.8g".format(v)
    }
    fun onDigit(d: String) {
        if (clearOnNext) { display = d; currentInput = d; clearOnNext = false }
        else { if (display == "0") { display = d; currentInput = d } else { display += d; currentInput += d } }
    }
    fun onOperator(op: String) { firstOperand = display.toDoubleOrNull() ?: 0.0; pendingOp = op; clearOnNext = true; currentInput = "" }
    fun onEquals() {
        if (pendingOp.isEmpty()) return
        val s = display.toDoubleOrNull() ?: 0.0
        val r = when (pendingOp) {
            "+" -> firstOperand + s; "-" -> firstOperand - s
            "×" -> firstOperand * s; "÷" -> if (s != 0.0) firstOperand / s else Double.NaN
            else -> s
        }
        display = fmt(r); pendingOp = ""; clearOnNext = true; currentInput = ""
    }
    fun onAC() {
        val now = System.currentTimeMillis()
        acTaps = if (now - lastAcTime < 2000) acTaps + 1 else 1; lastAcTime = now
        if (acTaps >= 3) { onExit(); return }
        display = "0"; firstOperand = 0.0; pendingOp = ""; clearOnNext = false; currentInput = ""
    }
    fun onPlusMinus() { val v = display.toDoubleOrNull() ?: return; display = fmt(-v) }
    fun onPercent()   { val v = display.toDoubleOrNull() ?: return; display = fmt(v / 100.0); currentInput = "" }
    fun onDot()       { if (clearOnNext) { display = "0."; clearOnNext = false; return }; if (!display.contains(".")) display += "." }

    val orange    = Color(0xFFFF9F0A)
    val darkGray  = Color(0xFF1C1C1C)
    val lightGray = Color(0xFF505050)
    val gap       = 10.dp
    val bh        = 78.dp

    Column(
        Modifier.fillMaxSize().background(Color(0xFF000000)).padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.Bottom
    ) {
        Text(
            display,
            fontSize     = if (display.length > 9) 42.sp else 68.sp,
            color        = Color.White,
            fontWeight   = FontWeight.Light,
            maxLines     = 1,
            textAlign    = TextAlign.End,
            modifier     = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 18.dp)
        )
        // Row 1: functions
        Row(Modifier.fillMaxWidth().padding(bottom = gap), horizontalArrangement = Arrangement.spacedBy(gap)) {
            CalcBtn("AC",  lightGray, Color.White, Modifier.weight(1f), bh) { onAC() }
            CalcBtn("+/-", lightGray, Color.White, Modifier.weight(1f), bh) { onPlusMinus() }
            CalcBtn("%",   lightGray, Color.White, Modifier.weight(1f), bh) { onPercent() }
            CalcBtn("÷",   orange,    Color.White, Modifier.weight(1f), bh) { onOperator("÷") }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = gap), horizontalArrangement = Arrangement.spacedBy(gap)) {
            CalcBtn("7", darkGray, Color.White, Modifier.weight(1f), bh) { onDigit("7") }
            CalcBtn("8", darkGray, Color.White, Modifier.weight(1f), bh) { onDigit("8") }
            CalcBtn("9", darkGray, Color.White, Modifier.weight(1f), bh) { onDigit("9") }
            CalcBtn("×", orange,   Color.White, Modifier.weight(1f), bh) { onOperator("×") }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = gap), horizontalArrangement = Arrangement.spacedBy(gap)) {
            CalcBtn("4", darkGray, Color.White, Modifier.weight(1f), bh) { onDigit("4") }
            CalcBtn("5", darkGray, Color.White, Modifier.weight(1f), bh) { onDigit("5") }
            CalcBtn("6", darkGray, Color.White, Modifier.weight(1f), bh) { onDigit("6") }
            CalcBtn("-", orange,   Color.White, Modifier.weight(1f), bh) { onOperator("-") }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = gap), horizontalArrangement = Arrangement.spacedBy(gap)) {
            CalcBtn("1", darkGray, Color.White, Modifier.weight(1f), bh) { onDigit("1") }
            CalcBtn("2", darkGray, Color.White, Modifier.weight(1f), bh) { onDigit("2") }
            CalcBtn("3", darkGray, Color.White, Modifier.weight(1f), bh) { onDigit("3") }
            CalcBtn("+", orange,   Color.White, Modifier.weight(1f), bh) { onOperator("+") }
        }
        // Row 5: wide 0, dot, equals (long-press = SOS)
        Row(Modifier.fillMaxWidth().padding(bottom = 28.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
            Box(
                modifier = Modifier.weight(2f).height(bh)
                    .clip(RoundedCornerShape(50)).background(darkGray)
                    .clickable { onDigit("0") },
                contentAlignment = Alignment.CenterStart
            ) { Text("0", fontSize = 30.sp, color = Color.White, modifier = Modifier.padding(start = 26.dp)) }

            CalcBtn(".", orange, Color.White, Modifier.weight(1f), bh) { onDot() }

            Box(
                modifier = Modifier.weight(1f).height(bh)
                    .clip(CircleShape).background(orange)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onEquals() },
                            onLongPress = {
                                val c = PreferencesHelper.getContacts(context)
                                LocationHelper.sendSosWithLocation(context, c, "[STEALTH SOS] Emergency triggered silently!")
                                CameraHelper.capturePhotosOnSos(context)
                                display = "Error"; currentInput = ""
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) { Text("=", fontSize = 30.sp, color = Color.White, fontWeight = FontWeight.Normal) }
        }
    }
}

@Composable
private fun CalcBtn(label: String, bg: Color, fg: Color, modifier: Modifier, height: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Box(
        modifier = modifier.height(height).clip(CircleShape).background(bg).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = 28.sp, color = fg, fontWeight = FontWeight.Normal) }
}

@Composable
private fun QuickToolCard(
    icon: ImageVector,
    title: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Surf1)
            .clickable { onClick() }
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = title, tint = color, modifier = Modifier.size(20.dp))
        }
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TxtHi, textAlign = TextAlign.Center)
    }
}

@Composable
private fun HelplineRow(
    title: String,
    number: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Surf2),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TxtHi)
            Text("Dial $number", fontSize = 12.sp, color = TxtMuted)
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Primary.copy(alpha = 0.15f))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text("Call", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Primary)
        }
    }
}

@Composable
private fun FakeCallDialog(onDismiss: () -> Unit) {
    var countdown by remember { mutableStateOf(5) }
    var inCall by remember { mutableStateOf(false) }

    LaunchedEffect(countdown, inCall) {
        if (!inCall && countdown > 0) {
            kotlinx.coroutines.delay(1000)
            countdown--
            if (countdown == 0) inCall = true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surf1,
        titleContentColor = TxtHi,
        textContentColor = TxtSub,
        title = {
            Text(if (inCall) "Incoming Call from Mom" else "Scheduling Fake Call...")
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (!inCall) {
                    Text("Ring in $countdown seconds", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Primary)
                    Spacer(Modifier.height(8.dp))
                    Text("Hold your phone to your ear when it rings to exit discreetly.", fontSize = 13.sp, color = TxtMuted, textAlign = TextAlign.Center)
                } else {
                    Text("Calling +91 98765 43210...", fontSize = 16.sp, color = GreenOn)
                    Spacer(Modifier.height(16.dp))
                    Text("Pretend to speak on the phone to leave unsafe situations.", fontSize = 13.sp, color = TxtSub, textAlign = TextAlign.Center)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (inCall) "End Call" else "Cancel", color = Primary)
            }
        }
    )
}
