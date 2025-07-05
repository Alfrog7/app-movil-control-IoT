package com.example.gigahouse

import android.app.TimePickerDialog
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.*

// Plantilla para guardar cada evento del historial
data class HistorialItem(val evento: String, val timestamp: String, val id: String)

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private val database = FirebaseDatabase.getInstance()
    // Referencias a los nodos principales que usaremos
    private val estadoRef = database.getReference("estado_leds")
    private val programacionRef = database.getReference("programacion")
    private val historialRef = database.getReference("historial")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GigaHouseApp()
        }
    }

    // Mapeo de endpoints. Se mantiene igual.
    private fun mapearEndpointAFirebase(endpoint: String): String {
        return when (endpoint) {
            "cuarto1" -> "ledIzqArriba"
            "cuarto2" -> "ledIzqAbajo"
            "cuarto3" -> "ledMedioArriba"
            "sala1" -> "ledMedioAbajo"
            "sala2" -> "ledDerArriba"
            "bano1" -> "ledDerAbajo"
            else -> endpoint
        }
    }

    // Función para enviar comandos de encendido/apagado. Se mantiene igual.
    private fun enviarComandoFirebase(endpoint: String, onResult: (Boolean) -> Unit) {
        val firebaseField = mapearEndpointAFirebase(endpoint)
        estadoRef.child(firebaseField).get().addOnSuccessListener { snapshot ->
            val estadoActual = snapshot.getValue(Int::class.java) ?: 0
            val nuevoEstado = if (estadoActual == 1) 0 else 1
            estadoRef.child(firebaseField).setValue(nuevoEstado).addOnCompleteListener { task ->
                if (task.isSuccessful) onResult(nuevoEstado == 1)
                else {
                    onResult(false)
                    Toast.makeText(this@MainActivity, "Error Firebase: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // FUNCIÓN HÍBRIDA: Soporte para ambos tipos de programación
    private fun guardarProgramacion(
        ledId: String,
        tipoProgramacion: String,
        fechaInicio: String,
        fechaFin: String,
        horaEncendido: String,
        horaApagado: String,
        descripcion: String,
        activo: Boolean,
        context: Context
    ) {
        if (ledId.isEmpty()) {
            Toast.makeText(context, "Por favor, selecciona una luz", Toast.LENGTH_SHORT).show()
            return
        }

        if (fechaInicio.isEmpty()) {
            Toast.makeText(context, "Por favor, selecciona una fecha", Toast.LENGTH_SHORT).show()
            return
        }

        val programacionData = if (tipoProgramacion == "mismo_dia") {
            // Programación tipo "mismo_dia"
            mapOf(
                "tipo" to "mismo_dia",
                "fecha" to fechaInicio,
                "hora_encendido" to horaEncendido,
                "hora_apagado" to horaApagado,
                "descripcion" to descripcion,
                "activo" to activo
            )
        } else {
            // Programación tipo "extendido"
            if (fechaFin.isEmpty()) {
                Toast.makeText(context, "Por favor, selecciona fecha de fin", Toast.LENGTH_SHORT).show()
                return
            }
            mapOf(
                "tipo" to "extendido",
                "fecha_encendido" to fechaInicio,
                "fecha_apagado" to fechaFin,
                "hora_encendido" to horaEncendido,
                "hora_apagado" to horaApagado,
                "descripcion" to descripcion,
                "activo" to activo
            )
        }

        programacionRef.child(ledId).setValue(programacionData)
            .addOnSuccessListener {
                val tipoTexto = if (tipoProgramacion == "mismo_dia") "normal" else "extendida"
                Toast.makeText(context, "¡Programación $tipoTexto guardada!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(context, "Error al guardar: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    @Composable
    fun GigaHouseApp() {
        var selectedTab by remember { mutableStateOf(0) }
        var conectadoFirebase by remember { mutableStateOf(false) }
        var temperaturaActual by remember { mutableStateOf(0.0f) }
        var bocinaManual by remember { mutableStateOf(false) }
        val context = LocalContext.current

        // Listener para leer datos en tiempo real (temperatura, bocina, etc.)
        LaunchedEffect(Unit) {
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    conectadoFirebase = true
                    temperaturaActual = snapshot.child("temperaturaActual").getValue(Double::class.java)?.toFloat() ?: 0.0f
                    bocinaManual = (snapshot.child("bocinaManual").getValue(Int::class.java) ?: 0) == 1
                }
                override fun onCancelled(error: DatabaseError) {
                    conectadoFirebase = false
                }
            }
            estadoRef.addValueEventListener(listener)
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("🏠 Casa Inteligente", fontSize = 22.sp, fontWeight = FontWeight.Bold) },
                    actions = {
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 8.dp)) {
                            Text(
                                text = if (conectadoFirebase) "🔗 Conectado" else "❌ Desconectado",
                                color = if (conectadoFirebase) Color(0xFF4CAF50) else Color(0xFFF44336),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "🌡️ ${"%.1f".format(temperaturaActual)}°C",
                                fontSize = 12.sp,
                                color = if (temperaturaActual >= 29.0f) Color(0xFFF44336) else Color.Gray
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
                        icon = { Icon(Icons.Default.Home, contentDescription = "Control Manual") },
                        label = { Text("Control") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Schedule, contentDescription = "Programar Horarios") },
                        label = { Text("Programar") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.History, contentDescription = "Historial de Eventos") },
                        label = { Text("Historial") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error al abrir la configuración de Wi-Fi", Toast.LENGTH_SHORT).show()
                            }
                        },
                        icon = { Icon(Icons.Default.Wifi, contentDescription = "Wi-Fi") },
                        label = { Text("Wi-Fi") }
                    )
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                when (selectedTab) {
                    0 -> ControlScreen(temperaturaActual, bocinaManual)
                    1 -> ProgramarScreen()
                    2 -> HistorialScreen()
                }
            }
        }
    }

    @Composable
    fun ControlScreen(temperaturaActual: Float, bocinaManual: Boolean) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(scrollState)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "💡 Control Manual",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E88E5)
            )

            AreaControlCard("🏠 Lado Izquierdo 2do Piso", "cuarto1")
            AreaControlCard("🏠 Lado Izquierdo 1er Piso", "cuarto2")
            AreaControlCard("🏠 Lado Medio 2do Piso", "cuarto3")
            AreaControlCard("🏠 Lado Medio 1er Piso", "sala1")
            AreaControlCard("🏠 Lado Derecho 2do Piso", "sala2")
            AreaControlCard("🏠 Lado Derecho 1er Piso", "bano1")

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "🔊 Control de Bocina + Sensor",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium
                    )

                    val estadoBocina = when {
                        bocinaManual -> "MANUAL: ENCENDIDA"
                        temperaturaActual >= 29.0f -> "AUTOMÁTICO: ENCENDIDA (≥29°C)"
                        else -> "APAGADA"
                    }

                    Text(
                        text = "Estado: $estadoBocina",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (bocinaManual || temperaturaActual >= 29.0f) Color(0xFFF44336) else Color(0xFF4CAF50)
                    )

                    Button(
                        onClick = {
                            val nuevoEstado = if (bocinaManual) 0 else 1
                            estadoRef.child("bocinaManual").setValue(nuevoEstado)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (bocinaManual) Color(0xFFF44336) else Color(0xFF4CAF50)
                        ),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Text(
                            text = if (bocinaManual) "DESACTIVAR CONTROL MANUAL" else "ACTIVAR CONTROL MANUAL",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun AreaControlCard(areaName: String, endpoint: String) {
        var estado by remember { mutableStateOf(false) }
        val firebaseField = mapearEndpointAFirebase(endpoint)

        LaunchedEffect(firebaseField) {
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    estado = (snapshot.getValue(Int::class.java) ?: 0) == 1
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            estadoRef.child(firebaseField).addValueEventListener(listener)
        }

        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEEEEEE))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = areaName, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Switch(
                    checked = estado,
                    onCheckedChange = { enviarComandoFirebase(endpoint) { /* El listener se encarga */ } },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF4CAF50),
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color.Gray
                    )
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ProgramarScreen() {
        val luces = mapOf(
            "Luz Izquierda 2do Piso" to "ledIzqArriba",
            "Luz Izquierda 1er Piso" to "ledIzqAbajo",
            "Luz Medio 2do Piso" to "ledMedioArriba",
            "Luz Medio 1er Piso" to "ledMedioAbajo",
            "Luz Derecha 2do Piso" to "ledDerArriba",
            "Luz Derecha 1er Piso" to "ledDerAbajo"
        )

        var expanded by remember { mutableStateOf(false) }
        var ledSeleccionadoNombre by remember { mutableStateOf("Selecciona una luz") }
        var ledSeleccionadoId by remember { mutableStateOf("") }

        // NUEVA VARIABLE: Tipo de programación
        var tipoProgramacion by remember { mutableStateOf("mismo_dia") }

        // Variables para fechas
        var fechaInicio by remember { mutableStateOf("") }
        var fechaInicioMostrar by remember { mutableStateOf("Seleccionar fecha") }
        var fechaFin by remember { mutableStateOf("") }
        var fechaFinMostrar by remember { mutableStateOf("Seleccionar fecha fin") }

        var descripcion by remember { mutableStateOf("") }
        var horaEncendido by remember { mutableStateOf("08:00") }
        var horaApagado by remember { mutableStateOf("22:00") }
        var programacionActiva by remember { mutableStateOf(true) }

        val context = LocalContext.current
        val calendar = Calendar.getInstance()

        // Formatear fecha
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val dateFormatFirebase = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        // DatePickerDialog para fecha de inicio
        val datePickerInicio = DatePickerDialog(
            context,
            { _, year: Int, month: Int, dayOfMonth: Int ->
                calendar.set(year, month, dayOfMonth)
                fechaInicioMostrar = dateFormat.format(calendar.time)
                fechaInicio = dateFormatFirebase.format(calendar.time)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        // DatePickerDialog para fecha de fin
        val datePickerFin = DatePickerDialog(
            context,
            { _, year: Int, month: Int, dayOfMonth: Int ->
                calendar.set(year, month, dayOfMonth)
                fechaFinMostrar = dateFormat.format(calendar.time)
                fechaFin = dateFormatFirebase.format(calendar.time)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        val timePickerDialog = { currentTime: String, onTimeSelected: (String) -> Unit ->
            val parts = currentTime.split(":").map { it.toInt() }
            val dialog = TimePickerDialog(
                context,
                { _, hour: Int, minute: Int ->
                    onTimeSelected(String.format("%02d:%02d", hour, minute))
                }, parts[0], parts[1], true
            )
            dialog.show()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "🕒 Programar Horarios",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E88E5)
            )

            // Selector de luz
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                TextField(
                    value = ledSeleccionadoNombre,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Luz a Programar") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    luces.forEach { (nombre, id) ->
                        DropdownMenuItem(
                            text = { Text(nombre) },
                            onClick = {
                                ledSeleccionadoNombre = nombre
                                ledSeleccionadoId = id
                                expanded = false
                            }
                        )
                    }
                }
            }

            // NUEVO: Selector de tipo de programación
            Card(
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E8))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "📍 Tipo de Programación",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF388E3C)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .selectable(
                                    selected = tipoProgramacion == "mismo_dia",
                                    onClick = { tipoProgramacion = "mismo_dia" }
                                )
                                .weight(1f)
                        ) {
                            RadioButton(
                                selected = tipoProgramacion == "mismo_dia",
                                onClick = { tipoProgramacion = "mismo_dia" }
                            )
                            Text("📅 Normal", fontSize = 14.sp)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .selectable(
                                    selected = tipoProgramacion == "extendido",
                                    onClick = { tipoProgramacion = "extendido" }
                                )
                                .weight(1f)
                        ) {
                            RadioButton(
                                selected = tipoProgramacion == "extendido",
                                onClick = { tipoProgramacion = "extendido" }
                            )
                            Text("🗓️ Extendida", fontSize = 14.sp)
                        }
                    }
                }
            }

            // Selectores de fecha (dinámicos según el tipo)
            Card(
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = if (tipoProgramacion == "mismo_dia") "📅 Fecha" else "📅 Fechas",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF1976D2)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (tipoProgramacion == "mismo_dia") {
                        // Un solo selector de fecha
                        Button(
                            onClick = { datePickerInicio.show() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2196F3)
                            )
                        ) {
                            Text("📅 $fechaInicioMostrar", color = Color.White)
                        }
                    } else {
                        // Dos selectores de fecha
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { datePickerInicio.show() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4CAF50)
                                )
                            ) {
                                Text("📅 Inicio", color = Color.White, fontSize = 12.sp)
                            }
                            Button(
                                onClick = { datePickerFin.show() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF44336)
                                )
                            ) {
                                Text("📅 Fin", color = Color.White, fontSize = 12.sp)
                            }
                        }

                        // Mostrar fechas seleccionadas
                        if (fechaInicio.isNotEmpty() || fechaFin.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Inicio: $fechaInicioMostrar\nFin: $fechaFinMostrar",
                                fontSize = 12.sp,
                                color = Color(0xFF1976D2)
                            )
                        }
                    }
                }
            }

            // Campo de descripción
            TextField(
                value = descripcion,
                onValueChange = { descripcion = it },
                label = { Text("Descripción (opcional)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2
            )

            // Selectores de hora
            Card(
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "⏰ Horarios",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF7B1FA2)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = { timePickerDialog(horaEncendido) { horaEncendido = it } },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4CAF50)
                            )
                        ) {
                            Text("🔛 $horaEncendido", color = Color.White)
                        }
                        Button(
                            onClick = { timePickerDialog(horaApagado) { horaApagado = it } },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF44336)
                            )
                        ) {
                            Text("🔚 $horaApagado", color = Color.White)
                        }
                    }
                }
            }

            // Switch para activar programación
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("✅ Activar esta programación:", fontSize = 16.sp)
                Switch(
                    checked = programacionActiva,
                    onCheckedChange = { programacionActiva = it }
                )
            }

            // Resumen de programación
            if (ledSeleccionadoId.isNotEmpty() && fechaInicio.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "📋 Resumen",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF424242)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("💡 Luz: $ledSeleccionadoNombre", fontSize = 14.sp)
                        Text("🎯 Tipo: ${if (tipoProgramacion == "mismo_dia") "Normal" else "Extendida"}", fontSize = 14.sp)

                        if (tipoProgramacion == "mismo_dia") {
                            Text("📅 Fecha: $fechaInicioMostrar", fontSize = 14.sp)
                        } else {
                            Text("📅 Desde: $fechaInicioMostrar", fontSize = 14.sp)
                            Text("📅 Hasta: $fechaFinMostrar", fontSize = 14.sp)
                        }

                        Text("⏰ Horario: $horaEncendido - $horaApagado", fontSize = 14.sp)
                        if (descripcion.isNotEmpty()) {
                            Text("📝 Descripción: $descripcion", fontSize = 14.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Botón para guardar
            Button(
                onClick = {
                    guardarProgramacion(
                        ledSeleccionadoId,
                        tipoProgramacion,
                        fechaInicio,
                        fechaFin,
                        horaEncendido,
                        horaApagado,
                        descripcion.ifEmpty { "Programación desde app" },
                        programacionActiva,
                        context
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = ledSeleccionadoId.isNotEmpty() && fechaInicio.isNotEmpty() &&
                        (tipoProgramacion == "mismo_dia" || fechaFin.isNotEmpty()),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1976D2),
                    disabledContainerColor = Color.Gray
                )
            ) {
                Text(
                    text = "💾 GUARDAR PROGRAMACIÓN",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }

    @Composable
    fun HistorialScreen() {
        var historialList by remember { mutableStateOf<List<HistorialItem>>(emptyList()) }

        LaunchedEffect(Unit) {
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val tempList = mutableListOf<HistorialItem>()
                    for (childSnapshot in snapshot.children) {
                        val evento = childSnapshot.child("evento").getValue(String::class.java) ?: ""
                        val timestamp = childSnapshot.child("timestamp").getValue(String::class.java) ?: ""
                        val id = childSnapshot.key ?: ""
                        if (evento.isNotEmpty() && timestamp.isNotEmpty()) {
                            tempList.add(HistorialItem(evento, timestamp, id))
                        }
                    }
                    historialList = tempList.sortedByDescending { it.timestamp }
                }

                override fun onCancelled(error: DatabaseError) {}
            }
            historialRef.addValueEventListener(listener)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "📖 Historial de Eventos",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E88E5)
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(historialList, key = { it.id }) { item ->
                    HistorialItemCard(item)
                }
            }
        }
    }

    @Composable
    fun HistorialItemCard(item: HistorialItem) {
        Card(
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "Evento",
                    tint = Color.Gray
                )
                Column {
                    Text(
                        text = item.evento,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp
                    )
                    Text(
                        text = item.timestamp.replace("T", " a las "),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}