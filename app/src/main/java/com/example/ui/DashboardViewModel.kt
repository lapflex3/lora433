package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

enum class ConnectionState {
    OFFLINE,
    CHECKING,
    CONNECTED
}

class DashboardViewModel(
    application: Application,
    private val repository: DashboardRepository
) : AndroidViewModel(application) {

    // --- State Observables ---

    val apiIpAddress = MutableStateFlow("192.168.1.15")
    val apiPort = MutableStateFlow("3000")
    val jwtToken = MutableStateFlow("transceiver_debug_session_token")
    val connectionState = MutableStateFlow(ConnectionState.OFFLINE)

    val selectedFrequency = MutableStateFlow(433.92)
    val selectedLoraSF = MutableStateFlow(7)
    val selectedLoraBW = MutableStateFlow(125.0)

    val isRfLearningActive = MutableStateFlow(false)
    val isLoraLearningActive = MutableStateFlow(false)

    val rfSearchQuery = MutableStateFlow("")
    val loraSearchQuery = MutableStateFlow("")

    // Raw databases lists
    val rfCodesList: StateFlow<List<RfCode>> = rfSearchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) repository.allRfCodes
            else repository.searchRfCodes(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val loraCodesList: StateFlow<List<LoraCode>> = loraSearchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) repository.allLoraCodes
            else repository.searchLoraCodes(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val connectionLogs: StateFlow<List<ConnectionLog>> = repository.connectionLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Live Unsaved Captures
    val liveCapturedRfSignal = MutableStateFlow<RfCode?>(null)
    val liveCapturedLoraPacket = MutableStateFlow<LoraCode?>(null)

    // Simulation Threads
    private var rfSimJob: Job? = null
    private var loraSimJob: Job? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    init {
        // Hydrate initial history log
        viewModelScope.launch {
            repository.insertConnectionLog(
                ConnectionLog(event = "RF & LoRa Dashboard Console Ready", deviceId = "DashCompanion")
            )
        }
    }

    // --- RF Operations ---

    fun toggleRfLearning() {
        val active = !isRfLearningActive.value
        isRfLearningActive.value = active
        if (active) {
            isLoraLearningActive.value = false
            stopLoraSimulation()
            startRfSimulation()
            triggerLearnModeOnServer("rf", true)
            logEvent("Command SENT: START 433MHz ASK learning scan", "DashCompanion")
        } else {
            stopRfSimulation()
            triggerLearnModeOnServer("rf", false)
            logEvent("Command SENT: STOP 433MHz ASK learning scan", "DashCompanion")
        }
    }

    fun insertRfCode(name: String, rawCode: String, protocol: String) {
        viewModelScope.launch {
            val code = RfCode(
                name = name,
                rawCode = rawCode,
                protocol = protocol,
                frequency = selectedFrequency.value
            )
            repository.insertRfCode(code)
            logEvent("Database entry CREATED: $name (RF: $rawCode)", "UserAction")
            
            // Push to server if online
            if (connectionState.value == ConnectionState.CONNECTED) {
                postRfToServer(code)
            }
        }
    }

    fun updateRfCodeName(code: RfCode, newName: String) {
        viewModelScope.launch {
            val updated = code.copy(name = newName)
            repository.updateRfCode(updated)
            logEvent("Database entry UPDATED: Name -> $newName", "UserAction")
        }
    }

    fun deleteRfCode(code: RfCode) {
        viewModelScope.launch {
            repository.deleteRfCode(code)
            logEvent("Database entry DELETED: ${code.name}", "UserAction")
        }
    }

    fun triggerReplayRf(code: RfCode) {
        viewModelScope.launch {
            logEvent("TX REQUEST: RF Replay [${code.rawCode}] on ${code.frequency}MHz", "DashCompanion")
            
            // Send replay to hardware via REST API if online
            if (connectionState.value == ConnectionState.CONNECTED) {
                sendReplayToServer("rf", code.id)
            } else {
                // Simulate tx pulse
                delay(800)
                logEvent("TX COMPLETED: Pulse transmitted [OOK Mod, Delay: 320us]", "Sim_Emitter")
            }
        }
    }

    // --- LoRa Operations ---

    fun toggleLoraLearning() {
        val active = !isLoraLearningActive.value
        isLoraLearningActive.value = active
        if (active) {
            isRfLearningActive.value = false
            stopRfSimulation()
            startLoraSimulation()
            triggerLearnModeOnServer("lora", true)
            logEvent("Command SENT: START LoRa transceiver scanning", "DashCompanion")
        } else {
            stopLoraSimulation()
            triggerLearnModeOnServer("lora", false)
            logEvent("Command SENT: STOP LoRa transceiver scanning", "DashCompanion")
        }
    }

    fun insertLoraCode(name: String, payload: String) {
        viewModelScope.launch {
            val code = LoraCode(
                name = name,
                payload = payload,
                frequency = selectedFrequency.value,
                sf = selectedLoraSF.value,
                bw = selectedLoraBW.value,
                crc = true
            )
            repository.insertLoraCode(code)
            logEvent("Database entry CREATED: $name (Payload: $payload)", "UserAction")
            
            // Push to server if online
            if (connectionState.value == ConnectionState.CONNECTED) {
                postLoraToServer(code)
            }
        }
    }

    fun updateLoraCodeName(code: LoraCode, newName: String) {
        viewModelScope.launch {
            val updated = code.copy(name = newName)
            repository.updateLoraCode(updated)
            logEvent("Database entry UPDATED: Name -> $newName", "UserAction")
        }
    }

    fun deleteLoraCode(code: LoraCode) {
        viewModelScope.launch {
            repository.deleteLoraCode(code)
            logEvent("Database entry DELETED: ${code.name}", "UserAction")
        }
    }

    fun triggerReplayLora(code: LoraCode) {
        viewModelScope.launch {
            logEvent("TX REQUEST: LoRa transmit [${code.payload}] (SF${code.sf}, ${code.bw}kHz)", "DashCompanion")
            
            if (connectionState.value == ConnectionState.CONNECTED) {
                sendReplayToServer("lora", code.id)
            } else {
                // Simulate tx pulse
                delay(1200)
                logEvent("TX COMPLETED: Payload modulated & packet emitted", "Sim_LoRaTransmitter")
            }
        }
    }

    // --- Core Logging & Helpers ---

    fun logEvent(event: String, deviceId: String) {
        viewModelScope.launch {
            val log = ConnectionLog(event = event, deviceId = deviceId)
            repository.insertConnectionLog(log)
            
            // Also sink to Express server if connected
            if (connectionState.value == ConnectionState.CONNECTED && deviceId == "UserAction") {
                postLogToServer(event, deviceId)
            }
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearConnectionLogs()
            repository.insertConnectionLog(
                ConnectionLog(event = "Activity Console cleared.", deviceId = "DashCompanion")
            )
        }
    }

    fun exportLogsAsCsv(): String {
        val logs = connectionLogs.value
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val csv = StringBuilder("Timestamp,Device_ID,Event_Log\n")
        logs.forEach { log ->
            val d = format.format(Date(log.timestamp))
            csv.append("\"$d\",\"${log.deviceId}\",\"${log.event.replace("\"", "\"\"")}\"\n")
        }
        return csv.toString()
    }

    fun exportLogsAsJson(): String {
        val logs = connectionLogs.value
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        val json = java.lang.StringBuilder("[\n")
        logs.forEachIndexed { i, log ->
            val timestampStr = format.format(Date(log.timestamp))
            json.append("  {\n")
            json.append("    \"id\": ${log.id},\n")
            json.append("    \"deviceId\": \"${log.deviceId}\",\n")
            json.append("    \"event\": \"${log.event.replace("\"", "\\\"")}\",\n")
            json.append("    \"timestamp\": \"$timestampStr\"\n")
            json.append("  }${if (i < logs.size - 1) "," else ""}\n")
        }
        json.append("]")
        return json.toString()
    }

    // --- Simulation Engines ---

    private fun startRfSimulation() {
        rfSimJob = viewModelScope.launch {
            val randomNames = listOf("Garage Door", "Ceiling Fan", "Smart Outlet", "Car Keyfob", "Lamp Switch")
            val protocols = listOf("1", "2", "3")
            while (isRfLearningActive.value) {
                delay(6000 + Random.nextLong(1000, 3000))
                val rawCodeValue = Random.nextInt(1000000, 9999999).toString()
                val protocol = protocols.random()
                val captured = RfCode(
                    name = "Captured Code " + Random.nextInt(100, 999), 
                    rawCode = rawCodeValue,
                    protocol = protocol,
                    frequency = selectedFrequency.value
                )
                liveCapturedRfSignal.value = captured
                logEvent("SIGNAL RX: Captured OOK [Code: $rawCodeValue, Proto: $protocol] on ${selectedFrequency.value} MHz", "433_RX_Module")
            }
        }
    }

    private fun stopRfSimulation() {
        rfSimJob?.cancel()
        rfSimJob = null
    }

    private fun startLoraSimulation() {
        loraSimJob = viewModelScope.launch {
            val telemetryPayloads = listOf(
                "{\"temp\":24.2,\"hum\":65,\"bat\":98}",
                "{\"lat\":-37.8136,\"lng\":144.9631,\"alt\":45}",
                "{\"node\":12,\"status\":\"OK\",\"rssi\":-80}",
                "{\"motion\":true,\"counter\":152}",
                "RAIN_SENSOR=435,WATER_MV=2100"
            )
            while (isLoraLearningActive.value) {
                delay(8000 + Random.nextLong(2000, 4000))
                val textPayload = telemetryPayloads.random()
                val rssi = Random.nextInt(-105, -45)
                val snr = Random.nextDouble(5.0, 11.0)
                val captured = LoraCode(
                    name = "Captured Packet " + Random.nextInt(10, 99),
                    payload = textPayload,
                    frequency = selectedFrequency.value,
                    sf = selectedLoraSF.value,
                    bw = selectedLoraBW.value,
                    crc = true,
                    rssi = rssi,
                    snr = snr
                )
                liveCapturedLoraPacket.value = captured
                logEvent("LORA RX: Signal modulated [SF: ${captured.sf}, BW: ${captured.bw}kHz, RSSI: $rssi dBm, SNR: ${String.format(Locale.getDefault(), "%.1f", snr)} dB]", "SX1278_Module")
            }
        }
    }

    private fun stopLoraSimulation() {
        loraSimJob?.cancel()
        loraSimJob = null
    }

    fun dismissRfCapture() {
        liveCapturedRfSignal.value = null
    }

    fun dismissLoraCapture() {
        liveCapturedLoraPacket.value = null
    }

    // --- REST API Client Implementations ---

    fun testServerConnection() {
        viewModelScope.launch {
            connectionState.value = ConnectionState.CHECKING
            logEvent("TESTING CONNECTION to http://${apiIpAddress.value}:${apiPort.value}", "DashCompanion")
            
            val success = checkEndpointStatus()
            if (success) {
                connectionState.value = ConnectionState.CONNECTED
                logEvent("CONNECTION SUCCESSFUL. Client synchronized with remote express backend server.", "DashCompanion")
            } else {
                connectionState.value = ConnectionState.OFFLINE
                logEvent("CONNECTION FAILED. Please review Node.js configuration or server credentials.", "DashCompanion")
            }
        }
    }

    private suspend fun checkEndpointStatus(): Boolean {
        return kotlin.runCatching {
            val url = "http://${apiIpAddress.value}:${apiPort.value}/history"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
                
            httpClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        }.getOrDefault(false)
    }

    private fun triggerLearnModeOnServer(type: String, active: Boolean) {
        if (connectionState.value != ConnectionState.CONNECTED) return
        viewModelScope.launch {
            kotlin.runCatching {
                val url = "http://${apiIpAddress.value}:${apiPort.value}/learn/$type"
                val json = "{\"active\": $active}"
                val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .header("Authorization", "Bearer ${jwtToken.value}")
                    .build()
                httpClient.newCall(request).execute().use { res ->
                    if (res.isSuccessful) {
                        logEvent("REST OK: Triggered server $type status ($active)", "DashCompanion")
                    }
                }
            }
        }
    }

    private fun sendReplayToServer(type: String, id: Int) {
        viewModelScope.launch {
            kotlin.runCatching {
                val url = "http://${apiIpAddress.value}:${apiPort.value}/replay/$type/$id"
                val body = "".toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .header("Authorization", "Bearer ${jwtToken.value}")
                    .build()
                httpClient.newCall(request).execute().use { res ->
                    if (res.isSuccessful) {
                        logEvent("REST OK: Replay action executed completely.", "DashCompanion")
                    } else {
                        logEvent("REST ERR: Server returned status code ${res.code}", "DashCompanion")
                    }
                }
            }.onFailure {
                logEvent("REST FAILED: ${it.message}", "DashCompanion")
            }
        }
    }

    private fun postRfToServer(code: RfCode) {
        viewModelScope.launch {
            kotlin.runCatching {
                val url = "http://${apiIpAddress.value}:${apiPort.value}/codes/rf"
                val json = """
                    {
                      "name": "${code.name}",
                      "raw_code": "${code.rawCode}",
                      "protocol": "${code.protocol}",
                      "frequency": ${code.frequency}
                    }
                """.trimIndent()
                val body = json.toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .header("Authorization", "Bearer ${jwtToken.value}")
                    .build()
                httpClient.newCall(request).execute().close()
            }
        }
    }

    private fun postLoraToServer(code: LoraCode) {
        viewModelScope.launch {
            kotlin.runCatching {
                val url = "http://${apiIpAddress.value}:${apiPort.value}/codes/lora"
                val json = """
                    {
                      "name": "${code.name}",
                      "payload": "${code.payload}",
                      "frequency": ${code.frequency},
                      "SF": ${code.sf},
                      "BW": ${code.bw},
                      "CRC": ${code.crc}
                    }
                """.trimIndent()
                val body = json.toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .header("Authorization", "Bearer ${jwtToken.value}")
                    .build()
                httpClient.newCall(request).execute().close()
            }
        }
    }

    private fun postLogToServer(event: String, deviceId: String) {
        viewModelScope.launch {
            kotlin.runCatching {
                val url = "http://${apiIpAddress.value}:${apiPort.value}/history"
                val json = """
                    {
                      "event": "$event",
                      "device_id": "$deviceId"
                    }
                """.trimIndent()
                val body = json.toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()
                httpClient.newCall(request).execute().close()
            }
        }
    }
}

class DashboardViewModelFactory(
    private val application: Application,
    private val repository: DashboardRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DashboardViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
