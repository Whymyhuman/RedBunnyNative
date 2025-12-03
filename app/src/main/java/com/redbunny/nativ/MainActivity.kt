package com.redbunny.nativ

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redbunny.nativ.data.ConfigGenerator
import com.redbunny.nativ.data.ProxyScraper
import com.redbunny.nativ.model.ProxyItem
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFDC143C), // Crimson
                    onPrimary = Color.White,
                    background = Color.Black,
                    surface = Color(0xFF110808)
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
    val clipboardManager = LocalClipboardManager.current
    
    var proxies by remember { mutableStateOf<List<ProxyItem>>(emptyList()) }
    var selectedProxies by remember { mutableStateOf<Set<ProxyItem>>(emptySet()) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf("") }

    // Settings State
    var frontDomain by remember { mutableStateOf("df.game.naver.com") }
    var sni by remember { mutableStateOf("df.game.naver.com.redbunny.dpdns.org") }

    // Result Dialog
    var showResultDialog by remember { mutableStateOf(false) }
    var generatedConfig by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RedBunny Native", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(it)
                .padding(16.dp)
        ) {
            // Settings Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF110808)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Settings", color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = frontDomain,
                        onValueChange = { frontDomain = it },
                        label = { Text("Front Domain") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = sni,
                        onValueChange = { sni = it },
                        label = { Text("SNI") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            statusMsg = "Scraping proxies..."
                            proxies = ProxyScraper.scrapeAll()
                            statusMsg = "Found ${proxies.size} proxies"
                            isLoading = false
                        }
                    },
                    enabled = !isLoading
                ) {
                    Text(if (isLoading) "Loading..." else "Load Proxies")
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC143C))
                ) {
                    Text("Generate (${selectedProxies.size})")
                }
            }

            Text(text = statusMsg, color = Color.White, modifier = Modifier.padding(vertical = 8.dp))

            // Proxy List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(proxies) { proxy ->
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

    if (showResultDialog) {
        AlertDialog(
            onDismissRequest = { showResultDialog = false },
            title = { Text("Generated Configs") },
            text = {
                OutlinedTextField(
                    value = generatedConfig,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().height(300.dp)
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
            }
        )
    }
}

@Composable
fun ProxyItemCard(proxy: ProxyItem, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFDC143C) else Color(0xFF1A0A0A)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(text = proxy.country, fontWeight = FontWeight.Bold, color = Color.LightGray)
                Text(text = "${proxy.ip}:${proxy.port}", color = Color.White)
            }
            Text(text = proxy.provider, fontSize = 12.sp, color = Color.Gray)
        }
    }
}
