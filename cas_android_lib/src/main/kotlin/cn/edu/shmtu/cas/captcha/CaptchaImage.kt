package cn.edu.shmtu.cas.captcha

/**
 * 验证码图片 + 关联 Cookie 的封装。
 *
 * 由 [Captcha.getImageDataFromUrlUsingGet] 返回的 `Pair<ByteArray?, String>?` 语义化而来，
 * 便于 app / ocr_app_demo 共享同一份数据类型（避免各自再定义一个本地 data class）。
 *
 * @property imageData 验证码 PNG/JPG 字节流，**可能为 null**（原 lib 接口契约保留空值）
 * @property cookie    验证码接口返回的 Set-Cookie（拼接好 ; 分隔的字符串），调用方应在登录提交时回带
 */
data class CaptchaImage(
    val imageData: ByteArray?,
    val cookie: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CaptchaImage
        if (imageData != null) {
            if (other.imageData == null) return false
            if (!imageData.contentEquals(other.imageData)) return false
        } else if (other.imageData != null) return false
        return cookie == other.cookie
    }

    override fun hashCode(): Int {
        var result = imageData?.contentHashCode() ?: 0
        result = 31 * result + cookie.hashCode()
        return result
    }
}
