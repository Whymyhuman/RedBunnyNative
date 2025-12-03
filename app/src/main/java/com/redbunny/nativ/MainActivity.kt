package com.redbunny.nativ

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redbunny.nativ.data.ConfigGenerator
import com.redbunny.nativ.data.ProxyChecker
import com.redbunny.nativ.data.ProxyRepository
import com.redbunny.nativ.data.ProxyScraper
import com.redbunny.nativ.data.ProxyChecker
import com.redbunny.nativ.model.ProxyItem
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFDC143C), // Crimson
                    onPrimary = Color.White,
                    background = Color.Black,
                    surface = Color(0xFF110808),
                    onSurface = Color.White,
                    secondaryContainer = Color(0xFF330000)
                )
            ) {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    
    var proxies by remember { mutableStateOf<List<ProxyItem>>(emptyList()) }
    var selectedProxies by remember { mutableStateOf<Set<ProxyItem>>(emptySet()) }
    var isLoading by remember { mutableStateOf(false) } // For scraping
    var isChecking by remember { mutableStateOf(false) } // For checking connectivity
    var statusMsg by remember { mutableStateOf("Ready") }

    // Settings State
    var frontDomain by remember { mutableStateOf("df.game.naver.com") }
    var sni by remember { mutableStateOf("df.game.naver.com.redbunny.dpdns.org") }

    // Dialogs State
    var showResultDialog by remember { mutableStateOf(false) }
    var generatedConfig by remember { mutableStateOf("") }
    var showSourceDialog by remember { mutableStateOf(false) }
    var showAddSourceDialog by remember { mutableStateOf(false) }
    var newSourceUrl by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RedBunny Native", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
                actions = {
                    IconButton(onClick = { showSourceDialog = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Manage Sources", tint = Color.White)
                    }
                }
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(it)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Settings Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF110808)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Settings Generator", color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = frontDomain,
                        onValueChange = { frontDomain = it },
                        label = { Text("Front Domain") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color.DarkGray,
                            focusedLabelColor = MaterialTheme.colorScheme.primary, unfocusedLabelColor = Color.Gray
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = sni,
                        onValueChange = { sni = it },
                        label = { Text("SNI") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color.DarkGray,
                            focusedLabelColor = MaterialTheme.colorScheme.primary, unfocusedLabelColor = Color.Gray
                        )
                    )
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            statusMsg = "Scraping proxies..."
                            val sources = ProxyRepository.getSources(context)
                            proxies = ProxyScraper.scrapeAll(sources)
                            statusMsg = "Found ${proxies.size} proxies"
                            isLoading = false
                        }
                    },
                    enabled = !isLoading && !isChecking,
                    modifier = Modifier.weight(1f).padding(end = 4.dp)
                ) {
                    Text(if (isLoading) "Scraping..." else "Scrape Proxies")
                }

                Button(
                    onClick = {
                        scope.launch {
                            isChecking = true
                            statusMsg = "Checking connectivity and GeoIP..."
                            // Make a mutable copy to update individual items
                            val checkedProxies = ProxyChecker.checkProxies(proxies.filter { !it.isChecking })
                            
                            // Merge results back into the original list (or just replace if simpler)
                            val updatedProxies = proxies.map { oldProxy ->
                                checkedProxies.find { it.ip == oldProxy.ip && it.port == oldProxy.port } ?: oldProxy
                            }
                            proxies = updatedProxies

                            // Filter for working proxies and update selected
                            val workingProxies = proxies.filter { it.isWorking && it.speedtestAccess }
                            selectedProxies = workingProxies.toSet()

                            statusMsg = "${workingProxies.size} working proxies found"
                            isChecking = false
                        }
                    },
                    enabled = proxies.isNotEmpty() && !isLoading && !isChecking,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                ) {
                    Text(if (isChecking) "Checking..." else "Check Proxies")
                }

                Button(
                    onClick = {
                        if (selectedProxies.isEmpty()) {
                            statusMsg = "Select proxies first!"
                            return@Button
                        }
                        val options = ConfigGenerator.ConfigOptions(frontDomain, sni)
                        val configs = selectedProxies.flatMap { proxy ->
                            listOf(
                                ConfigGenerator.generateVless(proxy, options),
                                ConfigGenerator.generateTrojan(proxy, options)
                            )
                        }.joinToString("\n\n")
                        
                        generatedConfig = configs
                        showResultDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                ) {
                    Text("Generate (${selectedProxies.size})")
                }
            }

            AnimatedVisibility(
                visible = isLoading || isChecking,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                 LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            }
           
            Text(text = statusMsg, color = Color.White, modifier = Modifier.padding(vertical = 8.dp))

            // Proxy List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(proxies, key = { "${it.ip}:${it.port}" }) { proxy ->
                    val isSelected = selectedProxies.contains(proxy)
                    ProxyItemCard(proxy, isSelected) {
                        selectedProxies = if (isSelected) {
                            selectedProxies - proxy
                        } else {
                            selectedProxies + proxy
                        }
                    }
                }
            }
        }
    }

    // Result Dialog
    if (showResultDialog) {
        AlertDialog(
            onDismissRequest = { showResultDialog = false },
            title = { Text("Generated Configs", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = generatedConfig,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color.DarkGray,
                        focusedLabelColor = MaterialTheme.colorScheme.primary, unfocusedLabelColor = Color.Gray
                    )
                )
            },
            confirmButton = {
                Button(onClick = {
                    clipboardManager.setText(AnnotatedString(generatedConfig))
                    statusMsg = "Copied to clipboard!"
                    showResultDialog = false
                }) {
                    Text("Copy & Close")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface
        )
    }

    // Source Management Dialog
    if (showSourceDialog) {
        val currentSources = remember { mutableStateListOf<String>().apply { addAll(ProxyRepository.getSources(context)) } }
        
        AlertDialog(
            onDismissRequest = { showSourceDialog = false },
            title = { Text("Manage Proxy Sources", color = Color.White) },
            text = {
                Column {
                    currentSources.forEach { source ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(source, color = Color.White, modifier = Modifier.weight(1f), overflow = TextOverflow.Ellipsis, maxLines = 1)
                            IconButton(onClick = { 
                                ProxyRepository.removeSource(context, source)
                                currentSources.remove(source)
                            }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove Source", tint = Color.Red)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { showAddSourceDialog = true }) {
                        Text("Add New Source")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { 
                        ProxyRepository.resetSources(context)
                        currentSources.clear()
                        currentSources.addAll(ProxyRepository.getSources(context)) // Reload defaults
                    }) {
                        Text("Reset to Default Sources")
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showSourceDialog = false }) {
                    Text("Close")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface
        )

        if (showAddSourceDialog) {
            AlertDialog(
                onDismissRequest = { showAddSourceDialog = false },
                title = { Text("Add New Proxy Source URL", color = Color.White) },
                text = {
                    OutlinedTextField(
                        value = newSourceUrl,
                        onValueChange = { newSourceUrl = it },
                        label = { Text("Raw GitHub URL") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color.DarkGray,
                            focusedLabelColor = MaterialTheme.colorScheme.primary, unfocusedLabelColor = Color.Gray
                        )
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        ProxyRepository.addSource(context, newSourceUrl)
                        newSourceUrl = ""
                        showAddSourceDialog = false
                        // Refresh current sources in parent dialog
                        currentSources.clear()
                        currentSources.addAll(ProxyRepository.getSources(context))
                    }) {
                        Text("Add Source")
                    }
                },
                dismissButton = {
                    Button(onClick = { showAddSourceDialog = false }) {
                        Text("Cancel")
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                textContentColor = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun ProxyItemCard(proxy: ProxyItem, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(text = "${proxy.country} (${proxy.provider})", fontWeight = FontWeight.Bold, color = Color.LightGray)
                Text(
                    text = "${proxy.ip}:${proxy.port}",
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Latency
                Icon(
                    Icons.Filled.Speed,
                    contentDescription = "Latency",
                    tint = Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (proxy.latency != -1L) "${proxy.latency} ms" else "N/A",
                    color = when {
                        proxy.latency < 200 -> Color.Green
                        proxy.latency < 800 -> Color.Yellow
                        proxy.latency == -1L -> Color.Gray
                        else -> Color.Red
                    },
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.width(8.dp))

                // Speedtest.net Access
                Icon(
                    if (proxy.speedtestAccess) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                    contentDescription = "Speedtest Access",
                    tint = if (proxy.speedtestAccess) Color.Green else Color.Red,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (proxy.speedtestAccess) "Speedtest OK" else "Speedtest Fail",
                    color = if (proxy.speedtestAccess) Color.Green else Color.Red,
                    fontSize = 12.sp
                )
            }
        }
    }
}