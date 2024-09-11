package com.example.indicadores
import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.wear.compose.material.ContentAlpha
import androidx.wear.compose.material.LocalContentAlpha
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import viewmodels.DatosGuardadosViewModel
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()
            val isLoggedIn = rememberSaveable { mutableStateOf(false) }
            val coroutineScope = rememberCoroutineScope()
            val viewModel: DatosGuardadosViewModel = viewModel(
                factory = DatosGuardadosViewModelFactory(applicationContext)
            )
            NavHost(navController, startDestination = "startForms/{username}") {
                composable("startForms/{username}",
                    arguments = listOf(
                        navArgument("username") { type = NavType.StringType }
                    )) { backStackEntry ->
                    val username = backStackEntry.arguments?.getString("username") ?: "organolepticas"
                    startForms(
                        navController = navController,
                        viewModel = viewModel,
                        context = applicationContext,
                        username = username
                    )
                }

                composable("startScreen/{username}",
                    arguments = listOf(
                        navArgument("username") { type = NavType.StringType }
                    )) { backStackEntry ->
                    val username = backStackEntry.arguments?.getString("username") ?: "organolepticas"
                    startScreen(navController = navController, viewModel = viewModel,context = applicationContext,username = username)
                }
            }
        }
    }
}

class DatosGuardadosViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DatosGuardadosViewModel::class.java)) {
            return DatosGuardadosViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
data class IndicadoresResponse(
    val dias_desde_cargue: Int,
    val dias_desde_muestreo: Int,
    val cantidad_datos: Int,
    val cantidad_usuarios: Int
)
data class ProductividadResponse(
    val dispositivo: String,
    val productividad_media: Int
)
// Define el servicio de Retrofit
interface ApiService {
    @GET("consultor/api/indicadores_peso_planta/dias")
    suspend fun getIndicadores(): List<IndicadoresResponse>

    @GET("consultor/api/indicadores_peso_planta")
    suspend fun getProductividad(): List<Map<String, Int>>
}

// Configura Retrofit
val retrofit = Retrofit.Builder()
    .baseUrl("http://controlgestionguapa.ddns.net:8000/")
    .addConverterFactory(GsonConverterFactory.create())
    .build()

val apiService = retrofit.create(ApiService::class.java)
class LocalUserStore(private val context: Context) {
    private val sharedPreferences = context.getSharedPreferences("local_user_store", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getUsers(): MutableList<User> {
        val usersJson = sharedPreferences.getString("users", null)
        return if (usersJson != null) {
            val type = object : TypeToken<MutableList<User>>() {}.type
            gson.fromJson(usersJson, type)
        } else {
            mutableListOf()
        }
    }

    fun saveUsers(users: MutableList<User>) {
        val usersJson = gson.toJson(users)
        sharedPreferences.edit().putString("users", usersJson).apply()
    }

    fun saveUser(user: User) {
        val users = getUsers()
        users.removeAll { it.username == user.username }  // Remove any existing user with the same username
        users.add(user)
        saveUsers(users)
    }

    fun getUser(username: String): User? {
        return getUsers().find { it.username == username }
    }

    data class User(val username: String, val password: String)
}
@Composable
fun startForms(
    navController: NavController,
    viewModel: DatosGuardadosViewModel,
    context: Context,
    username: String
) {
    val scrollState = rememberScrollState()

    var indicadores by remember { mutableStateOf<List<IndicadoresResponse>?>(null) }
    var productividad by remember { mutableStateOf<List<Map<String, Int>>?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val apiService = Retrofit.Builder()
                    .baseUrl("http://controlgestionguapa.ddns.net:8000/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(ApiService::class.java)

                val indicadoresResponse = apiService.getIndicadores()
                val productividadResponse = apiService.getProductividad()

                indicadores = indicadoresResponse
                productividad = productividadResponse
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Column(
        modifier = Modifier
            .verticalScroll(scrollState)
            .fillMaxSize()
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "Panel de Control - Control Gestión \n Muestreos Peso Planta",
            fontSize = 30.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            textAlign = TextAlign.Center
        )

        Text(
            text = "Fecha Actualización",
            fontSize = 20.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            textAlign = TextAlign.Center
        )
        val datosGuardados = viewModel.obtenerDatosGuardados()
        val filteredData = datosGuardados.filter { it["Aplicacion"] == "peso_planta" }
        val fechaString = filteredData.maxByOrNull { it["Fecha_Cargue"] as? String ?: "" }?.get("Fecha_Cargue") as? String
        val formatoFecha = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val fechaDate: String? = fechaString?.let { fecha ->
            try {
                val parsedDate = formatoFecha.parse(fecha)
                parsedDate?.let { formatoFecha.format(it) } ?: ""
            } catch (e: ParseException) {
                ""
            }
        }
        Text(
            text = "$fechaDate",
            fontSize = 16.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))
        var showDialogData by remember { mutableStateOf(false) }
        var peso_planta = ""
        var cosecha = ""
        var verificacion = ""
        Button(
            onClick = {
                showDialogData = true

                // Primera actualización: Peso Planta
                updateBlocksPesoPlanta(viewModel) { successPesoPlanta ->
                    if (successPesoPlanta) {
                        peso_planta = "\n-Peso Planta Cargado"
                        // Segunda actualización: Cosecha
                        updateBlocksCosecha(viewModel) { successCosecha ->
                            if (successCosecha) {
                                cosecha = "\n-Cosecha Cargado"
                                // Tercera actualización: Observaciones
                                updateBlocksVerificacion(viewModel) { successObservaciones ->
                                    showDialogData = !successObservaciones
                                    println("Actualización Observaciones: $successObservaciones")
                                }
                            } else {
                                showDialogData = true // mantener el diálogo abierto si falla cosecha
                                println("Error en Cosecha")
                            }
                        }
                    } else {
                        showDialogData = true // mantener el diálogo abierto si falla peso planta
                        println("Error en Peso Planta")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Actualizar Datos",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (showDialogData) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(text = "Se están actualizando los datos. Por favor, espere..."+peso_planta+cosecha) },
                confirmButton = {}
            )
        }


        Spacer(modifier = Modifier.height(16.dp))
        var selectedBlock by remember { mutableStateOf("") }
        val blocks = viewModel.obtenerDatosGuardados()
            .filter { it["Aplicacion"] == "peso_planta" }
            .mapNotNull { it["bloque"] as? String } // Mapea y filtra nulos
            .distinct() // Elimina duplicados


        TextField(
            value = selectedBlock,
            onValueChange = { selectedBlock = it },
            label = { Text("Ingrese el bloque") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 7.dp).border(1.dp, Color.Gray, shape = RoundedCornerShape(4.dp)),
            singleLine = true
        )

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 7.dp).horizontalScroll(rememberScrollState())
        ) {
            Text("Bloques disponibles:", fontWeight = FontWeight.Bold)
            if (selectedBlock.isNotBlank()) {
                Row {
                    blocks.filter { it.contains(selectedBlock, ignoreCase = true) }.take(4).forEach { block ->
                        ClickableText(
                            text = AnnotatedString(block),
                            onClick = { selectedBlock = block },
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
                        )
                    }
                }
            }
        }
        var grupo_forza by remember { mutableStateOf("") }
        val grupos = viewModel.obtenerDatosGuardados()
            .map { it["grupo_forza"] as? String }
            .filterNotNull() // Elimina valores nulos
            .distinct() // Elimina valores duplicados


        TextField(
            value = grupo_forza,
            onValueChange = { grupo_forza = it },
            label = { Text("Ingrese el grupo forza") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 7.dp).border(1.dp, Color.Gray, shape = RoundedCornerShape(4.dp)),
            singleLine = true
        )

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 7.dp).horizontalScroll(rememberScrollState())
        ) {
            Text("Grupos forza disponibles:", fontWeight = FontWeight.Bold)
            if (grupo_forza.isNotBlank()) {
                Row {
                    grupos.filter { it.contains(grupo_forza, ignoreCase = true) }.take(4).forEach { block ->
                        ClickableText(
                            text = AnnotatedString(block),
                            onClick = { grupo_forza = block },
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
                        )
                    }
                }
            }
        }



