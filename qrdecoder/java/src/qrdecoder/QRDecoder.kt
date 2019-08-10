package com.ocm.qrdecoder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.renderscript.*
import android.text.TextUtils
import android.util.Log
import cn.bingoogolapple.qrcode.zbar.BarcodeFormat.QRCODE
//import com.google.zxing.*
//import com.google.zxing.common.GlobalHistogramBinarizer
//import com.google.zxing.qrcode.QRCodeReader
import net.sourceforge.zbar.Config
import net.sourceforge.zbar.Image
import net.sourceforge.zbar.ImageScanner
import net.sourceforge.zbar.Symbol
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.Utils
import org.opencv.imgproc.Imgproc
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.lang.Exception
import android.os.Environment.getExternalStorageDirectory
import org.opencv.core.*
import org.opencv.core.CvType.CV_8UC1
import org.opencv.utils.Converters
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.objdetect.QRCodeDetector


//先注释Zxing
//二维码处理
class QRDecoder(context: Context) {
    private var mScanner = ImageScanner()

    private var rs: RenderScript = RenderScript.create(context)
    private var yuvToRgbIntrinsic: ScriptIntrinsicYuvToRGB
    private var yuvType: Type.Builder? = null
    private var rgbaType: Type.Builder? = null
    private var `in`: Allocation? = null
    private var out: Allocation? = null
    private var listener: QRDecoderListener? = null
    private val executor= Executors.newSingleThreadExecutor{ r -> Thread(r, "decoderDispatcher") }
    private val handler = android.os.Handler()
//    private val ALL_HINT_MAP: MutableMap<DecodeHintType, Any> = EnumMap(DecodeHintType::class.java)

    private val mLoaderCallback = object : BaseLoaderCallback(context) {
        override fun onManagerConnected(status: Int) {
            when (status) {
                LoaderCallbackInterface.SUCCESS -> {
                    Log.i("QRDecoder", "OpenCV loaded successfully")
                }
                else -> {
                    super.onManagerConnected(status)
                }
            }
        }
    }

    init {
        System.loadLibrary("opencv_java4")
        yuvToRgbIntrinsic = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs))
        mScanner = ImageScanner()
        mScanner.setConfig(0, Config.X_DENSITY, 3)
        mScanner.setConfig(0, Config.Y_DENSITY, 3)

        mScanner.setConfig(Symbol.NONE, Config.ENABLE, 0)

        mScanner.setConfig(QRCODE.id, Config.ENABLE, 1)
//        if (!OpenCVLoader.initDebug()) {
//            Log.d("QRDecoder", "Internal OpenCV library not found. Using OpenCV Manager for initialization")
//            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, context, mLoaderCallback)
//        } else {
//            Log.d("QRDecoder", "OpenCV library found inside package. Using it!")
//            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS)
//        }

// Zxing
//        val allFormatList = ArrayList<BarcodeFormat>()
//        allFormatList.add(BarcodeFormat.AZTEC)
//        allFormatList.add(BarcodeFormat.CODABAR)
//        allFormatList.add(BarcodeFormat.CODE_39)
//        allFormatList.add(BarcodeFormat.CODE_93)
//        allFormatList.add(BarcodeFormat.CODE_128)
//        allFormatList.add(BarcodeFormat.DATA_MATRIX)
//        allFormatList.add(BarcodeFormat.EAN_8)
//        allFormatList.add(BarcodeFormat.EAN_13)
//        allFormatList.add(BarcodeFormat.ITF)
//        allFormatList.add(BarcodeFormat.MAXICODE)
//        allFormatList.add(BarcodeFormat.PDF_417)
//        allFormatList.add(BarcodeFormat.QR_CODE)
//        allFormatList.add(BarcodeFormat.RSS_14)
//        allFormatList.add(BarcodeFormat.RSS_EXPANDED)
//        allFormatList.add(BarcodeFormat.UPC_A)
//        allFormatList.add(BarcodeFormat.UPC_E)
//        allFormatList.add(BarcodeFormat.UPC_EAN_EXTENSION)

        // 可能的编码格式
//        ALL_HINT_MAP[DecodeHintType.POSSIBLE_FORMATS] = allFormatList
        // 花更多的时间用于寻找图上的编码，优化准确性，但不优化速度
//        ALL_HINT_MAP[DecodeHintType.TRY_HARDER] = java.lang.Boolean.FALSE
        // 复杂模式，开启 PURE_BARCODE 模式（带图片 LOGO 的解码方案）
//        ALL_HINT_MAP.put(DecodeHintType.PURE_BARCODE, Boolean.TRUE);
        // 编码字符集
