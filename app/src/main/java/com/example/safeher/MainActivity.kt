package com.example.safeher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.safeher.services.JourneyService
import com.example.safeher.theme.SafeHerTheme
import com.example.safeher.utils.LocationHelper
import com.example.safeher.utils.PreferencesHelper
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize the free map configuration and set User-Agent to avoid 403 Blocked errors
        Configuration.getInstance().userAgentValue = "com.example.safeher"
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))

        enableEdgeToEdge()
        setContent {
            SafeHerTheme { 
                Surface(
                    modifier = Modifier.fillMaxSize(), 
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppScreen() 
                } 
            }
        }
    }
}

@Composable
fun MainAppScreen() {
    var currentTab by remember { mutableStateOf("SOS") }

    Box(modifier = Modifier.fillMaxSize()) {
        
        // Background content based on tab
        AnimatedContent(
            targetState = currentTab,
            transitionSpec = {
                fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
            }, label = "tab_animation"
        ) { targetTab ->
            when (targetTab) {
                "SOS" -> SosScreen()
                "Journey" -> JourneyScreen()
                "Contacts" -> ContactsScreen()
            }
        }

        // Premium Detached Floating Bottom Navigation
        FloatingBottomNavBar(
            currentTab = currentTab,
            onTabSelected = { currentTab = it },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun FloatingBottomNavBar(
    currentTab: String,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var contacts by remember { mutableStateOf(PreferencesHelper.getContacts(context)) }

    Box(
        modifier = modifier
            .padding(start = 24.dp, end = 24.dp, bottom = 40.dp) // Detached floating
            .fillMaxWidth()
            .height(80.dp),
        contentAlignment = Alignment.Center
    ) {
        // The Bar itself
        Card(
            shape = RoundedCornerShape(40.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .align(Alignment.BottomCenter)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Journey Tab
                NavBarItem(
                    title = "Journey",
                    icon = "MAP",
                    isSelected = currentTab == "Journey",
                    onClick = { onTabSelected("Journey") }
                )
                
                // Spacer for center SOS button
                Spacer(modifier = Modifier.width(80.dp))
                
                // Contacts Tab
                NavBarItem(
                    title = "Network",
                    icon = "TEAM",
                    isSelected = currentTab == "Contacts",
                    onClick = { onTabSelected("Contacts") }
                )
            }
        }

        // Floating Overlapping SOS Button in the Center
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ), label = "pulse_scale"
        )

        Box(
            modifier = Modifier
                .size(85.dp) // Extra Large
                .offset(y = (-20).dp) // Pop out of the top of the bar
                .scale(scale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFFFF5252), Color(0xFFD32F2F))
                    )
                )
                .clickable {
                    LocationHelper.sendSosWithLocation(context, contacts)
                    onTabSelected("SOS")
                },
            contentAlignment = Alignment.Center
        ) {
            Text("SOS", color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp)
        }
    }
}

@Composable
fun NavBarItem(title: String, icon: String, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = icon,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SosScreen() {
    val context = LocalContext.current
    var contacts by remember { mutableStateOf(PreferencesHelper.getContacts(context)) }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "SafeHer",
            fontSize = 48.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 2.sp
        )
        Text(
            text = "Your digital bodyguard.",
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 60.dp)
        )

        // Large status indicator
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth().height(200.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "STATUS",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Text(
                    text = "ACTIVE",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF388E3C),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Text(
                    text = "${contacts.size} Emergency Contacts Linked",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun JourneyScreen() {
    val context = LocalContext.current
    var isJourneyActive by remember { mutableStateOf(false) }
    var destination by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .padding(top = 40.dp, bottom = 120.dp), // Extra bottom padding for floating bar
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Route Tracker",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Destination Search Bar
        OutlinedTextField(
            value = destination,
            onValueChange = { destination = it },
            placeholder = { Text("Where are you going?", fontWeight = FontWeight.SemiBold) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(20.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        )

        Card(
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 16.dp)
        ) {
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        setMultiTouchControls(true)
                        controller.setZoom(16.0)
                        controller.setCenter(GeoPoint(40.7128, -74.0060)) 
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        Button(
            onClick = {
                val intent = Intent(context, JourneyService::class.java)
                if (isJourneyActive) {
                    context.stopService(intent)
                    isJourneyActive = false
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                    isJourneyActive = true
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(65.dp),
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isJourneyActive) Color(0xFF388E3C) else MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = if (isJourneyActive) "STOP JOURNEY" else "START JOURNEY",
                fontSize = 18.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
fun ContactsScreen() {
    val context = LocalContext.current
    var contacts by remember { mutableStateOf(PreferencesHelper.getContacts(context)) }
    var newContact by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .padding(top = 40.dp, bottom = 120.dp) // Bottom padding for floating bar
    ) {
        Text(
            text = "Emergency Network",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                OutlinedTextField(
                    value = newContact,
                    onValueChange = { newContact = it },
                    label = { Text("Phone Number") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (newContact.isNotBlank()) {
                            val updated = contacts + newContact
                            PreferencesHelper.saveContacts(context, updated)
                            contacts = updated
                            newContact = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Add Guardian", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(contacts) { contact ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = contact, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Button(
                            onClick = {
                                val updated = contacts.filter { it != contact }
                                PreferencesHelper.saveContacts(context, updated)
                                contacts = updated
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Remove", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
