package cn.edu.shmtu.cas.captcha

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

/**
 * Android 平台相关的验证码工具扩展。
 *
 * 本类仅承载 cas_lib 无法在纯 JVM 中表达的 Android 平台 API 调用（例如 Bitmap）。
 * cas_lib 本身保持平台无关；cas_android_lib 通过 `api project(':cas_lib')` 将 cas_lib
 * 的公开 API 传递给消费者（app / ocr_app_demo），本文件作为 Android 平台专属代码单独提供。
 */
class CaptchaAndroid {

    companion object {

        /**
         * 将 Android Bitmap 编码为 PNG 字节数组，便于在 CAS 验证码流程中作为 ByteArray 传递。
         */
        fun AndroidBitmapToByteArray(bitmap: Bitmap): ByteArray {
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
            return baos.toByteArray()
        }

    }

}