//        ALL_HINT_MAP[DecodeHintType.CHARACTER_SET] = "utf-8"
    }

    /**
     * 解码回调
     * @param listener QRDecoderListener
     */
    fun setDecodeListener(listener: QRDecoderListener) {
        this.listener = listener
    }


    fun processData(data: ByteArray, width: Int, height: Int, rotate: Float, mirror: Int = 0) {
        executor.submit {
            val startTime = System.currentTimeMillis()
            try {
                val bitmap = rotateNV21ToBitmap(data, width, height, rotate, mirror)
                val mat = Mat()
                val kSize = Size(3.toDouble(), 3.toDouble())
                Utils.bitmapToMat(bitmap, mat)
                Imgproc.GaussianBlur(mat, mat, kSize, 0.toDouble())
                Imgproc.cvtColor(mat, mat, Imgproc.COLOR_BGRA2GRAY, 1)
                Utils.matToBitmap(mat, bitmap)
                handler.post {
                    listener?.onCvtBitmap(bitmap)
                }
                var result: String? = processBitmapData(bitmap)
                if (result != null) {
                    handler.post {
                        listener?.onDecodeSuccess(result!!)
                        listener?.onFindQR(bitmap)
                    }
                    return@submit
                }
                Log.e("QRDemo", String.format("第一次处理及识别耗时: %d ms", System.currentTimeMillis() - startTime))
                val bitmap1 = ExtractQrCodeHelper.extract(mat)
                bitmap1?.let {
                    handler.post {
                        listener?.onOpenCVQR(bitmap1)
                    }
                    result = processBitmapData(bitmap1)
                    if (result != null) {
                        handler.post {
                            listener?.onFindQR(bitmap1)
                            listener?.onDecodeSuccess(result!!)
                        }
                    }
                    Log.e("QRDemo", String.format("第二次处理及识别耗时: %d ms", System.currentTimeMillis() - startTime))
                }
                mat.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            Log.e("QRDemo", String.format("图像处理及识别总耗时: %d ms", System.currentTimeMillis() - startTime))
        }
    }

    //停止所有解码
    fun close() {
        executor.shutdownNow()
    }
    //解码
    private fun processData(data: ByteArray, width: Int, height: Int): String? {
        val barcode = Image(width, height, "Y800")
        barcode.data = data
        var result = processData(barcode)
        if (result != null) return result
//        result = processBitmapData(rotateNV21ToBitmap(data, width, height, 30f))
//        if (result != null) return result
//        result = processBitmapData(rotateNV21ToBitmap(data, width, height, 35f))
//        if (result != null) return result
//        result = processBitmapData(rotateNV21ToBitmap(data, width, height, 40f))
//        if (result != null) return result
//        result = processBitmapData(rotateNV21ToBitmap(data, width, height, 45f))
//        if (result != null) return result
//        result = processBitmapData(rotateNV21ToBitmap(data, width, height, 50f))
//        if (result != null) return result
//        result = processBitmapData(rotateNV21ToBitmap(data, width, height, 55f))
//        if (result != null) return result
        return null
    }

    fun processBitmapData(bitmap: Bitmap): String?  {
        val picWidth = bitmap.width
        val picHeight = bitmap.height
        val barcode = Image(picWidth, picHeight, "RGB4")
        val pix = IntArray(picWidth * picHeight)
        bitmap.getPixels(pix, 0, picWidth, 0, 0, picWidth, picHeight)
        barcode.setData(pix)
        return processData(barcode.convert("Y800"))
    }

    private fun syncDecodeQRCode(bitmap: Bitmap): String? {
//        var result: Result
//        var source: RGBLuminanceSource? = null
//        try {
//            val width = bitmap.width
//            val height = bitmap.height
//            val pixels = IntArray(width * height)
//            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
//            source = RGBLuminanceSource(width, height, pixels)
//            result = QRCodeReader().decode(BinaryBitmap(GlobalHistogramBinarizer(source)), ALL_HINT_MAP)
//            return result.text
//        } catch (e: Exception) {
//            e.printStackTrace()
//            if (source != null) {
//                try {
//                    result = QRCodeReader().decode(BinaryBitmap(GlobalHistogramBinarizer(source)), ALL_HINT_MAP)
//                    return result.text
//                } catch (e2: Throwable) {
//                    e2.printStackTrace()
//                }
//
//            }
//        }
        return null

    }

    private fun processData(barcode: Image): String? {
        if (mScanner.scanImage(barcode) == 0) {
            return null
        }
        for (symbol in mScanner.results) {
            val symData = String(symbol.dataBytes, StandardCharsets.UTF_8)
            // 空数据继续遍历
            if (TextUtils.isEmpty(symData)) {
                continue
            }
            return symData
        }
        return null
    }

    private fun nv21ToBitmap(nv21: ByteArray, width: Int, height: Int): Bitmap {
        if (yuvType == null) {
            yuvType = Type.Builder(rs, Element.U8(rs)).setX(nv21.size)
            `in` = Allocation.createTyped(rs, yuvType!!.create(), Allocation.USAGE_SCRIPT)
            rgbaType = Type.Builder(rs, Element.RGBA_8888(rs)).setX(width).setY(height)
            out = Allocation.createTyped(rs, rgbaType!!.create(), Allocation.USAGE_SCRIPT)
        }
        `in`!!.copyFrom(nv21)
        yuvToRgbIntrinsic.setInput(`in`)
        yuvToRgbIntrinsic.forEach(out)
        val bmpout = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        out!!.copyTo(bmpout)
        return bmpout
    }


    /**
     * 选择变换
     *
     * @param origin 原图
     * @param alpha  旋转角度，可正可负
     * @return 旋转后的图片
     */
    private fun rotateNV21ToBitmap(nv21: ByteArray, width: Int, height: Int, rotate: Float, mirror: Int = 0): Bitmap {
        val origin = nv21ToBitmap(nv21, width, height)
        val owidth = origin.width
        val oheight = origin.height
        val matrix = Matrix()
        matrix.setRotate(rotate)
        if (mirror == 1) {
            matrix.postScale(-1f, 1f)
        }
        // 围绕原地进行旋转
//        val newBM = Bitmap.createBitmap(origin, 0, 0, owidth, oheight, matrix, false)
        val newBM = Bitmap.createBitmap(origin, 90, 55, 320, 360, matrix, false)
        if (newBM == origin) {
            return newBM
        }
        origin.recycle()
        return newBM
    }

    interface QRDecoderListener {
        fun onDecodeSuccess(result: String) {}
        fun onCvtBitmap(bitmap: Bitmap) {}
        fun onOpenCVQR(bitmap: Bitmap) {}
        fun onFindQR(bitmap: Bitmap) {}
    }

}