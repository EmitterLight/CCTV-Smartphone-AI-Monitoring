package com.example.phonecamsender
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import android.widget.ImageButton
import android.view.View

class MainActivity : AppCompatActivity() {
    private lateinit var blackScreen: View
    private lateinit var previewView: PreviewView
    private lateinit var statusText: TextView
    private lateinit var overlayText: TextView
    private val okHttp = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val prefs by lazy { getSharedPreferences("phonecam", MODE_PRIVATE) }
    private val gate = CaptureGate()
    @Volatile
    private var baseUrl: String? = null
    private var dbgEnabledFlag = false
    private var camProviderRef: ProcessCameraProvider? = null
    private var boundPreview: Preview? = null
    private val cameraStart = CameraStartGuard()
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val debugUpdater = object : Runnable {
        override fun run() {
            if (dbgEnabledFlag) {
                overlayText.text = generateDebugText()
                overlayText.visibility = android.view.View.VISIBLE
                mainHandler.postDelayed(this, 500)
            } else {
                overlayText.visibility = android.view.View.GONE
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        overlayText = findViewById(R.id.overlayText)
        previewView = findViewById(R.id.previewView)
        statusText = findViewById(R.id.statusText)
        blackScreen = findViewById(R.id.blackScreen)

        setStatus("Status: Connecting...")
        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        connectAndStart()
    }
    override fun onResume() {
        super.onResume()
        applySettingsFromPrefs()
    }
    private fun applySettingsFromPrefs() {
        baseUrl = prefs.getString("baseUrl", null)
        dbgEnabledFlag = prefs.getBoolean("showDebug", false)

        val hidePreviewFlag = prefs.getBoolean("hidePreview", false)
        val stopCameraFlag = prefs.getBoolean("stopCamera", false)

        GateSync.apply(gate, stopCameraFlag)
        mainHandler.removeCallbacks(debugUpdater)
        mainHandler.post(debugUpdater)

        if (!hasCameraPermission()) {
            setStatus("Status: Camera permission required")
            return
        }

        if (gate.userStopped) {
            previewView.visibility = View.GONE
            blackScreen.visibility = View.VISIBLE
            setStatus("Status: Camera stopped")
            return
        }

        CaptureService.current()?.applySettings()
        CaptureService.start(this, gate)
        bindPreview()

        if (hidePreviewFlag) {
            // Keep the CameraX preview surface alive; the opaque overlay hides it.
            previewView.visibility = View.VISIBLE
            blackScreen.visibility = View.VISIBLE
            setStatus("Status: Preview hidden")
        } else {
            previewView.visibility = View.VISIBLE
            blackScreen.visibility = View.GONE

            val url = baseUrl
            if (url == null) setStatus("Status: Not connected")
            else setStatus("Status: Connected ${NetworkDiscover.baseUrlToInput(url)}")
        }
    }

    // ==========================
    // Connection process
    // ==========================
    private fun connectAndStart() {
        val savedBaseUrl = prefs.getString("baseUrl", null)

        if (!savedBaseUrl.isNullOrBlank()) {
            setStatus("Status: Testing saved connection...")
            verifyPing(savedBaseUrl) { ok ->
                if (ok) {
                    onServerReady(savedBaseUrl)
                } else {
                    setStatus("Status: Saved server unavailable")
                    showManualInputDialog()
                }
            }
        } else {
            setStatus("Status: Server address required")
            showManualInputDialog()
        }
    }

    private fun onServerReady(url: String) {
        baseUrl = url
        prefs.edit().putString("baseUrl", url).apply()

        runOnUiThread {
            setStatus("Status: Connected ${NetworkDiscover.baseUrlToInput(url)}")
            Toast.makeText(this, "Connected", Toast.LENGTH_SHORT).show()

            if (hasCameraPermission()) {
                applySettingsFromPrefs()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CAMERA),
                    1001
                )
            }
        }
    }

    // ==========================
    // Manual input pop-up window
    // ==========================

