package com.ocm.qrdecoder

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin


/**
 * 提取二维码算法
 */
object ExtractQrCodeHelper {

    init {
        System.loadLibrary("opencv_java4")
    }

    fun extract(bitmap: Bitmap): Bitmap? {
        val mat = Mat()
        val matDst = Mat()
        Utils.bitmapToMat(bitmap, mat)
        val kSize = Size(3.toDouble(), 3.toDouble())
        Imgproc.cvtColor(mat, matDst, Imgproc.COLOR_BGRA2GRAY, 1)
        Imgproc.GaussianBlur(matDst, matDst, kSize, 0.toDouble())
        handleMat(matDst, matDst)
        val dstBitmap = Bitmap.createBitmap(matDst.width(), matDst.height(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(matDst, dstBitmap)
        mat.release()
        matDst.release()
        return dstBitmap
    }

    fun extract(mat: Mat): Bitmap? {
        val matDst = Mat()
        handleMat(mat, matDst)
        val bitmap = Bitmap.createBitmap(matDst.width(), matDst.height(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(matDst, bitmap)
        matDst.release()
        return bitmap
    }

    private fun handleMat(mat:Mat, matDst: Mat): Mat? {
        Core.copyTo(mat, matDst, Mat())
        if (mat.height()*mat.width() < 9000) {
            Imgproc.resize(matDst, matDst, Size(800.toDouble(), 600.toDouble()))
        }
        val checkMat = Mat()
        Imgproc.adaptiveThreshold(matDst, checkMat, 255.toDouble(), Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, 105, 2.toDouble())
        // Canny边缘检测
        Imgproc.Canny(checkMat, checkMat, 112.toDouble(), 255.toDouble(), 3)
        // 膨胀，连接边缘
        for (i in 0 until 2)
        Imgproc.dilate(checkMat, checkMat, Mat(), Point((-1).toDouble(), (-1).toDouble()), 3, 1, Scalar(1.toDouble()))

        //查找二维码位置
        val contours: List<MatOfPoint> = ArrayList()
        val hierarchy = Mat()
        Imgproc.findContours(checkMat, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_NONE, Point(0.toDouble(), 0.toDouble()))
        for (contour in contours) {
            val newMtx = MatOfPoint2f(*contour.toArray())
            val rotRect = Imgproc.minAreaRect(newMtx)

            val w = rotRect.size.width
            val h = rotRect.size.height
            if (abs(w-h) > 60 || w < 100) { continue }
            val cutRect = rotRect.boundingRect()
            if (cutRect.x < 0) {
                cutRect.x = 0
            }
            if (cutRect.x+cutRect.width > matDst.cols()) {
                cutRect.width = matDst.cols() - cutRect.x
            }
            if (cutRect.y < 0) {
                cutRect.y = 0
            }
            if (cutRect.y+cutRect.height > matDst.rows()) {
                cutRect.height = matDst.rows() - cutRect.y
            }
            Core.copyTo(Mat(matDst, cutRect), matDst, Mat())
            Imgproc.adaptiveThreshold(matDst, matDst, 255.toDouble(), Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, 25, 2.toDouble())
            //旋转
            if (rotRect.angle < 0 && rotRect.angle > -5) break
            if (rotRect.angle > 85 && rotRect.angle < 90) break
            if (rotRect.angle < -85 && rotRect.angle > -90) break
            if (rotRect.angle > 0 && rotRect.angle < 5) break
            val radians = Math.toRadians(rotRect.angle)
            val sin = abs(sin(radians))
            val cos = abs(cos(radians))
            val newWidth = (matDst.width() * cos + matDst.height() * sin)
            val newHeight = (matDst.width() * sin + matDst.height() * cos)
            val center = Point(newWidth / 2, newHeight / 2)
            val size = Size(newWidth, newHeight)
            val affineTrans = Imgproc.getRotationMatrix2D(center, rotRect.angle, 1.toDouble())
            Imgproc.warpAffine(matDst, matDst, affineTrans, size, Imgproc.INTER_NEAREST)
            break
        }
        return null
    }
}