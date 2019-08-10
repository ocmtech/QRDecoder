package com.ocm.qrdemo

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.Camera
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.support.annotation.NonNull
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import com.guo.android_extend.GLES2Render
import com.guo.android_extend.widget.CameraFrameData
import com.guo.android_extend.widget.CameraSurfaceView
import com.ocm.qrdecoder.ExtractQrCodeHelper
import com.ocm.qrdecoder.QRDecoder
import kotlinx.android.synthetic.main.activity_main.*
import org.opencv.core.Rect


class MainActivity : Activity(), CameraSurfaceView.OnCameraListener {

    private var mWidth = 640
    private var mHeight = 480
    private lateinit var qrDecoder: QRDecoder
    private var toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        qrDecoder = QRDecoder(this)
        qrDecoder.setDecodeListener(object : QRDecoder.QRDecoderListener{
            override fun onCvtBitmap(bitmap: Bitmap) {
                ivPreview.setImageBitmap(bitmap)
            }

            override fun onDecodeSuccess(result: String) {
                Log.d("MainActivity", "onDecodeSuccess: $result)")
                toneGen.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
            }

            override fun onFindQR(bitmap: Bitmap) {
                ivQR.setImageBitmap(bitmap)
            }

            override fun onOpenCVQR(bitmap: Bitmap) {
                ivOpenCV.setImageBitmap(bitmap)
            }
        })

        var startTime = System.currentTimeMillis()
        val bitmap = BitmapFactory.decodeResource(resources, R.mipmap.qr1)

        val bitmap1 = ExtractQrCodeHelper.extract(bitmap)
        Log.e("QRDemo", String.format("图像处理及识别耗时: %d ms", System.currentTimeMillis() - startTime))
        bitmap1?.let {
            ivPreview.setImageBitmap(bitmap1)
            buttonDecode.setOnClickListener {
                startTime = System.currentTimeMillis()
                val result = qrDecoder.processBitmapData(bitmap1)
                Log.e("QRDemo", String.format("识别耗时: %d ms", System.currentTimeMillis() - startTime))
                result?.let {
                    Log.d("MainActivity", "onDecodeSuccess: $result)")
                    toneGen.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
                }
            }
        }
//        surfaceView.setOnCameraListener(this)
//        surfaceView.setupGLSurafceView(glsurfaceView, true, GLES2Render.MIRROR_NONE, 90)
//        surfaceView.debug_print_fps(true, false)
    }


    override fun onResume() {
        super.onResume()
        if (!checkPermission()) {
            requestPermission()
        }
    }

    override fun onPreview(data: ByteArray?, width: Int, height: Int, format: Int, timestamp: Long): Any {
        data?.let { data ->
            qrDecoder.processData(data, width, height, 90f, GLES2Render.MIRROR_NONE)
        }
        tvThreshold.text = ExtractQrCodeHelper.threshold.toString()
        return arrayOfNulls<Rect>(0)
    }

    override fun setupCamera(): Camera? {
        try {
//            val cameraFacing = Camera.CameraInfo.CAMERA_FACING_FRONT
//            var ultimateCameraId = findCameraIdByFacing(cameraFacing)
//            if (ultimateCameraId == -1) {
//                ultimateCameraId = findCameraIdByFacing(Camera.CameraInfo.CAMERA_FACING_BACK)
//            }
//            val mCamera = Camera.open(ultimateCameraId)
            val mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT)
//            val mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK)
            val parameters = mCamera!!.parameters
            parameters?.setPreviewSize(mWidth, mHeight)
            mCamera.parameters = parameters

            mWidth = mCamera.parameters!!.previewSize.width
            mHeight = mCamera.parameters!!.previewSize.height
            return mCamera
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    override fun setupChanged(format: Int, width: Int, height: Int) {
    }

    override fun onBeforeRender(data: CameraFrameData?) {
    }

    override fun startPreviewImmediately(): Boolean {
        return true
    }


    override fun onAfterRender(data: CameraFrameData) {
    }

    private fun findCameraIdByFacing(cameraFacing: Int): Int {
        val cameraInfo = Camera.CameraInfo()
        for (cameraId in 0 until Camera.getNumberOfCameras()) {
            try {
                Camera.getCameraInfo(cameraId, cameraInfo)
                if (cameraInfo.facing == cameraFacing) {
                    return cameraId
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return -1
    }

    private fun checkPermission(): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !== PackageManager.PERMISSION_GRANTED) {
            return false
        }
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) === PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !== PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf<String>(Manifest.permission.CAMERA), 2)
            return
        }
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) !== PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf<String>(Manifest.permission.WRITE_EXTERNAL_STORAGE), 3)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, @NonNull permissions: Array<String>, @NonNull grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty()) {
            if (!checkPermission()) {
                requestPermission()
            }
        }
    }
}
