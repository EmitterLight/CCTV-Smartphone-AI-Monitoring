package com.example.phonecamsender

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/** Owns the CameraX binding and the capture pipeline; survives screen off. */
class CaptureService : Service(), LifecycleOwner {
    companion object {
        @Volatile private var instance: CaptureService? = null
        @Volatile private var sharedGate: CaptureGate? = null

        fun start(context: Context, gate: CaptureGate) {
            sharedGate = gate
            gate.serviceRunning = true
            ContextCompat.startForegroundService(context, Intent(context, CaptureService::class.java))
        }

        fun stop(context: Context) {
            sharedGate?.serviceRunning = false
            context.stopService(Intent(context, CaptureService::class.java))
        }

        /** LocalBinder-style reference for the UI. */
        fun current(): CaptureService? = instance
    }

    val gate: CaptureGate = sharedGate ?: CaptureGate()
    val slots = UploadSlots(2)
    val planner = ReconnectPlanner(2000L, 30000L)
    val resolutionStatus = ResolutionStatus()

    @Volatile var status: String = "Not started"
    @Volatile var lastEncodeMs: Long = 0L
    val uploadSuccessCount = AtomicLong(0)
    val uploadFailureCount = AtomicLong(0)

    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry

    override fun onBind(intent: Intent?): IBinder? = null

