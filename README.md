# 二维码扫码解析优化
## 前言
本代码库为 [`重庆一厘米科技有限公司`](http://www.ocmcom.com/index.html#/) 所用 Android 产品的扫二维码优化方案，不一定适用于手机方案，使用了 [BGAQRCode-Android](https://github.com/bingoogolapple/BGAQRCode-Android) 的 Zbar 集成方案解码，手机扫码优化可以学习该库处理；

## 使用
可以使用我们 maven 源 gradle 集成，也可以下载源码使用：
1. 在 Project 中加入 maven 源
```gradle
allprojects {
    repositories {
        ...
        .....
        maven { url 'http://maven.ocmcom.com:9999/repository/android/' }
    }
}

```
2. 添加 dependencies
```gradle
dependencies {
    ...
    ......
    implementation "com.ocm.lib:qrdecoder:1.0.0"
}
```
3. 解码
```Kotlin
QRDecoder(context).processBitmapData(bitmap)
// or
QRDecoder(context).processData(data, width, height, 90f, GLES2Render.MIRROR_NONE)
```

## Demo 说明
Demo 默认加载旋转的图片, 如使用摄像头解码 Demo：
1. `activity_main.xml` 中将预览图大小调为
```xml
    <ImageView
            android:id="@+id/ivPreview"
            android:layout_width="200dp"
            android:layout_height="200dp"/>
```
2. `MainActivity.kt` 中解开注释
```Kotlin
        surfaceView.setOnCameraListener(this)
        surfaceView.setupGLSurafceView(glsurfaceView, true, GLES2Render.MIRROR_NONE, 90)
        surfaceView.debug_print_fps(true, false)
```
PS: 打开摄像头和摄像头参数配置在 `MainActivity.kt setupCamera()` 中，详情见代码库中。
## 优化方法
#### 使用 `OpenCV` 做图片预处理
1. 图片灰度处理，解析一次，正常的二维码一般在这里就能解析了，耗时 18 ms；
2. 二值化
3. 找到二维码并校正角度，第二次解析耗时 22 ms

一次解码大概总耗时: 50 ms；<br>
- 原图 <br>
![原图](qr.png)

- 校正后的图 <br>
![校正图](dstQR.png)

## 可能的问题
* 二值化阈值，改值目前适配了定焦摄像头，受到光的影响较大，容易出现曝光后二值化没有二维码的情况；<br>
TODO：可能的话后面会做一下自适应阈值处理，目前使用 OpenCV 4.1.1 `adaptiveThreshold` 方法会抛异常 `(-215:Assertion failed) src && dst && count > 0 in function 'FilterEngine__proceed'` 
* 校正旋转后二维码可能会出现在剪裁框外，不剪裁则不会有此问题；

如果遇到了问题，或者有更好的建议，欢迎给我们提 issue

## 引用的库
> 图像处理 https://github.com/opencv/opencv <br>
Zbar 解码 https://github.com/bingoogolapple/BGAQRCode-Android <br>
Demo 摄像头 https://github.com/gqjjqg/android-extend