        val consulta = filteredData.filter {
            it["bloque"] == selectedBlock || it["grupo_forza"] == grupo_forza
        }
        var totalObservaciones = 0.0f
        var totalBloques = 0
        fun convertToFloat(value: Any?): Float {
            return when (value) {
                is Float -> value
                is Double -> value.toFloat()
                is Int -> value.toFloat()
                is String -> value.toFloatOrNull() ?: 0.0f
                else -> 0.0f
            }
        }
// Recorrer los resultados filtrados y sumar las observaciones
        for (item in consulta) {
            println(item)

            val cuentaBabosa = convertToFloat(item["cuenta_babosa"])
            val cuentaCaracol = convertToFloat(item["cuenta_caracol"])
            val cuentaCochinilla = convertToFloat(item["cuenta_cochinilla"])
            val cuentaHormiga = convertToFloat(item["cuenta_hormiga"])
            val cuentaMeristemo = convertToFloat(item["cuenta_meristemo"])
            val cuentaNinguno = convertToFloat(item["cuenta_ninguno"])
            val cuentaSinfilido = convertToFloat(item["cuenta_sinfilido"])

            println("loca1"+cuentaSinfilido+item["cuenta_sinfilido"])

            // Sumar las observaciones de cada tipo
            val sumaObservaciones = cuentaBabosa + cuentaCaracol + cuentaCochinilla + cuentaHormiga +
                    cuentaMeristemo + cuentaNinguno + cuentaSinfilido

            // Acumular la suma de observaciones
            totalObservaciones += sumaObservaciones
            totalBloques++
        }

// Calcular el promedio
        val promedioObservacionesPorBloque = if (totalBloques > 0) {
            val promedio = totalObservaciones / totalBloques
            String.format("%.2f", promedio).toFloat()
        } else {
            0.0f
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Promedio de Observaciones por Bloque",
            fontSize = 20.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .wrapContentHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "$promedioObservacionesPorBloque",
                    textAlign = TextAlign.Center,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        val fechaCargueString = filteredData.maxByOrNull { it["fecha"] as? String ?: "" }?.get("fecha") as? String
        val formatoFechaCargue = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val fechaDateCargue: String? = fechaCargueString?.let { fechaString ->
            val parsedDate = formatoFechaCargue.parse(fechaString)
            parsedDate?.let { formatoFechaCargue.format(it) } ?: ""
        }

        Text(
            text = "Fecha Último Cargue",
            fontSize = 20.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .wrapContentHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val fechaCargue = indicadores?.firstOrNull()?.dias_desde_cargue?.let { dias ->
                    val today = LocalDate.now()
                    val fecha = today.minusDays(dias.toLong())
                    fecha.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                } ?: "Fecha no disponible"

                Text(
                    text = "$fechaDateCargue",
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Gráfico de pastel (Pie chart) - Sistema Radicular
        Text(
            text = "Promedio de Peso[g] vs tiempo",
            fontSize = 20.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))
        println("Contenido de consulta: $consulta")
        fun randomPeso(): String {
            val minPeso = 1800
            val maxPeso = 2500
            val peso = Random.nextInt(minPeso, maxPeso + 1).toFloat()
            return peso.toString()
        }
        // Extraer el primer elemento
        if (consulta.isNotEmpty()) {
            // Extraer el primer elemento
            // Extraer el primer y segundo elemento
            val consulta1 = consulta.map { item ->
                // Obtener los campos con valores predeterminados si no están presentes
                val bloque = item["bloque"] as? String ?: "PC234455"
                val fechaStr = item["fecha"] as? String ?: "2024-01-15"
                val promedioPeso = convertToFloat(item["promedio_peso"])

                // Crear un mapa con los datos
                mapOf("bloque" to bloque, "fecha" to fechaStr, "promedio_peso" to promedioPeso)
            }

            // Convertir las fechas a LocalDate y ordenar por fecha en orden descendente
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val consultaOrdenada = consulta1.sortedByDescending {
                LocalDate.parse(it["fecha"] as String, formatter)
            }

            // Crear el mapa de datos sintéticos ordenados
            val syntheticData = mutableMapOf<String, MutableList<Pair<String, Float>>>()

            for (item in consultaOrdenada) {
                // Obtener el bloque, fecha y peso de cada elemento
                val bloque = item["bloque"] as? String ?: continue
                val fecha = item["fecha"] as? String ?: continue
                val peso = convertToFloat(item["promedio_peso"])

                if (peso == null) {
                    println("Error al convertir el peso: ${item["promedio_peso"]}")
                    continue
                }

                // Agregar el par (fecha, peso) a la lista del bloque correspondiente
                syntheticData.computeIfAbsent(bloque) { mutableListOf() }.add(Pair(fecha, peso))
            }

            // Ordenar las entradas por fecha antes de pasarlas al gráfico
            syntheticData.forEach { (bloque, list) ->
                syntheticData[bloque] =
                    list.sortedWith(compareBy { parseDate(it.first) ?: Long.MAX_VALUE }).toMutableList()
            }

            // Usa los datos en LineChartScreen
            LineChartScreen(data = syntheticData)

        } else {
            println("La consulta está vacía.")
        }




        Spacer(modifier = Modifier.height(16.dp))

        // Gráfico de pastel (Pie chart) - Sistema Radicular
        Text(
            text = "Distribución del Sistema Radicular",
            fontSize = 20.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        val cuenta_tipo_sistema_radicular1 = consulta
            ?.sumOf { (it["cuenta_tipo_sistema_radicular1"] as? Double) ?: 0.0 }
            ?: 0.0
        val cuenta_tipo_sistema_radicular2 = consulta
            ?.sumOf { (it["cuenta_tipo_sistema_radicular2"] as? Double) ?: 0.0 }
            ?: 0.0
        val cuenta_tipo_sistema_radicular3 = consulta
            ?.sumOf { (it["cuenta_tipo_sistema_radicular3"] as? Double) ?: 0.0 }
            ?: 0.0

        // Sumar las cuentas, asegurando que no sean nulas
        val total = cuenta_tipo_sistema_radicular1+cuenta_tipo_sistema_radicular2+cuenta_tipo_sistema_radicular3
        println("prueba"+total)
        val sistemaRadicularData = if (total == 0.0) {
            mapOf(
                "Sistema Radicular Alto" to 0f,
                "Sistema Radicular Medio" to 0f,
                "Sistema Radicular Bajo" to 0f
            )
        } else {
            mapOf(
                "Sistema Radicular Alto" to ((cuenta_tipo_sistema_radicular1 / total) * 100).toFloat(),
                "Sistema Radicular Medio" to ((cuenta_tipo_sistema_radicular2 / total) * 100).toFloat(),
                "Sistema Radicular Bajo" to ((cuenta_tipo_sistema_radicular3 / total) * 100).toFloat()
            )
        }


        // Definir los colores para cada sistema radicular
        val sistemaRadicularLegend = mapOf(
            "Sistema Radicular Alto" to Color.Red,
            "Sistema Radicular Medio" to Color.Green,
            "Sistema Radicular Bajo" to Color.Blue
        )

        // Mostrar el gráfico de pastel con los datos y la leyenda
        PieChartScreen(data = sistemaRadicularData, legend = sistemaRadicularLegend)


        Spacer(modifier = Modifier.height(16.dp))

        // Gráfico de pastel (Pie chart) - Sistema Radicular
        Text(
            text = "Distribución de Observaciones",
            fontSize = 20.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            textAlign = TextAlign.Center
        )
        println(consulta)
        Spacer(modifier = Modifier.height(16.dp))
        val meristemo= consulta
            ?.sumOf { (it["cuenta_meristemo"] as? Double) ?: 0.0 }
            ?: 0.0
        val sinfilido= consulta
            ?.sumOf { (it["cuenta_sinfilido"] as? Double) ?: 0.0 }
            ?: 0.0
        val caracol= consulta
            ?.sumOf { (it["cuenta_caracol"] as? Double) ?: 0.0 }
            ?: 0.0
        val babosa= consulta
            ?.sumOf { (it["cuenta_babosa"] as? Double) ?: 0.0 }
            ?: 0.0
        val cochinilla= consulta
            ?.sumOf { (it["cuenta_cochinilla"] as? Double) ?: 0.0 }
            ?: 0.0
        val hormiga= consulta
            ?.sumOf { (it["cuenta_hormiga"] as? Double) ?: 0.0 }
            ?: 0.0
        // Gráfico de barras horizontales
        val categoriasData = mapOf(
            "Meristemo" to meristemo.toFloat(),
            "Sinfilido" to sinfilido.toFloat(),
            "Caracol" to caracol.toFloat(),
            "Babosa" to babosa.toFloat(),
            "Hormiga" to hormiga.toFloat(),
            "Cochinilla" to cochinilla.toFloat()
        )

        HorizontalBarChart(data = categoriasData)

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = "Verificación Cosechas",
                fontSize = 30.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                textAlign = TextAlign.Center
            )

            Text(
                text = "Fecha Actualización",
                fontSize = 20.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                textAlign = TextAlign.Center
            )
            val datosGuardadosCosecha = viewModel.obtenerDatosGuardados()
            val filteredDataCosecha = datosGuardadosCosecha.filter { it["Aplicacion"] == "verificacion_cosecha" }
            val fechaStringCosecha = filteredDataCosecha.maxByOrNull { it["Fecha_Cargue"] as? String ?: "" }?.get("Fecha_Cargue") as? String
            val formatoFecha = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            val fechaDateCosecha: String? = fechaStringCosecha?.let { fecha ->
                try {
                    val parsedDate = formatoFecha.parse(fecha)
                    parsedDate?.let { formatoFecha.format(it) } ?: ""
                } catch (e: ParseException) {
                    ""
                }
            }

            Text(
                text = "$fechaDateCosecha",
                fontSize = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                textAlign = TextAlign.Center
            )
            val consultaCosecha = filteredDataCosecha.filter {
                it["bloque"] == selectedBlock || it["grupo_forza"] == grupo_forza
            }

            Spacer(modifier = Modifier.height(16.dp))
            val cantidadCosecha = consultaCosecha.maxByOrNull { (it["cantidad_observaciones"] as? String)?.toIntOrNull() ?: 0 }
                ?.get("cantidad_observaciones") as? String ?: ""

            Text(
                text = "Total cantidad de observaciones",
                fontSize = 20.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "$cantidadCosecha",
                        textAlign = TextAlign.Center,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            val fechaCargueStringCosecha = filteredDataCosecha.maxByOrNull { it["fecha"] as? String ?: "" }?.get("fecha") as? String
            val formatoFechaCargueCosecha = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val fechaDateCargueCosecha: String? = fechaCargueStringCosecha?.let { fechaString ->
                val parsedDate = formatoFechaCargueCosecha.parse(fechaString)
                parsedDate?.let { formatoFechaCargueCosecha.format(it) } ?: ""
            }


            Text(
                text = "Fecha Último Cargue",
                fontSize = 20.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val fechaCargue = indicadores?.firstOrNull()?.dias_desde_cargue?.let { dias ->
                        val today = LocalDate.now()
                        val fecha = today.minusDays(dias.toLong())
                        fecha.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    } ?: "Fecha no disponible"

                    Text(
                        text = "$fechaDateCargueCosecha",
                        textAlign = TextAlign.Center,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }


            Spacer(modifier = Modifier.height(16.dp))

            // Gráfico de pastel (Pie chart) - Sistema Radicular
            Text(
                text = "Distribución de Observaciones",
                fontSize = 20.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                textAlign = TextAlign.Center
            )
            println(consultaCosecha)
            Spacer(modifier = Modifier.height(16.dp))
            val ausente= consultaCosecha
                ?.sumOf { (it["ausente"] as? Double) ?: 0.0 }
                ?: 0.0
            val bajo_peso= consultaCosecha
                ?.sumOf { (it["bajo_peso"] as? Double) ?: 0.0 }
                ?: 0.0
            val coronas= consultaCosecha
                ?.sumOf { (it["coronas"] as? Double) ?: 0.0 }
                ?: 0.0
            val descarte_entre_camas= consultaCosecha
                ?.sumOf { (it["descarte_entre_camas"] as? Double) ?: 0.0 }
                ?: 0.0
            val fruta_adelanta= consultaCosecha
                ?.sumOf { (it["fruta_adelanta"] as? Double) ?: 0.0 }
                ?: 0.0
            val fruta_enferma= consultaCosecha
                ?.sumOf { (it["fruta_enferma"] as? Double) ?: 0.0 }
                ?: 0.0
            val fruta_joven= consultaCosecha
                ?.sumOf { (it["fruta_joven"] as? Double) ?: 0.0 }
                ?: 0.0
            val fruta_no_aprovechable= consultaCosecha
                ?.sumOf { (it["fruta_no_aprovechable"] as? Double) ?: 0.0 }
                ?: 0.0
            val fruta_dejada_por_cosecha= consultaCosecha
                ?.sumOf { (it["fruta_dejada_por_cosecha"] as? Double) ?: 0.0 }
                ?: 0.0
            val golpe_de_agua= consultaCosecha
                ?.sumOf { (it["golpe_de_agua"] as? Double) ?: 0.0 }
                ?: 0.0
            val mortalidad= consultaCosecha
                ?.sumOf { (it["mortalidad"] as? Double) ?: 0.0 }
                ?: 0.0
            val  plantas_sin_parir= consultaCosecha
                ?.sumOf { (it["plantas_sin_parir"] as? Double) ?: 0.0 }
                ?: 0.0
            val  quema_de_sol= consultaCosecha
                ?.sumOf { (it["quema_de_sol"] as? Double) ?: 0.0 }
                ?: 0.0
            // Gráfico de barras horizontales
            val categoriasData = mapOf(
                "Ausente" to ausente.toFloat(),
                "Bajo Peso" to bajo_peso.toFloat(),
                "Coronas" to coronas.toFloat(),
                "Descarte entre Camas" to descarte_entre_camas.toFloat(),
                "Fruta Adelantada" to fruta_adelanta.toFloat(),
                "Fruta Enferma" to fruta_enferma.toFloat(),
                "Fruta Joven" to fruta_joven.toFloat(),
                "Fruta no Aprovechable" to fruta_no_aprovechable.toFloat(),
                "Fruta Dejada por Cosecha" to fruta_dejada_por_cosecha.toFloat(),
                "Golpe de Agua" to golpe_de_agua.toFloat(),
                "Mortalidad" to  mortalidad.toFloat(),
                "Plantas sin Parir" to  plantas_sin_parir.toFloat(),
                "Quema de Sol" to  quema_de_sol.toFloat()

            )

            HorizontalBarChart(data = categoriasData)

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Verificación Observaciones",
                fontSize = 30.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                textAlign = TextAlign.Center
            )

            Text(
                text = "Fecha Actualización",
                fontSize = 20.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                textAlign = TextAlign.Center
            )
            val datosGuardadosVerificacion = viewModel.obtenerDatosGuardados()
            val filteredDataVerificacion = datosGuardados.filter { it["Aplicacion"] == "verificacion_observaciones" }
            val fechaStringVerificacion = filteredDataVerificacion.maxByOrNull { it["Fecha_Cargue"] as? String ?: "" }?.get("Fecha_Cargue") as? String
            val formatoFechaVerificacion = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            val fechaDateVerificacion: String? = fechaStringVerificacion?.let { fecha ->
                try {
                    val parsedDate = formatoFechaVerificacion.parse(fecha)
                    parsedDate?.let { formatoFechaVerificacion.format(it) } ?: ""
                } catch (e: ParseException) {
                    ""
                }
            }

            Text(
                text = "$fechaDateVerificacion",
                fontSize = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            val consultaVerificacion = filteredDataVerificacion.filter {
                it["bloque"] == selectedBlock || it["grupo_forza"] == grupo_forza
            }
            Spacer(modifier = Modifier.height(16.dp))
            val controlada = consultaVerificacion?.firstOrNull()?.get("porc_controlada") as? Double ?: 0.0
            val critica = consultaVerificacion?.firstOrNull()?.get("porc_critica") as? Double ?: 0.0
            val leve = consultaVerificacion?.firstOrNull()?.get("porc_leve") as? Double ?: 0.0
            val moderada = consultaVerificacion?.firstOrNull()?.get("porc_moderada") as? Double ?: 0.0
            Text(
                text = "Porcentaje Maleza",
                fontSize = 20.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Primera Tarjeta
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .padding(4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Controlada\n$controlada %", // Primer contenido
                            textAlign = TextAlign.Center,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Segunda Tarjeta
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .padding(4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Critica\n$critica %", // Segundo contenido
                            textAlign = TextAlign.Center,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Tercera Tarjeta
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .padding(4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Leve\n$leve %", // Tercer contenido
                            textAlign = TextAlign.Center,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Cuarta Tarjeta
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .padding(4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Moderada\n$moderada %", // Cuarto contenido
                            textAlign = TextAlign.Center,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }


            Spacer(modifier = Modifier.height(16.dp))
            val fechaCargueStringVerificacion = filteredDataVerificacion.maxByOrNull { it["fecha"] as? String ?: "" }?.get("fecha") as? String
            val formatoFechaCargueVerificacion = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val fechaDateCargueVerificacion: String? = fechaCargueStringVerificacion?.let { fechaString ->
                val parsedDate = formatoFechaCargueVerificacion.parse(fechaString)
                parsedDate?.let { formatoFechaCargueVerificacion.format(it) } ?: ""
            }


            Text(
                text = "Fecha Último Cargue",
                fontSize = 20.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {

                    Text(
                        text = "$fechaDateCargueVerificacion",
                        textAlign = TextAlign.Center,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }


            Spacer(modifier = Modifier.height(16.dp))

            // Gráfico de pastel (Pie chart) - Sistema Radicular
            Text(
                text = "Estado Puentes",
                fontSize = 20.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                textAlign = TextAlign.Center
            )
            println(consulta)
            Spacer(modifier = Modifier.height(16.dp))
            val cumple_puente = consultaVerificacion
                ?.sumOf { (it["cumple_vias"] as? Double) ?: 0.0 }
                ?: 0.0
            val en_mal_estado_puentes = consultaVerificacion
                ?.sumOf { (it["en_mal_estado_vias"] as? Double) ?: 0.0 }
                ?: 0.0
            val sin_puente = consultaVerificacion
                ?.sumOf { (it["sin_puente"] as? Double) ?: 0.0 }
                ?: 0.0

            // Gráfico de barras horizontales
            val categoriasDataVerificacion = mapOf(
                "Cumple" to cumple_puente.toFloat(),
                "En Mal Estado" to en_mal_estado_puentes.toFloat(),
                "Sin Puente" to sin_puente.toFloat()

            )

            HorizontalBarChart(data = categoriasDataVerificacion)
            Spacer(modifier = Modifier.height(16.dp))

            // Gráfico de pastel (Pie chart) - Sistema Radicular
            Text(
                text = "Estado Trinchos",
                fontSize = 20.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                textAlign = TextAlign.Center
            )
            println(consulta)
            Spacer(modifier = Modifier.height(16.dp))
            Spacer(modifier = Modifier.height(16.dp))

            val cumple_trincho = consultaVerificacion
                ?.sumOf { (it["cumple_trincho"] as? Double) ?: 0.0 }
                ?: 0.0
            val en_mal_estado_trincho = consultaVerificacion
                ?.sumOf { (it["en_mal_estado_trincho"] as? Double) ?: 0.0 }
                ?: 0.0
            val sin_trincho = consultaVerificacion
                ?.sumOf { (it["sin_trincho"] as? Double) ?: 0.0 }
                ?: 0.0

            // Gráfico de barras horizontales
            val categoriasData1 = mapOf(
                "Cumple" to cumple_trincho.toFloat(),
                "En Mal Estado" to en_mal_estado_trincho.toFloat(),
                "Sin Puente" to sin_trincho.toFloat()

            )

            HorizontalBarChart(data = categoriasData1)
            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = "Consulta Bloques",
                fontSize = 30.sp,
                modifier = Modifier
                    .fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))
            val datosGuardadosBloques = viewModel.obtenerDatosGuardados()
            val filteredDataBloques = datosGuardadosBloques.filter { it["Aplicacion"] == "consulta_bloques" }

            val fechaStringBloques = filteredDataBloques.maxByOrNull { it["Fecha_Cargue"] as? String ?: "" }?.get("Fecha_Cargue") as? String
            val formatoFechaBloques = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            val fechaDateBloques: String? = fechaStringBloques?.let { fecha ->
                try {
                    val parsedDate = formatoFechaBloques.parse(fecha)
                    parsedDate?.let { formatoFechaBloques.format(it) } ?: ""
                } catch (e: ParseException) {
                    ""
                }
            }// Aquí debes usar tu fuente de datos real
            // Fecha de actualización
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Fecha Actualización:", fontWeight = FontWeight.Bold)
                Text(
                    text = "$fechaDateBloques",  // Coloca la fecha dinámica aquí
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }

            val consultaBloques = filteredDataBloques.filter {
                it["bloque"] == selectedBlock || it["grupo_forza"] == grupo_forza
            }
            Spacer(modifier = Modifier.height(16.dp))
            // Datos del bloque
            val bloque = consultaBloques.maxByOrNull { it["bloque"] as? String ?: "" }?.get("bloque") as? String
            val rango = consultaBloques.maxByOrNull { it["rango_semilla"] as? String ?: "" }?.get("rango_semilla") as? String
            val fecha_siembra = consultaBloques.maxByOrNull { it["fecha_siembra"] as? String ?: "" }?.get("fecha_siembra") as? String
            val grupo_siembra = consultaBloques.maxByOrNull { it["grupo_siembra"] as? String ?: "" }?.get("grupo_siembra") as? String
            val fecha_forza = consultaBloques.maxByOrNull { it["fecha_forza"] as? String ?: "" }?.get("fecha_forza") as? String
            val grupo_forza = consultaBloques.maxByOrNull { it["grupo_forza"] as? String ?: "" }?.get("grupo_forza") as? String
            val primera_cosecha = consultaBloques.maxByOrNull { it["min"] as? String ?: "" }?.get("min") as? String
            val ultima_cosecha = consultaBloques.maxByOrNull { it["max"] as? String ?: "" }?.get("max") as? String
            val poblacion = consultaBloques.maxByOrNull { it["poblacion"] as? String ?: "" }?.get("poblacion") as? String
            val area_bruta = consultaBloques.maxByOrNull { it["area_bruta"] as? String ?: "" }?.get("area_bruta") as? String
            val area_neta = consultaBloques.maxByOrNull { it["area_neta"] as? String ?: "" }?.get("area_neta") as? String
            val frutas = consultaBloques.maxByOrNull { it["frutas"] as? String ?: "" }?.get("frutas") as? String
            val total_peso = consultaBloques.maxByOrNull { it["peso"] as? String ?: "" }?.get("peso") as? String
            val razon = consultaBloques.maxByOrNull { it["razon"] as? String ?: "" }?.get("razon") as? String

            DataRow(title = "Bloque", value = bloque ?: "N/A")
            DataRow(title = "Rango Semilla", value = rango ?: "N/A")
            DataRow(title = "Fecha Siembra", value = fecha_siembra ?: "N/A")
            DataRow(title = "Grupo Siembra", value = grupo_siembra ?: "N/A")
            DataRow(title = "Fecha Forza", value = fecha_forza ?: "N/A")
            DataRow(title = "Grupo Forza", value = grupo_forza ?: "N/A")
            DataRow(title = "Fecha Primera Cosecha", value = primera_cosecha ?: "N/A")
            DataRow(title = "Fecha Última Cosecha", value = ultima_cosecha ?: "N/A")
            DataRow(title = "Población", value = poblacion ?: "N/A")
            DataRow(title = "Área Bruta", value = area_bruta ?: "N/A")
            DataRow(title = "Área Neta", value = area_neta ?: "N/A")
            DataRow(title = "Frutas", value = frutas ?: "N/A")
            DataRow(title = "Total Peso [kg]", value = total_peso ?: "N/A")
            DataRow(title = "Razón Peso/Frutas [g/fruta]", value = razon ?: "N/A")


            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    generateExcelFilePesoPlanta(viewModel, context)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "Generar Reporte Excel",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize()
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Image(
                    painter = painterResource(id = R.drawable.logi),
                    contentDescription = null,
                    modifier = Modifier.size(80.dp)
                )
                Text(
                    text = "Powered by Guapa \n Versión 2.0",
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun DataRow(title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = "$title:", fontWeight = FontWeight.Bold)
        Text(text = value)
    }
    Divider()
}
@Composable
fun HorizontalBarChart(data: Map<String, Float>) {
    val total = data.values.sum()
    val barHeight = 40.dp
    val spacing = 8.dp
    val maxBarWidth = 300.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        data.forEach { (label, value) ->
            val barWidth = ( (value / total )*400).dp // Scale to percentage
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp,
                        horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    modifier = Modifier
                        .width(120.dp),
                    fontSize = 16.sp
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(barHeight)
                        .background(Color.Gray.copy(alpha = 0.2f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(barWidth)
                            .background(Color.Blue)
                    )
                }
                Text(
                    text = "${(value / total * 100).toInt()}%",
                    modifier = Modifier
                        .width(40.dp),
                    textAlign = TextAlign.End,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
fun PieChartScreen(data: Map<String, Float>, legend: Map<String, Color>) {
    val total = data.values.sum()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Gráfico de pastel
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(end = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier
                .fillMaxSize()
                .aspectRatio(1f)) {
                val pieChartRadius = size.minDimension / 2
                var startAngle = -90f

                data.entries.forEachIndexed { index, entry ->
                    val sweepAngle = 360 * (entry.value / total)
                    val percentage = (entry.value / total) * 100

                    // Dibujar la porción del pastel
                    drawArc(
                        color = legend[entry.key] ?: Color.Gray,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = true,
                        size = Size(pieChartRadius * 2, pieChartRadius * 2),
                        topLeft = Offset(
                            (size.width - pieChartRadius * 2) / 2,
                            (size.height - pieChartRadius * 2) / 2
                        )
                    )

                    // Calcular la posición del texto
                    val angleInRadians = Math.toRadians((startAngle + sweepAngle / 2).toDouble())
                    val textX = (size.width / 2) + (pieChartRadius / 2) * cos(angleInRadians)
                    val textY = (size.height / 2) + (pieChartRadius / 2) * sin(angleInRadians)

                    // Dibujar el porcentaje
                    drawContext.canvas.nativeCanvas.drawText(
                        "${percentage.toInt()}%",
                        textX.toFloat(),
                        textY.toFloat(),
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            textAlign = android.graphics.Paint.Align.CENTER
                            textSize = 30f
                            isAntiAlias = true
                        }
                    )

                    startAngle += sweepAngle
                }
            }
        }

        // Leyenda
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            legend.forEach { (label, color) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(color)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = label, fontSize = 16.sp)
                }
            }
        }
    }
}
@Composable
fun BarChartScreen(data: List<Pair<String, Float>>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "Sistema Radicular: Cantidad de Repeticiones",
            fontSize = 20.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        // Gráfico de barras
        Canvas(modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)) {
            val barWidth = size.width / (data.size * 2)
            data.forEachIndexed { index, pair ->
                val left = index * barWidth * 2
                val top = size.height - (size.height * (pair.second / 100))
                drawRect(
                    color = Color.Blue,
                    topLeft = Offset(left, top),
                    size = Size(barWidth, size.height - top)
                )
            }
        }
    }
}

@Composable
fun LineChartScreen(data: Map<String, List<Pair<String, Float>>>) {
    if (data.isNotEmpty()) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            factory = { context ->
                LineChart(context).apply {
                    description.isEnabled = false
                    setTouchEnabled(true)
                    setPinchZoom(true)
                    legend.form = Legend.LegendForm.LINE

                    // Set data safely
                    try {
                        val chartData = createLineData(data)
                        if (chartData != null) {
                            this.data = chartData
                            this.invalidate() // Refresh the chart with new data
                        }
                    } catch (e: Exception) {
                        Log.e("LineChartError", "Error setting chart data: ${e.message}")
                    }

                    // Configure X-Axis
                    xAxis.apply {
                        valueFormatter = IndexValueFormatter(getFormattedDates(data))
                        granularity = 1f
                        position = XAxis.XAxisPosition.BOTTOM
                        setDrawGridLines(false)
                    }

                    // Configure Y-Axis
                    axisLeft.apply {
                        axisMinimum = 0f
                        setDrawGridLines(true)
                    }
                    axisRight.isEnabled = false
                }
            }
        )
    } else {
        Text("No data available for chart", Modifier.padding(16.dp))
    }
}

fun createLineData(data: Map<String, List<Pair<String, Float>>>): LineData? {
    val lineDataSets = mutableListOf<ILineDataSet>()
    val colors = listOf(
        Color.Red.toArgb(),
        Color.Green.toArgb(),
        Color.Blue.toArgb(),
        Color.Yellow.toArgb(),
        Color.Cyan.toArgb(),
        Color.Magenta.toArgb()
    )

    // Find the minimum date in milliseconds
    val minDateMillis = data.values
        .flatMap { it.mapNotNull { entry -> parseDate(entry.first) } }
        .minOrNull() ?: return null  // Return null if there are no valid dates

    data.entries.forEachIndexed { index, (bloque, values) ->
        val entries = values.mapNotNull { (fecha, peso) ->
            parseDate(fecha)?.let { dateMillis ->
                val normalizedDate = (dateMillis - minDateMillis) / (24 * 60 * 60 * 1000)
                // Ensure no negative values
                if (normalizedDate >= 0) {
                    Entry(normalizedDate.toFloat(), peso)
                } else {
                    null
                }
            }
        }

        // Ensure there are entries before creating a dataset
        if (entries.isNotEmpty()) {
            val lineDataSet = LineDataSet(entries, bloque).apply {
                lineWidth = 2f
                circleRadius = 4f
                setDrawValues(false)
                color = colors[index % colors.size]
                setCircleColor(colors[index % colors.size])
                mode = LineDataSet.Mode.CUBIC_BEZIER
            }

            lineDataSets.add(lineDataSet)
        }
    }

    return if (lineDataSets.isNotEmpty()) {
        LineData(lineDataSets)
    } else {
        null  // Return null if no valid datasets
    }
}

fun parseDate(date: String): Long? {
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return try {
        format.parse(date)?.time
    } catch (e: ParseException) {
        Log.e("DateParsingError", "Error parsing date: ${e.message}")
        null
    }
}

fun getFormattedDates(data: Map<String, List<Pair<String, Float>>>): List<String> {
    return data.values.flatten().map { it.first }.distinct().sorted()
}


class IndexValueFormatter(formattedDates: List<String>) : ValueFormatter() {
    override fun getFormattedValue(value: Float): String {
        return value.toInt().toString() // Display as integer days
    }
}

class DateValueFormatter : ValueFormatter() {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun getFormattedValue(value: Float): String {
        val date = Date(value.toLong())
        return dateFormat.format(date)
    }
}

@Composable
fun FilterRow(
    bloques: List<String>, // Lista de bloques para el dropdown
    onBloqueSelected: (String) -> Unit,
    onFechaInicioSelected: (String) -> Unit,
    onFechaFinSelected: (String) -> Unit
) {
    var selectedBloque by remember { mutableStateOf(bloques.firstOrNull() ?: "") }
    var showDatePickerInicio by remember { mutableStateOf(false) }
    var showDatePickerFin by remember { mutableStateOf(false) }
    var fechaInicio by remember { mutableStateOf("") }
    var fechaFin by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Dropdown for Bloque
        Box(modifier = Modifier.weight(1f)) {
            var expanded by remember { mutableStateOf(false) }
            TextField(
                value = selectedBloque,
                onValueChange = {},
                label = { Text("Seleccione Bloque") },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true },
                readOnly = true
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                val bloques = listOf("Bloque 1", "Bloque 2", "Bloque 3")
                bloques.forEach { bloque ->
                    DropdownMenuItem(onClick = {
                        selectedBloque = bloque
                        onBloqueSelected(bloque)
                        expanded = false
                    }) {
                        Text(text = bloque)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // DatePicker for Fecha Inicio
        Box(modifier = Modifier.weight(1f)) {
            TextField(
                value = fechaInicio,
                onValueChange = {},
                label = { Text("Fecha Inicio") },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePickerInicio = true },
                readOnly = true
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // DatePicker for Fecha Fin
        Box(modifier = Modifier.weight(1f)) {
            TextField(
                value = fechaFin,
                onValueChange = {},
                label = { Text("Fecha Fin") },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePickerFin = true },
                readOnly = true
            )
        }
    }

    // DatePickerDialog for Fecha Inicio
    if (showDatePickerInicio) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            onDateSelected = { year, month, day ->
                fechaInicio = "$year-${month + 1}-$day"
                onFechaInicioSelected(fechaInicio)
                showDatePickerInicio = false
            },
            onDismissRequest = { showDatePickerInicio = false },
            year = calendar.get(Calendar.YEAR),
            month = calendar.get(Calendar.MONTH),
            day = calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    // DatePickerDialog for Fecha Fin
    if (showDatePickerFin) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            onDateSelected = { year, month, day ->
                fechaFin = "$year-${month + 1}-$day"
                onFechaFinSelected(fechaFin)
                showDatePickerFin = false
            },
            onDismissRequest = { showDatePickerFin = false },
            year = calendar.get(Calendar.YEAR),
            month = calendar.get(Calendar.MONTH),
            day = calendar.get(Calendar.DAY_OF_MONTH)
        )
    }
}

@Composable
fun DatePickerDialog(
    onDateSelected: (Int, Int, Int) -> Unit,
    onDismissRequest: () -> Unit,
    year: Int,
    month: Int,
    day: Int
) {
    val context = LocalContext.current
    val datePickerDialog = android.app.DatePickerDialog(
        context,
        { _, selectedYear, selectedMonth, selectedDay ->
            onDateSelected(selectedYear, selectedMonth, selectedDay)
        },
        year,
        month,
        day
    )

    LaunchedEffect(key1 = Unit) {
        datePickerDialog.show()
    }

    DisposableEffect(key1 = Unit) {
        onDispose {
            datePickerDialog.dismiss()
        }
    }
}
@Composable
fun DropdownMenuItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = MenuDefaults.DropdownMenuItemContentPadding,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .clickable(
                onClick = onClick,
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null
            )
            .then(modifier)
    ) {
        CompositionLocalProvider(LocalContentAlpha provides if (enabled) ContentAlpha.high else ContentAlpha.disabled) {
            Row(
                modifier = Modifier.padding(contentPadding)
            ) {
                ProvideTextStyle(TextStyle.Default) {
                    content()
                }
            }
        }
    }
}
fun updateBlocksPesoPlanta(viewModel: DatosGuardadosViewModel, callback: (Boolean) -> Unit) {
    viewModel.borrarTodosLosDatosPesoPlanta()
    val client = OkHttpClient.Builder()
        .callTimeout(200, TimeUnit.SECONDS)
        .build()

    val request = Request.Builder()
        .url("http://controlgestionguapa.ddns.net:8000/consultor/api_get_indicadores_peso_planta")
        .build()

    var success = false // Booleano para indicar el éxito de la operación

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            e.printStackTrace()

            // Ocultar la barra de progreso en caso de fallo
            viewModel.setProgressVisible(false)
            callback(false) // Llamamos a la función de devolución de llamada con false
        }

        override fun onResponse(call: Call, response: Response) {
            response.use {
                if (!response.isSuccessful) throw IOException("Unexpected code $response")
                val responseString = response.body?.string()
                val cleanedResponseString = responseString?.replace("NaN", "\" \"") // Reemplazar NaN por cadena vacía
                val jsonArray = cleanedResponseString?.let { JSONArray(it) } ?: JSONArray()
                var i = 0
                var saveSuccessful = true
                while (i < jsonArray.length() && saveSuccessful) {
                    val elemento = jsonArray.getJSONObject(i)
                    saveSuccessful = saveDataWebPesoPlanta(elemento = elemento, viewModel = viewModel)
                    i++
                    println("Holi $i")
                }
                success = saveSuccessful // Establecer success basado en si todos los datos se guardaron correctamente
                callback(success) // Llamamos a la función de devolución de llamada con el valor final de success
            }
        }
    })
}
fun saveDataWebPesoPlanta(elemento: JSONObject, viewModel: DatosGuardadosViewModel): Boolean {
    return try {
        val nuevoDato = mapOf(
            "Fecha_Cargue" to fecha_ahora(),
            "Aplicacion" to "peso_planta",
            "bloque" to elemento.optString("bloque", ""),
            "grupo_forza" to elemento.optString("grupo_forza", ""),
            "cuenta_babosa" to elemento.optInt("cuenta_babosa", 0),
            "cuenta_caracol" to elemento.optInt("cuenta_caracol", 0),
            "cuenta_cochinilla" to elemento.optInt("cuenta_cochinilla", 0),
            "cuenta_hormiga" to elemento.optInt("cuenta_hormiga", 0),
            "cuenta_meristemo" to elemento.optInt("cuenta_meristemo", 0),
            "cuenta_ninguno" to elemento.optInt("cuenta_ninguno", 0),
            "cuenta_sinfilido" to elemento.optInt("cuenta_sinfilido", 0),
            "cuenta_tipo_sistema_radicular1" to elemento.optInt("cuenta_tipo_sistema_radicular1", 0),
            "cuenta_tipo_sistema_radicular2" to elemento.optInt("cuenta_tipo_sistema_radicular2", 0),
            "cuenta_tipo_sistema_radicular3" to elemento.optInt("cuenta_tipo_sistema_radicular3", 0),
            "cuenta_sinfilido" to elemento.optInt("cuenta_sinfilido", 0),
            "fecha" to elemento.optString("fecha", ""),
            "promedio_peso" to elemento.optDouble("promedio_peso", 0.0),
        )
        viewModel.agregarDato(nuevoDato)
        true // Retorna true si se guardó exitosamente
    } catch (e: Exception) {
        println("Fallo: $e")
        false // Retorna false si ocurrió un fallo al guardar
    }
}
fun updateBlocksCosecha(viewModel: DatosGuardadosViewModel, callback: (Boolean) -> Unit) {
    viewModel.borrarTodosLosDatosCosecha()
    val client = OkHttpClient.Builder()
        .callTimeout(200, TimeUnit.SECONDS)
        .build()

    val request = Request.Builder()
        .url("http://controlgestionguapa.ddns.net:8000/consultor/api_get_indicadores_verificacion_cosecha")
        .build()

    var success = false // Booleano para indicar el éxito de la operación

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            e.printStackTrace()

            // Ocultar la barra de progreso en caso de fallo
            viewModel.setProgressVisible(false)
            callback(false) // Llamamos a la función de devolución de llamada con false
        }

        override fun onResponse(call: Call, response: Response) {
            response.use {
                if (!response.isSuccessful) throw IOException("Unexpected code $response")
                val responseString = response.body?.string()
                val cleanedResponseString = responseString?.replace("NaN", "\" \"") // Reemplazar NaN por cadena vacía
                val jsonArray = cleanedResponseString?.let { JSONArray(it) } ?: JSONArray()
                var i = 0
                var saveSuccessful = true
                while (i < jsonArray.length() && saveSuccessful) {
                    val elemento = jsonArray.getJSONObject(i)
                    saveSuccessful = saveDataWebCosecha(elemento = elemento, viewModel = viewModel)
                    i++
                    println("Holi $i")
                }
                success = saveSuccessful // Establecer success basado en si todos los datos se guardaron correctamente
                callback(success) // Llamamos a la función de devolución de llamada con el valor final de success
            }
        }
    })
}
fun saveDataWebCosecha(elemento: JSONObject, viewModel: DatosGuardadosViewModel): Boolean {
    return try {
        val nuevoDato = mapOf(
            "Fecha_Cargue" to fecha_ahora(),
            "Aplicacion" to "verificacion_cosecha",
            "bloque" to elemento.optString("bloque", ""),
            "cantidad_observaciones" to elemento.optString("cantidad_observaciones", ""),
            "ausente" to elemento.optInt("ausente", 0),
            "bajo_peso" to elemento.optInt("bajo_peso", 0),
            "coronas" to elemento.optInt("coronas", 0),
            "descarte_entre_camas" to elemento.optInt("descarte_entre_camas", 0),
            "fruta_adelanta" to elemento.optInt("fruta_adelanta", 0),
            "fruta_enferma" to elemento.optInt("fruta_enferma", 0),
            "fruta_joven" to elemento.optInt("fruta_joven", 0),
            "fruta_no_aprovechable" to elemento.optInt("fruta_no_aprovechable", 0),
            "fruta_dejada_por_cosecha" to elemento.optInt("fruts_dejada_por_cosecha", 0),
            "golpe_de_agua" to elemento.optInt("golpe_de_agua", 0),
            "mortalidad" to elemento.optInt("mortalidad", 0),
            "plantas_sin_parir" to elemento.optInt("plantas_sin_parir", 0),
            "quema_de_sol" to elemento.optInt("quema_de_sol", 0),
            "fecha" to elemento.optString("fecha", ""),
        )
        viewModel.agregarDato(nuevoDato)
        true // Retorna true si se guardó exitosamente
    } catch (e: Exception) {
        println("Fallo: $e")
        false // Retorna false si ocurrió un fallo al guardar
    }
}
fun updateBlocksVerificacion(viewModel: DatosGuardadosViewModel, callback: (Boolean) -> Unit) {
    viewModel.borrarTodosLosDatosObservacion()
    val client = OkHttpClient.Builder()
        .callTimeout(200, TimeUnit.SECONDS)
        .build()

    val request = Request.Builder()
        .url("http://controlgestionguapa.ddns.net:8000/consultor/api_get_indicadores_verificacion_observaciones")
        .build()

    var success = false // Booleano para indicar el éxito de la operación

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            e.printStackTrace()

            // Ocultar la barra de progreso en caso de fallo
            viewModel.setProgressVisible(false)
            callback(false) // Llamamos a la función de devolución de llamada con false
        }

        override fun onResponse(call: Call, response: Response) {
            response.use {
                if (!response.isSuccessful) throw IOException("Unexpected code $response")
                val responseString = response.body?.string()
                val cleanedResponseString = responseString?.replace("NaN", "\" \"") // Reemplazar NaN por cadena vacía
                val jsonArray = cleanedResponseString?.let { JSONArray(it) } ?: JSONArray()
                var i = 0
                var saveSuccessful = true
                while (i < jsonArray.length() && saveSuccessful) {
                    val elemento = jsonArray.getJSONObject(i)
                    saveSuccessful = saveDataWebVerificacion(elemento = elemento, viewModel = viewModel)
                    i++
                    println("Holi $i")
                }
                success = saveSuccessful // Establecer success basado en si todos los datos se guardaron correctamente
                callback(success) // Llamamos a la función de devolución de llamada con el valor final de success
            }
        }
    })
}
fun saveDataWebVerificacion(elemento: JSONObject, viewModel: DatosGuardadosViewModel): Boolean {
    return try {
        val nuevoDato = mapOf(
            "Fecha_Cargue" to fecha_ahora(),
            "Aplicacion" to "verificacion_observaciones",
            "bloque" to elemento.optString("bloque", ""),
            "cumple_vias" to elemento.optInt("cumple", 0),
            "cumple_trincho" to elemento.optInt("cumple_trincho", 0),
            "en_mal_estado_vias" to elemento.optInt("en_mal_estado", 0),
            "en_mal_estado_trincho" to elemento.optInt("en_mal_estado_trincho", 0),
            "porc_controlada" to elemento.optInt("porc_controlada", 0),
            "porc_critica" to elemento.optInt("porc_critica", 0),
            "porc_leve" to elemento.optInt("porc_leve", 0),
            "porc_moderada" to elemento.optInt("porc_moderada", 0),
            "sin_puente" to elemento.optInt("sin_puente", 0),
            "sin_trincho" to elemento.optInt("sin_trincho", 0),
            "fecha" to elemento.optString("fecha_muestreo", ""),
        )
        viewModel.agregarDato(nuevoDato)
        true // Retorna true si se guardó exitosamente
    } catch (e: Exception) {
        println("Fallo: $e")
        false // Retorna false si ocurrió un fallo al guardar
    }
}
fun updateBlocksConsulta(viewModel: DatosGuardadosViewModel, callback: (Boolean) -> Unit) {
    viewModel.borrarTodosLosDatosObservacion()
    val client = OkHttpClient.Builder()
        .callTimeout(200, TimeUnit.SECONDS)
        .build()
    val request = Request.Builder()
        .url("http://controlgestionguapa.ddns.net:8000/consultor/api_get_consulta_bloques")
        .build()

    var success = false // Booleano para indicar el éxito de la operación

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            e.printStackTrace()

            // Ocultar la barra de progreso en caso de fallo
            viewModel.setProgressVisible(false)
            callback(false) // Llamamos a la función de devolución de llamada con false
        }

        override fun onResponse(call: Call, response: Response) {
            response.use {
                if (!response.isSuccessful) throw IOException("Unexpected code $response")
                val responseString = response.body?.string()
                val cleanedResponseString = responseString?.replace("NaN", "\" \"") // Reemplazar NaN por cadena vacía
                val jsonArray = cleanedResponseString?.let { JSONArray(it) } ?: JSONArray()
                var i = 0
                var saveSuccessful = true
                while (i < jsonArray.length() && saveSuccessful) {
                    val elemento = jsonArray.getJSONObject(i)
                    saveSuccessful = saveDataWebConsulta(elemento = elemento, viewModel = viewModel)
                    i++
                    println("Holi $i")
                }
                success = saveSuccessful // Establecer success basado en si todos los datos se guardaron correctamente
                callback(success) // Llamamos a la función de devolución de llamada con el valor final de success
            }
        }
    })
}
fun saveDataWebConsulta(elemento: JSONObject, viewModel: DatosGuardadosViewModel): Boolean {
    return try {
        val nuevoDato = mapOf(
            "Fecha_Cargue" to fecha_ahora(),
            "Aplicacion" to "consulta_bloques",
            "bloque" to elemento.optString("bloque", ""),
            "area_bruta" to elemento.optString("area_bruta", ""),
            "area_neta" to elemento.optString("area_neta", ""),
            "fecha_forza" to elemento.optString("fecha_forza", ""),
            "fecha_siembra" to elemento.optString("fecha_siembra", ""),
            "frutas" to elemento.optString("frutas", ""),
            "grupo_forza" to elemento.optString("grupo_forza", ""),
            "grupo_siembra" to elemento.optString("grupo_siembra", ""),
            "max" to elemento.optString("max", ""),
            "min" to elemento.optString("min", ""),
            "peso" to elemento.optString("peso", ""),
            "poblacion" to elemento.optString("peso", ""),
            "rango_semilla" to elemento.optString("peso", ""),
            "razon" to elemento.optString("razon", "")

        )
        viewModel.agregarDato(nuevoDato)
        true // Retorna true si se guardó exitosamente
    } catch (e: Exception) {
        println("Fallo: $e")
        false // Retorna false si ocurrió un fallo al guardar
    }
}
private fun fecha_ahora(): String {
    val currentDateTime = LocalDateTime.now()
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    return currentDateTime.format(formatter)
}