    private fun showManualInputDialog() {
        runOnUiThread {
            val input = android.widget.EditText(this)
            input.hint = "e.g. 192.168.1.10:8000"
            input.setText(prefs.getString("serverInput", "") ?: "")

            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Enter Sentinel server address")
                .setMessage("Run server.py on the PC, then enter the CamFlow address shown in its startup output.")
                .setView(input)
                .setCancelable(false)
                .setPositiveButton("Connect") { _, _ ->
                    val text = input.text.toString().trim()
                    val normalized = NetworkDiscover.inputToBaseUrlOrNull(text)

                    if (normalized == null) {
                        Toast.makeText(
                            this,
                            "Invalid format. Please enter IP or IP:port.",
                            Toast.LENGTH_LONG
                        ).show()
                        showManualInputDialog()
                        return@setPositiveButton
                    }

                    verifyPing(normalized) { ok ->
                        if (ok) {
                            prefs.edit()
                                .putString("serverInput", text)
                                .putString("baseUrl", normalized)
                                .apply()

                            onServerReady(normalized)
                        } else {
                            setStatus("Status: Connection failed")
                            Toast.makeText(
                                this,
                                "Still failed. Make sure your phone and PC are on the same network.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
                .show()
        }
    }

    // ==========================
    // Network testing
    // ==========================

    private fun verifyPing(url: String, callback: (Boolean) -> Unit) {
        val request = try {
            Request.Builder()
                .url("${url.removeSuffix("/")}/ping")
                .get()
                .build()
        } catch (_: IllegalArgumentException) {
            callback(false)
            return
        }

        okHttp.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false)
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    callback(it.isSuccessful)
                }
            }
        })
    }

    // ==========================
    // Foreground preview only
    // ==========================

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 1001) {
            val granted = grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (granted) {
                applySettingsFromPrefs()
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun bindPreview() {
        if (isDestroyed || isFinishing) return
        val ticket = cameraStart.begin() ?: return
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (!cameraStart.isCurrent(ticket) || isDestroyed || isFinishing) return@addListener
            try {
                val provider = future.get()
                camProviderRef = provider
                boundPreview?.let { provider.unbind(it) }
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                boundPreview = preview
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview)
                cameraStart.complete(ticket, true)
            } catch (e: Exception) {
                cameraStart.complete(ticket, false)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun setStatus(text: String) {
        runOnUiThread { statusText.text = text }
    }
    override fun onPause() {
        super.onPause()
        mainHandler.removeCallbacks(debugUpdater)
    }
    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(debugUpdater)
        // Stopping capture requires the explicit stopCamera setting, not activity destruction.
        cameraStart.stop()
        camProviderRef?.let { provider -> boundPreview?.let { provider.unbind(it) } }
    }
    private fun generateDebugText(): String {
        val service = CaptureService.current()
        val url = baseUrl?.let { NetworkDiscover.baseUrlToInput(it) } ?: "Not connected"
        val hidePreviewFlag = prefs.getBoolean("hidePreview", false)
        val stopCameraFlag = prefs.getBoolean("stopCamera", false)

        val qualityLabel = getImageQualityLabel()
        val qualityValue = getImageQualityValue()

        val uploadRateLabel = getUploadRateLabel()
        val uploadIntervalMs = getUploadIntervalMs()

        val resolutionText = service?.resolutionStatus?.summary(getResolutionLabel())
            ?: "Resolution preference: ${getResolutionLabel()}\nFrame size: waiting for frame"

        val mode = when {
            stopCameraFlag -> "Camera stopped"
            hidePreviewFlag -> "Preview hidden"
            else -> "Normal"
        }

        val serviceStatus = service?.status ?: "service not started"
        return "CamFlow ${BuildConfig.VERSION_NAME} build ${BuildConfig.VERSION_CODE}\n" +
                "Server: $url\nMode: $mode\n$resolutionText\n" +
                "Upload rate: $uploadRateLabel (${uploadIntervalMs}ms)\n" +
                "Quality: $qualityLabel ($qualityValue)\n" +
                "Upload status: $serviceStatus\n" +
                "Encode: ${service?.lastEncodeMs ?: 0L}ms | limit: ${UploadHttp.CALL_TIMEOUT_MS}ms\n" +
                "Uploads: ok=${service?.uploadSuccessCount?.get() ?: 0} " +
                "failed=${service?.uploadFailureCount?.get() ?: 0} " +
                "dropped=${service?.pipeline?.dropped ?: 0}\n" +
                "Gate: service=${gate.serviceRunning} userStopped=${gate.userStopped}"
    }

    private fun getImageQualityLabel(): String {
        return prefs.getString("imageQualityLabel", "Medium") ?: "Medium"
    }

    private fun getImageQualityValue(): Int {
        return when (getImageQualityLabel()) {
            "Low" -> 35
            "High" -> 75
            else -> 55
        }
    }

    private fun getUploadRateLabel(): String {
        return prefs.getString("uploadRateLabel", "High") ?: "High"
    }

    private fun getUploadIntervalMs(): Long {
        return when (getUploadRateLabel()) {
            "Low" -> 500L
            "Medium" -> 250L
            else -> 120L
        }
    }

    private fun getResolutionLabel(): String {
        return prefs.getString("resolutionLabel", "Medium") ?: "Medium"
    }

}
