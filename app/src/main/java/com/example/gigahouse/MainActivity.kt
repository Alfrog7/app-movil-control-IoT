package com.example.gigahouse

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private val esp32Ip = "http://192.168.4.1"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GigaHouseApp()
        }
    }

    private fun enviarComando(endpoint: String, onResult: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("$esp32Ip$endpoint")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connect()
                val response = connection.inputStream.bufferedReader().readText()
                connection.disconnect()

                onResult(response == "ON")
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
                onResult(false)
            }
        }
    }

    private fun verificarConexionESP32(onResult: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("$esp32Ip/estado")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connect()
                val responseCode = connection.responseCode
                connection.disconnect()
                onResult(responseCode == 200)
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }

    @Composable
    fun GigaHouseApp() {
        var selectedTab by remember { mutableStateOf(0) }
        var conectadoESP32 by remember { mutableStateOf(false) }
        val context = LocalContext.current

        // Verificar la conexión al ESP32 al iniciar la aplicación
        LaunchedEffect(Unit) {
            verificarConexionESP32 { estado ->
                conectadoESP32 = estado
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("GigaHouse", fontSize = 22.sp, fontWeight = FontWeight.Bold) },
                    actions = {
                        Button(
                            onClick = {
                                if (conectadoESP32) {
                                    verificarConexionESP32 { estado -> conectadoESP32 = estado }
                                } else {
                                    abrirConfiguracionWifi(context)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (conectadoESP32) Color(0xFF4CAF50) else Color(0xFFF44336)
                            ),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Text(
                                text = if (conectadoESP32) "Conectado" else "Desconectado",
                                color = Color.White
                            )
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Control de Luces") },
                        label = { Text("Luces") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Control de Cochera") },
                        label = { Text("Cochera") }
                    )
                    // Nuevo ítem para abrir la configuración de Wi-Fi
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { abrirConfiguracionWifi(context) },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Wi-Fi") },
                        label = { Text("Wi-Fi") }
                    )
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                when (selectedTab) {
                    0 -> ControlScreen()
                    1 -> CocheraScreen()
                }
            }
        }
    }
    private fun abrirConfiguracionWifi(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Error al abrir la configuración de Wi-Fi", Toast.LENGTH_SHORT).show()
        }
    }


    @Composable
    fun ControlScreen() {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(scrollState)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "Control de Iluminación",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E88E5),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            areaControlCard("Cuartos", listOf("cuarto1", "cuarto2", "cuarto3"))
            areaControlCard("Sala", listOf("sala1", "sala2"))
            areaControlCard("Baños", listOf("bano1", "bano2"))
            areaControlCard("Pasillo", listOf("pasillo1", "pasillo2"))
            areaControlCard("Cocina", listOf("cocina"))
        }
    }

    @Composable
    fun CocheraScreen() {
        val scrollState = rememberScrollState()
        var cocheraAbierta by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            enviarComando("/cochera/estado") { estado ->
                cocheraAbierta = estado
            }
        }

        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(scrollState)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "Control de Cochera",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E88E5),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Button(
                onClick = {
                    enviarComando("/cochera/toggle") { estado ->
                        cocheraAbierta = estado
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (cocheraAbierta) Color(0xFFF44336) else Color(0xFF4CAF50)
                ),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text(if (cocheraAbierta) "Cerrar Cochera" else "Abrir Cochera", color = Color.White)
            }

            Spacer(modifier = Modifier.height(20.dp))
            areaControlCard("Luces de Cochera", listOf("cochera1", "cochera2", "cochera3", "cochera4"))
        }
    }

    @Composable
    fun areaControlCard(areaName: String, endpoints: List<String>) {
        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEEEEEE))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(text = areaName, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                endpoints.forEach { endpoint ->
                    var estado by remember { mutableStateOf(false) }

                    LaunchedEffect(Unit) {
                        enviarComando("/$endpoint/estado") { nuevoEstado -> estado = nuevoEstado }
                    }

                    Button(
                        onClick = { enviarComando("/$endpoint/toggle") { nuevoEstado -> estado = nuevoEstado } },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (estado) Color(0xFF4CAF50) else Color(0xFFF44336)
                        ),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Text(if (estado) "Encendido" else "Apagado", color = Color.White)
                    }
                }
            }
        }
    }
}