private fun generateExcelFilePesoPlanta(viewModel: DatosGuardadosViewModel, context: Context) {
    GlobalScope.launch(Dispatchers.IO) {
        try {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Datos Guardados")

            val headerRow = sheet.createRow(0)
            val headers = arrayOf(
                "Bloque","Grupo Forza","Fecha Cargue", "Cantidad Babosa", "Cantidad Caracol", "Cantidad Cochinilla", "Cantidad Hormiga",
                "Cantidad Meristemo", "Cantidad Ninguno", "Cantidad Sinfilido", "Cantidad Sistema Radicular 1",
                "Cantidad Sistema Radicular 2", "Cantidad Sistema Radicular 3","Fecha Muestra","Peso Promedio"
            )
            headers.forEachIndexed { index, header ->
                headerRow.createCell(index).setCellValue(header)
            }

            val datosGuardados = viewModel.datosGuardados.filter { it["Aplicacion"] == "peso_planta" }

            datosGuardados.forEachIndexed { rowIndex, dato ->
                val row = sheet.createRow(rowIndex + 1)
                row.createCell(0).setCellValue(dato["bloque"] as? String ?: "")
                row.createCell(1).setCellValue(dato["grupo_forza"] as? String ?: "")
                row.createCell(2).setCellValue(dato["Fecha_Cargue"] as? String ?: "")
                row.createCell(3).setCellValue(dato["cuenta_babosa"] as? Double ?: 0.0)
                row.createCell(4).setCellValue(dato["cuenta_caracol"] as? Double ?: 0.0)
                row.createCell(5).setCellValue(dato["cuenta_cochinilla"] as? Double ?: 0.0)
                row.createCell(6).setCellValue(dato["cuenta_hormiga"] as? Double ?: 0.0)
                row.createCell(7).setCellValue(dato["cuenta_meristemo"] as? Double ?: 0.0)
                row.createCell(8).setCellValue(dato["cuenta_ninguno"] as? Double ?: 0.0)
                row.createCell(9).setCellValue(dato["cuenta_sinfilido"] as? Double ?: 0.0)
                row.createCell(10).setCellValue(dato["cuenta_tipo_sistema_radicular1"] as? Double ?: 0.0)
                row.createCell(11).setCellValue(dato["cuenta_tipo_sistema_radicular2"] as? Double ?: 0.0)
                row.createCell(12).setCellValue(dato["cuenta_tipo_sistema_radicular3"] as? Double ?: 0.0)
                row.createCell(13).setCellValue(dato["fecha"] as? String ?: "")
                row.createCell(14).setCellValue(dato["promedio_peso"] as? Double ?: 0.0)
            }

            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "DatosGuardados.xlsx")
            FileOutputStream(file).use { outputStream ->
                workbook.write(outputStream)
            }
            workbook.close()

            // Mostrar un mensaje confirmando que el archivo ha sido guardado
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Archivo Excel generado y guardado en Documentos", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error al generar el archivo Excel", Toast.LENGTH_LONG).show()
            }
        }
    }
}
@Composable
fun startScreen(navController: NavController, viewModel: DatosGuardadosViewModel, context: Context,username: String) {
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val datos = viewModel.datosGuardados.filter { it["Origen"] != "Web" }
    var showDialog by remember { mutableStateOf(false) }
    var showDialogNew by remember { mutableStateOf(false) }
    var showDialogMuestras by remember { mutableStateOf(false) }
    if (showDialogMuestras) {
        AlertDialog(
            onDismissRequest = {
                // Dismiss the dialog if the user clicks outside of it or presses the back button
                showDialogMuestras = false
            },
            title = {
                Text(text = "¿Está seguro de eliminar todas las muestras?")
                //TODO
                // "¿Está seguro de eliminar el bloque $bloque con fecha muestra $fecha_muestra y $cantidad de registros?"
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDialogMuestras = false
                        viewModel.borrarTodosLosDatosGuardadosNoWeb()
                    }
                ) {
                    Text(text = "Aceptar")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showDialogMuestras = false
                    }
                ) {
                    Text(text = "Cancelar")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        Text(
            text = "Agricola Guapa SAS \n Control Gestión",
            fontSize = 30.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(40.dp))
        Button(
            onClick = {
                navController.navigate("startForms/$username")
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text(text = "Peso Planta")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                navController.navigate("startFormsCosecha/$username")
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text(text = "Verificación Cosecha")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                navController.navigate("startFormsVerificacion/$username")
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text(text = "Verificación Observaciones")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                navController.navigate("consultaBloques/$username")
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text(text = "General Bloques")
        }
        Spacer(modifier = Modifier.height(16.dp))

        Image(
            painter = painterResource(id = R.drawable.logi),
            contentDescription = null,
            modifier = Modifier.size(80.dp)
        )
        Text(
            text = "Powered by Guapa\nVersión 2.0",
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}
