package com.example.safeher.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Silently captures photos from both front and back cameras when SOS is triggered.
 * All camera operations run on the main thread as required by CameraX.
 * Photos are saved to: {externalFilesDir}/SOS_Photos/
 */
object CameraHelper {

    private const val TAG = "SafeHer.Camera"
    private const val CAMERA_INIT_DELAY_MS = 800L
    private const val BETWEEN_CAMERAS_DELAY_MS = 1500L

    /**
     * Entry point: silently captures from back camera, then front camera.
     * Respects the camera-on-SOS toggle preference.
     */
    fun capturePhotosOnSos(context: Context) {
        if (!PreferencesHelper.isCameraOnSosEnabled(context)) {
            Log.d(TAG, "Camera capture disabled — skipping.")
            return
        }
        val appContext = context.applicationContext
        Handler(Looper.getMainLooper()).post {
            captureFromCamera(appContext, CameraSelector.DEFAULT_BACK_CAMERA) {
                // After back camera finishes, wait then capture front camera
                Handler(Looper.getMainLooper()).postDelayed({
                    captureFromCamera(appContext, CameraSelector.DEFAULT_FRONT_CAMERA) {
                        Log.d(TAG, "Both cameras captured successfully.")
                    }
                }, BETWEEN_CAMERAS_DELAY_MS)
            }
        }
    }

    /**
     * Captures a single photo from the given camera selector.
     * Must be called on the main thread.
     */
    private fun captureFromCamera(
        context: Context,
        selector: CameraSelector,
        onDone: () -> Unit
    ) {
        val owner = HeadlessLifecycleOwner()
        owner.start()

        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = try {
                providerFuture.get()
            } catch (e: Exception) {
                Log.e(TAG, "Provider error: ${e.message}")
                owner.destroy()
                onDone()
                return@addListener
            }

            val imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            try {
                provider.unbindAll()
                provider.bindToLifecycle(owner, selector, imageCapture)
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed (${selectorName(selector)}): ${e.message}")
                owner.destroy()
                onDone()
                return@addListener
            }

            // Give the camera sensor time to initialize before shooting
            Handler(Looper.getMainLooper()).postDelayed({
                val outputFile = createPhotoFile(context, selector)
                val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

                imageCapture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                            Log.d(TAG, "Photo saved [${selectorName(selector)}]: ${outputFile.absolutePath}")
                            provider.unbindAll()
                            owner.destroy()
                            onDone()
                        }

                        override fun onError(exc: ImageCaptureException) {
                            Log.e(TAG, "Capture error [${selectorName(selector)}]: ${exc.message}")
                            provider.unbindAll()
                            owner.destroy()
                            onDone()
                        }
                    }
                )
            }, CAMERA_INIT_DELAY_MS)

        }, ContextCompat.getMainExecutor(context))
    }

    /** Creates the output file in {externalFilesDir}/SOS_Photos/ */
    private fun createPhotoFile(context: Context, selector: CameraSelector): File {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val photoDir = File(baseDir, "SOS_Photos").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val face = selectorName(selector)
        return File(photoDir, "SOS_${face}_$timestamp.jpg")
    }

    private fun selectorName(selector: CameraSelector) =
        if (selector == CameraSelector.DEFAULT_BACK_CAMERA) "back" else "front"

    /**
     * A lightweight LifecycleOwner that lets CameraX bind in a Service context.
     * All state changes MUST happen on the main thread (CameraX requirement).
     */
    private class HeadlessLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle = registry

        /** Transitions lifecycle to RESUMED so CameraX can start streaming. */
        fun start() {
            // LifecycleRegistry.currentState traverses through all required states automatically
            registry.currentState = Lifecycle.State.RESUMED
        }

        /** Tears down the lifecycle cleanly. */
        fun destroy() {
            if (registry.currentState != Lifecycle.State.DESTROYED) {
                registry.currentState = Lifecycle.State.DESTROYED
            }
        }
    }
}
