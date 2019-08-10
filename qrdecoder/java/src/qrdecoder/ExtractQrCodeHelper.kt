package com.ocm.qrdecoder

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ToneGenerator
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.concurrent.timer
import kotlin.math.*

/**
 * 提取二维码算法
 */
object ExtractQrCodeHelper {

    var threshold = 120.toDouble()
        private set

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
        val bitmap = Bitmap.createBitmap(mat.width(), mat.height(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(matDst, bitmap)
        matDst.release()
        return bitmap
    }

    private fun handleMat(mat:Mat, matDst: Mat): Mat? {
        var startTime = System.currentTimeMillis()
        if (mat.height()*mat.width() < 9000) {
            Imgproc.resize(mat, mat, Size(800.toDouble(), 600.toDouble()))
        }
        Imgproc.threshold(mat, matDst, threshold, 255.toDouble(), Imgproc.THRESH_BINARY)
//        Imgproc.adaptiveThreshold(matDst, matDst, 255.toDouble(), Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, 25, 10.toDouble())
        // Canny边缘检测
//        Imgproc.Canny(matDst, matDst, 112.toDouble(), 255.toDouble())
        // 膨胀，连接边缘
//        Imgproc.dilate(matDst, matDst, Mat(), Point((-1).toDouble(),(-1).toDouble()), 3, 1, Scalar(1.toDouble()))
        startTime = System.currentTimeMillis()
        val qrMats = findQRPoint(matDst)
        Log.e("QRDemo", String.format("找二维码耗时: %d ms $qrMats", System.currentTimeMillis() - startTime))
        startTime = System.currentTimeMillis()
        qrMats?.let {
            //            capture(qrMats, mat, "qr")
            val fPoint = otherPoint(qrMats)
            val rectPoints = ArrayList(qrMats.map { centerCal(it) })
            fPoint?.let {
                val center = Point(matDst.width().toDouble()/2, matDst.height().toDouble()/2)
                val referPoint = Point(rectPoints[0].x + 100, rectPoints[0].y)
                val angle = getAngle(referPoint, rectPoints[1], rectPoints[0])
                val affineTrans = Imgproc.getRotationMatrix2D(center, angle, 1.toDouble())

                Log.e("QRDemo", "angle: $angle")
                Imgproc.warpAffine(matDst, matDst, affineTrans, matDst.size(), Imgproc.INTER_NEAREST)
                return matDst
            }
        }
        Log.e("QRDemo", String.format("变换二维码耗时: %d ms", System.currentTimeMillis() - startTime))
        return null
    }

    private fun findQRPoint(matDst: Mat): List<MatOfPoint>? {
        val contours: List<MatOfPoint> = ArrayList()
        val hierarchy = Mat()
        Imgproc.findContours(matDst, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_NONE, Point(0.toDouble(), 0.toDouble()))
        val markContours = ArrayList<MatOfPoint>()
        contours.forEachIndexed { i, contour ->
            val newMtx = MatOfPoint2f(*contours[i].toArray())
            val rotRect = Imgproc.minAreaRect(newMtx)

            val w = rotRect.size.width
            val h = rotRect.size.height
            val rate = max(w, h) / min(w, h)
            if (rate < 1.3 && w < matDst.cols()/4 && h<matDst.rows()/4 && Imgproc.contourArea(contours[i])>60) {
                var ds = hierarchy.get(0, i)
                if (ds != null && ds.size > 3) {
                    var count = 0
                    if (ds[3].toInt() == -1) {
                        /**最外層輪廓排除 */
                        return@forEachIndexed
                    }
                    /***
                     * 計算所有子輪廓數量
                     */
                    while (ds[2].toInt() != -1) {
                        ++count
                        ds = hierarchy.get(0, ds[2].toInt())
                    }
                    if (count >= 2 && !containsMat(markContours, contour)) {
                        markContours.add(contour)
                    }
                }
            }
        }
        Log.e("QRDemo", "变换二维码耗时: ${markContours.size}")
        if (markContours.size < 3) return null
        for (i in 0..markContours.size-3) {
            for (j in i+1..markContours.size-2) {
                for (k in j+1 until markContours.size) {
                    val pointthree = arrayListOf<Point>()
                    pointthree.add(centerCal(markContours[i]))
                    pointthree.add(centerCal(markContours[j]))
                    pointthree.add(centerCal(markContours[k]))
                    val cosine0 = abs(getAngle(pointthree[1], pointthree[2], pointthree[0])).toInt()
                    val cosine1 = abs(getAngle(pointthree[0], pointthree[2], pointthree[1])).toInt()
                    val cosine2 = abs(getAngle(pointthree[0], pointthree[1], pointthree[2])).toInt()
                    val cosines = arrayOf(cosine0, cosine1, cosine2)
                    val max = cosines.max() ?: 0
                    val min = cosines.min() ?: 0
                    if((max in 85..95) && (min in 40..50)) {
                        Log.e("1234", "pointthree add $pointthree")
                        val rAngleIndex = cosines.indexOf(max)
                        if (rAngleIndex == 0) return arrayListOf(markContours[i], markContours[j], markContours[k])
                        if (rAngleIndex == 1) return arrayListOf(markContours[j], markContours[i], markContours[k])
                        return arrayListOf(markContours[k], markContours[i], markContours[j])
                    }
                }
            }
        }
        return null
    }

    //除了三个回之后的矩形第四点
    private fun otherPoint(pointMats: List<MatOfPoint>): Point? {
        val p1 = centerCal(pointMats[0])
        val p2 = centerCal(pointMats[1])
        val p3 = centerCal(pointMats[2])
        return Point((p2.x+p3.x-p1.x), (p2.y+p3.y-p1.y))
    }

    private fun containsMat(mats: List<MatOfPoint>, mat: MatOfPoint): Boolean {
        val matCenter = centerCal(mat)
        mats.forEach {
            val itCenter = centerCal(it)
            val rotX = itCenter.x/matCenter.x
            val rotY = itCenter.y/matCenter.y
            if ((rotX>0.95 && rotX<1.05) && rotY>0.95 && rotY<1.05) {
                return true
            }
        }
        return false
    }

    private fun centerCal(matOfPoint: MatOfPoint): Point {
        val mark1Moments = Imgproc.moments(matOfPoint)
        val mark1X = mark1Moments._m10/mark1Moments._m00
        val mark1Y = mark1Moments._m01/mark1Moments._m00
        return Point(mark1X, mark1Y)
    }

    // 根据三个点计算中间那个点的夹角   pt1 pt0 pt2
    private fun getAngle(pt1: Point, pt2: Point, pt0: Point): Double {
        val dx1 = pt1.x - pt0.x
        val dy1 = pt1.y - pt0.y
        val dx2 = pt2.x - pt0.x
        val dy2 = pt2.y - pt0.y
        return 180/3.1415* acos((dx1 * dx2 + dy1 * dy2)/(sqrt(dx1*dx1+dy1*dy1) * sqrt(dx2*dx2+dy2*dy2)))
    }
}