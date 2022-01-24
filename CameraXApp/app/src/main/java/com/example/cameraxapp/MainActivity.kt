package com.example.cameraxapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.*

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.cameraxapp.databinding.ActivityMainBinding
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.camera.core.ImageCapture


class MainActivity : AppCompatActivity() {
    val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private var imageCapture: ImageCapture? = null

    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)


        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        // Set up the listener for take photo button
        binding.cameraCaptureButton.setOnClickListener { takePhoto() }

        outputDirectory = getOutputDirectory()

        cameraExecutor = Executors.newSingleThreadExecutor()

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    // ImageProxy를 Bitmap으로 바꾸어주는 함수.. but previewView.getBitmap()을 사용함으로써 사용할 필요가 없어졌다.
    fun ImageProxy.convertImageProxyToBitmap(): Bitmap {
        val buffer = planes[0].buffer
        buffer.rewind()
        val bytes = ByteArray(buffer.capacity())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    // function for encode Bitmap to Base64 String
    fun bitmapToString(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val bytes = stream.toByteArray()
        return Base64.getEncoder().encodeToString(bytes)
    }

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {

                    Log.e(TAG, "Photo capture !")
                    Log.d("mymy", "foramt: ${image.format}, height: ${image.height}, width: ${image.width}, info: ${image.imageInfo}, plane: ${image.planes[0].buffer[4]}")

                    binding.tvBase64.text = "captured"

                    // ImageProxy to Bitmap과 같은 함수를 따로 작성하여 사용할 필요 없이, previewView가 보여주고있는 화면 그대로 bitmap을 생성할 수 있다.
                    val bm = binding.viewFinder.getBitmap()
                    //rotate if needed using matrix

//                val mat = Matrix()
//                mat.postRotate(0F)
//                val newBm = Bitmap.createBitmap(bm!!, 0, 0, bm.width, bm.height, mat, true)
//                //bm.recycle()
//                val rotateEnd = System.currentTimeMillis()
//                Log.d("mymy", "${newBm.height},    ${newBm.width},  rotate time: ${rotateEnd - rotateStart}")
//                //binding.ivReturn.setImageBitmap(newBm)

                    //bitmap to string
                    val resultBase64 = bitmapToString(bm!!)
                    Log.d("mymy", "${resultBase64}....")

                    // base64로 encoding된 String을 다시 decode하여 Bitmap형태로 바꿈
                    val nowBA = Base64.getDecoder().decode(resultBase64)
                    val nowImg: Bitmap = BitmapFactory.decodeByteArray(nowBA, 0, nowBA.size)

                    // decode하여 만든 bitmap이미지를 화면에 띄운다
                    binding.ivReturn.setImageBitmap(nowImg)
                    image.close()
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture error")
                }
            })
    }


    @SuppressLint("UnsafeOptInUsageError", "RestrictedApi")
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder().build()

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }


    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXBasic"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    fun getCorrectionMatrix(imageProxy: ImageProxy, previewView: PreviewView): Matrix {
        val cropRect = imageProxy.cropRect
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val matrix = Matrix()

        // A float array of the source vertices (crop rect) in clockwise order.
        val source = floatArrayOf(
            cropRect.left.toFloat(),
            cropRect.top.toFloat(),
            cropRect.right.toFloat(),
            cropRect.top.toFloat(),
            cropRect.right.toFloat(),
            cropRect.bottom.toFloat(),
            cropRect.left.toFloat(),
            cropRect.bottom.toFloat()
        )

        // A float array of the destination vertices in clockwise order.
        val destination = floatArrayOf(0f, 0f, previewView.width.toFloat(), 0f,
            previewView.width.toFloat(), previewView.height.toFloat(), 0f, previewView.height.toFloat()
        )

        // The destination vertexes need to be shifted based on rotation degrees. The
        // rotation degree represents the clockwise rotation needed to correct the image.

        // Each vertex is represented by 2 float numbers in the vertices array.
        val vertexSize = 2
        // The destination needs to be shifted 1 vertex for every 90° rotation.
        val shiftOffset = rotationDegrees / 90 * vertexSize;
        val tempArray = destination.clone()
        for (toIndex in source.indices) {
            val fromIndex = (toIndex + shiftOffset) % source.size
            destination[toIndex] = tempArray[fromIndex]
        }
        matrix.setPolyToPoly(source, 0, destination, 0, 4)
        return matrix
    }


}