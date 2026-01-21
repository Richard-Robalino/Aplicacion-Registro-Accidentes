package com.example.miapp

import android.Manifest
import android.annotation.SuppressLint
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.miapp.ui.theme.MiAppTheme
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class TipoAccidente(val label: String) {
    CHOQUE("Choque"),
    COLISION("Colisión"),
    ATROPELLO("Atropello")
}

data class Accidente(
    val id: String = UUID.randomUUID().toString(),
    val tipo: TipoAccidente,
    val fechaMillis: Long,
    val matricula: String,
    val nombreConductor: String,
    val cedulaConductor: String,
    val observaciones: String,
    val foto: android.graphics.Bitmap?,
    val ubicacion: Location?
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MiAppTheme {
                AccidentesApp()
            }
        }
    }
}

@Composable
fun AccidentesApp() {
    val snackHostState = remember { SnackbarHostState() }
    val registros = remember { mutableStateListOf<Accidente>() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackHostState) }
    ) { inner ->
        AccidentesScreen(
            modifier = Modifier.padding(inner),
            registros = registros,
            onGuardar = { nuevo ->
                registros.add(0, nuevo)
            },
            snackHostState = snackHostState
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccidentesScreen(
    modifier: Modifier = Modifier,
    registros: List<Accidente>,
    onGuardar: (Accidente) -> Unit,
    snackHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ------- estados del formulario -------
    var tipoAccidente by remember { mutableStateOf(TipoAccidente.CHOQUE) }
    var fechaMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var matricula by remember { mutableStateOf("") }
    var nombreConductor by remember { mutableStateOf("") }
    var cedulaConductor by remember { mutableStateOf("") }
    var observaciones by remember { mutableStateOf("") }

    var foto by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var ubicacion by remember { mutableStateOf<Location?>(null) }

    var cargandoUbicacion by remember { mutableStateOf(false) }

    // ------- permisos dinámicos -------
    val permisosLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { /* resultado llega aquí, pero no hace falta procesarlo para compilar */ }

    fun pedirPermisos(vararg permisos: String) {
        permisosLauncher.launch(permisos.toList().toTypedArray())
    }

    // ------- cámara (captura rápida) -------
    val camaraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) foto = bitmap
    }

    // ------- ubicación -------
    val fusedClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    @SuppressLint("MissingPermission")
    fun obtenerUbicacion() {
        cargandoUbicacion = true

        fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc: Location? ->
                ubicacion = loc
                cargandoUbicacion = false
            }
            .addOnFailureListener {
                cargandoUbicacion = false
            }
    }

    // ------- date picker -------
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = fechaMillis)
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { fechaMillis = it }
                        showDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // ------- UI -------
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Registro de Accidentes",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        // Tipo de accidente (dropdown)
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = tipoAccidente.label,
                onValueChange = {},
                readOnly = true,
                label = { Text("Tipo de accidente") },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                TipoAccidente.values().forEach { item ->
                    DropdownMenuItem(
                        text = { Text(item.label) },
                        onClick = {
                            tipoAccidente = item
                            expanded = false
                        }
                    )
                }
            }
        }

        // Fecha
        val fechaTexto = remember(fechaMillis) {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(fechaMillis))
        }
        OutlinedTextField(
            value = fechaTexto,
            onValueChange = {},
            readOnly = true,
            label = { Text("Fecha del siniestro") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                TextButton(onClick = { showDatePicker = true }) { Text("Cambiar") }
            }
        )

        OutlinedTextField(
            value = matricula,
            onValueChange = { matricula = it },
            label = { Text("Matrícula del auto") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = nombreConductor,
            onValueChange = { nombreConductor = it },
            label = { Text("Nombre del conductor") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = cedulaConductor,
            onValueChange = { cedulaConductor = it },
            label = { Text("Cédula del conductor") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = observaciones,
            onValueChange = { observaciones = it },
            label = { Text("Observaciones") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        // Foto
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Fotografía", fontWeight = FontWeight.Bold)

                if (foto != null) {
                    Image(
                        bitmap = foto!!.asImageBitmap(),
                        contentDescription = "Foto",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                    )
                } else {
                    Text("Sin foto aún")
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            pedirPermisos(Manifest.permission.CAMERA)
                            camaraLauncher.launch(null)
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Tomar foto") }

                    OutlinedButton(
                        onClick = { foto = null },
                        modifier = Modifier.weight(1f),
                        enabled = foto != null
                    ) { Text("Quitar") }
                }
            }
        }

        // Ubicación
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Ubicación GPS", fontWeight = FontWeight.Bold)

                if (cargandoUbicacion) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Obteniendo ubicación...")
                    }
                } else {
                    if (ubicacion != null) {
                        Text("Lat: ${ubicacion!!.latitude}")
                        Text("Lon: ${ubicacion!!.longitude}")
                        Text("Precisión: ${ubicacion!!.accuracy} m")
                    } else {
                        Text("Sin ubicación aún")
                    }
                }

                Button(
                    onClick = {
                        pedirPermisos(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                        obtenerUbicacion()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Obtener ubicación")
                }
            }
        }

        // Guardar
        Button(
            onClick = {
                val ok = matricula.isNotBlank()
                        && nombreConductor.isNotBlank()
                        && cedulaConductor.isNotBlank()

                if (!ok) {
                    scope.launch {
                        snackHostState.showSnackbar("Completa matrícula, nombre y cédula.")
                    }
                    return@Button
                }

                val nuevo = Accidente(
                    tipo = tipoAccidente,
                    fechaMillis = fechaMillis,
                    matricula = matricula.trim(),
                    nombreConductor = nombreConductor.trim(),
                    cedulaConductor = cedulaConductor.trim(),
                    observaciones = observaciones.trim(),
                    foto = foto,
                    ubicacion = ubicacion
                )

                onGuardar(nuevo)

                // Vibrar 5 segundos
                vibrar(context, 5000L)

                // Limpiar formulario
                matricula = ""
                nombreConductor = ""
                cedulaConductor = ""
                observaciones = ""
                foto = null
                ubicacion = null
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Guardar accidente")
        }

        Divider()

        Text("Registros guardados (${registros.size})", fontWeight = FontWeight.Bold)

        if (registros.isEmpty()) {
            Text("Aún no hay registros.")
        } else {
            registros.take(5).forEach { r ->
                RegistroCard(r)
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RegistroCard(r: Accidente) {
    val fecha = remember(r.fechaMillis) {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(r.fechaMillis))
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("${r.tipo.label} • $fecha", fontWeight = FontWeight.Bold)
            Text("Matrícula: ${r.matricula}")
            Text("Conductor: ${r.nombreConductor}")
            Text("Cédula: ${r.cedulaConductor}")

            if (r.ubicacion != null) {
                Text("GPS: ${r.ubicacion.latitude}, ${r.ubicacion.longitude}")
            } else {
                Text("GPS: (sin ubicación)")
            }

            Text(text = if (r.foto != null) "Foto: Sí" else "Foto: No")

            if (r.observaciones.isNotBlank()) {
                Text("Obs: ${r.observaciones}")
            }
        }
    }
}

private fun vibrar(context: android.content.Context, durationMs: Long) {
    val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(VibratorManager::class.java)
        vm?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
            ?: (context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator)
    }

    if (vibrator == null || !vibrator.hasVibrator()) return

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        // API 26+
        val effect = VibrationEffect.createOneShot(
            durationMs,
            VibrationEffect.DEFAULT_AMPLITUDE
        )
        vibrator.vibrate(effect)
    } else {
        // API 24-25 (método viejo)
        @Suppress("DEPRECATION")
        vibrator.vibrate(durationMs)
    }
}