    private val prefs by lazy { getSharedPreferences("phonecam", MODE_PRIVATE) }
    private val uploadHttp = UploadHttp.client()
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val activeUploads = ConcurrentHashMap<Call, UploadTrace>()
    private val source = AndroidNotificationSource(this)
    private var analyzedProxy: ImageProxy? = null
    var pipeline: FramePipeline? = null
    private var boundAnalysis: ImageAnalysis? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "camflow:capture")
        wakeLock?.acquire(30 * 60 * 1000L)
        pipeline = newPipeline()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = CaptureNotifier.build(source, status)
        if (notification != null) {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    CaptureNotifier.NOTIFICATION_ID,
                    notification as Notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                )
            } else {
                startForeground(CaptureNotifier.NOTIFICATION_ID, notification as Notification)
            }
        }
        applySettings()
        if (hasCameraPermission()) bindCamera()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        gate.serviceRunning = false
        ProcessCameraProvider.getInstance(this).addListener({
            try {
                val provider = ProcessCameraProvider.getInstance(this).get()
                boundAnalysis?.let { provider.unbind(it) }
            } catch (_: Exception) { }
        }, ContextCompat.getMainExecutor(this))
        cameraExecutor.shutdown()
        wakeLock?.release()
        slots.stopAccepting()
        activeUploads.keys.forEach { it.cancel() }
    }

    /** Settings can change while the service runs; the gate is updated without rebinding the camera. */
    fun applySettings() {
        val p = pipeline ?: return
        if (getUploadIntervalMs() != p.intervalMs) pipeline = newPipeline()
    }

    private fun newPipeline(): FramePipeline = FramePipeline(
        gate,
        slots,
        getUploadIntervalMs(),
        { _, slot -> encodeAndUpload(slot) },
        { SystemClock.elapsedRealtime() }
    )

    private fun encodeAndUpload(slot: UploadSlots.Lease) {
        val proxy = analyzedProxy
        if (proxy == null) { slot.close(); return }
        try {
            val started = SystemClock.elapsedRealtime()
            val jpeg = ImageUtil.yuvToJpeg(proxy, getImageQualityValue())
            lastEncodeMs = SystemClock.elapsedRealtime() - started
            uploadFrame(jpeg, slot)
        } catch (e: Exception) {
            slot.close()
            throw e
        }
    }

    private fun uploadFrame(jpegBytes: ByteArray, slot: UploadSlots.Lease) {
        val url = baseUrl()
        if (url == null) { slot.close(); return }
        val token = DeviceCredentials.tokenFor(applicationContext, url)
        if (token == null) {
            status = "Pairing required — open Settings"
            slot.close()
            return
        }

        val trace = UploadTrace(jpegBytes.size)
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", "frame.jpg", jpegBytes.toRequestBody("image/jpeg".toMediaType()))
            .build()

        val request = try {
            Request.Builder()
                .url("${url.removeSuffix("/")}/upload")
                .header("Authorization", "Bearer $token")
                .header("X-CamFlow-Upload-ID", trace.id)
                .tag(UploadTrace::class.java, trace)
                .post(body)
                .build()
        } catch (_: IllegalArgumentException) {
            uploadFailureCount.incrementAndGet()
            status = "Invalid server URL"
            slot.close()
            return
        }

        val call = uploadHttp.newCall(request)
        activeUploads[call] = trace
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                uploadFailureCount.incrementAndGet()
                status = "Failed: ${e.javaClass.simpleName}"
                activeUploads.remove(call)
                slot.close()
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (response.isSuccessful) {
                        uploadSuccessCount.incrementAndGet()
                        status = "OK (${response.code})"
                        planner.reset()
                        persistUploadSuccess()
                    } else {
                        uploadFailureCount.incrementAndGet()
                        status = "HTTP ${response.code}"
                        if (response.code == 401 || response.code == 403) {
                            DeviceCredentials.clearIfCurrent(applicationContext, url, token)
                            status = "Pairing revoked or invalid — pair again in Settings"
                        }
                    }
                } finally {
                    try { response.close() } finally {
                        activeUploads.remove(call)
                        slot.close()
                    }
                }
            }
        })
    }

    @Synchronized private fun persistUploadSuccess() {
        val now = System.currentTimeMillis()
        prefs.edit().putLong("lastUploadSuccess", now).apply()
    }

    private fun bindCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                boundAnalysis?.let { provider.unbind(it) }
                val ticket = resolutionStatus.start(getResolutionLabel())
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetResolution(getTargetResolutionSize())
                    .build()

                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    resolutionStatus.record(ticket, imageProxy.cropRect.width(), imageProxy.cropRect.height())
                    analyzedProxy = imageProxy
                    try {
                        pipeline?.handle(imageProxy.cropRect.width(), imageProxy.cropRect.height())
                    } finally {
                        analyzedProxy = null
                        imageProxy.close()
                    }
                }

                boundAnalysis = analysis
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, analysis)
            } catch (e: Exception) {
                resolutionStatus.stop()
                status = "Camera unavailable: ${e.javaClass.simpleName}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun baseUrl(): String? = prefs.getString("baseUrl", null)

    private fun getImageQualityValue(): Int = when (prefs.getString("imageQualityLabel", "Medium")) {
        "Low" -> 35
        "High" -> 75
        else -> 55
    }

    private fun getUploadIntervalMs(): Long = when (prefs.getString("uploadRateLabel", "High")) {
        "Low" -> 500L
        "Medium" -> 250L
        else -> 120L
    }

    private fun getResolutionLabel(): String = prefs.getString("resolutionLabel", "Medium") ?: "Medium"

    private fun getTargetResolutionSize(): Size = when (getResolutionLabel()) {
        "Low" -> Size(320, 240)
        "High" -> Size(1280, 960)
        else -> Size(640, 480)
    }

    /** Android side of the notification rule; the rule itself is in CaptureNotifier. */
    private class AndroidNotificationSource(private val context: Context) : NotificationSource {
        override fun permissionGranted(): Boolean =
            Build.VERSION.SDK_INT < 33 ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED

        override fun ensureChannel(): Boolean = try {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CaptureNotifier.CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CaptureNotifier.CHANNEL_ID, "CamFlow capture", NotificationManager.IMPORTANCE_LOW)
                )
            }
            true
        } catch (_: Exception) {
            false
        }

        override fun build(statusText: String): Notification =
            NotificationCompat.Builder(context, CaptureNotifier.CHANNEL_ID)
                .setContentTitle(CaptureNotifier.TITLE)
                .setContentText(statusText)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .setShowWhen(false)
                .build()
    }
}
