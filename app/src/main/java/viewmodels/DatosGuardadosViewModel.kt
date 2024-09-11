package viewmodels

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class DatosGuardadosViewModel (private val context: Context) : ViewModel() {
    private val sharedPreferences = context.getSharedPreferences("datos_guardados", Context.MODE_PRIVATE)
    private val gson = Gson()
    val datosGuardados = obtenerDatosGuardados()
    private val _progress = mutableStateOf(0f)

    // Método para obtener el progreso actual
    fun getProgress(): Float {
        return _progress.value
    }

    // Método para establecer el progreso
    fun setProgress(progress: Float) {
        _progress.value = progress
    }

    // Método para mostrar u ocultar la barra de progreso
    private val _progressVisible = mutableStateOf(false)

    fun setProgressVisible(visible: Boolean) {
        _progressVisible.value = visible
    }

    fun isProgressVisible(): Boolean {
        return _progressVisible.value
    }
    fun agregarDato(dato: Map<String, Any>) {
        datosGuardados.add(dato)
        guardarDatosGuardados(datosGuardados)
    }

    fun obtenerDatosGuardados(): MutableList<Map<String, Any>> {
        val datosGuardadosJson = sharedPreferences.getString("datos_guardados", null)
        return if (datosGuardadosJson != null) {
            val type = object : TypeToken<MutableList<Map<String, Any>>>() {}.type
            gson.fromJson(datosGuardadosJson, type)
        } else {
            mutableListOf()
        }
    }

    private fun guardarDatosGuardados(datosGuardados: MutableList<Map<String, Any>>) {
        val datosGuardadosJson = gson.toJson(datosGuardados)
        sharedPreferences.edit().putString("datos_guardados", datosGuardadosJson).apply()
    }

    fun obtenerPromedioPesoPorBloque(bloque: String): Float {
        val datosPorBloque = datosGuardados.filter { it["bloque"] == bloque && it["Origen"] != "Web" }
        // Sumar todos los pesos
        val totalPeso = datosPorBloque.sumOf { (it["peso"] as? String)?.toDoubleOrNull() ?: 0.0 }
        println("peso $totalPeso")

        // Calcular el promedio del peso
        return if (datosPorBloque.isNotEmpty()) {
            totalPeso.toFloat() / datosPorBloque.size
        } else {
            0f // Devuelve 0 si no hay registros para ese bloque o si todos son de origen web
        }
    }
    fun borrarTodosLosDatosGuardados() {
        datosGuardados.removeAll { it["Origen"] == "Web" }
        guardarDatosGuardados(datosGuardados)  // Asegúrate de guardar los cambios
    }
    fun borrarTodosLosDatosPesoPlanta() {
        datosGuardados.removeAll { it["Aplicacion"] == "peso_planta" }
        guardarDatosGuardados(datosGuardados)  // Asegúrate de guardar los cambios
    }
    fun borrarTodosLosDatosCosecha() {
        datosGuardados.removeAll { it["Aplicacion"] == "verificacion_cosecha" }
        guardarDatosGuardados(datosGuardados)  // Asegúrate de guardar los cambios
    }
    fun borrarTodosLosDatosObservacion() {
        datosGuardados.removeAll { it["Aplicacion"] == "verificacion_observaciones" }
        guardarDatosGuardados(datosGuardados)  // Asegúrate de guardar los cambios
    }
    fun borrarTodosLosDatosGuardadosNoWeb() {
        datosGuardados.removeAll { it["Origen"] != "Web" }
        guardarDatosGuardados(datosGuardados)  // Asegúrate de guardar los cambios
    }
    fun borrarDatoPorFechaOrigenYBloque(fecha: Any?, bloque: Any?) {
        val datoABorrar = datosGuardados.find { it["fecha"] == fecha && it["bloque"] == bloque && it["Origen"] != "Web" }
        datoABorrar?.let {
            datosGuardados.remove(it)
            guardarDatosGuardados(datosGuardados)
        }
    }
    fun borrarDatoPorBloque(bloque: String) {
        datosGuardados.removeAll { it["bloque"] == bloque && it["Origen"] != "Web" }
        guardarDatosGuardados(datosGuardados)
    }
    fun eliminarDato(dato: Map<String, Any>) {
        datosGuardados.remove(dato)
        guardarDatosGuardados(datosGuardados)
    }

    fun eliminarDuplicados() {
        TODO("Not yet implemented")
    }
}