package cn.edu.shmtu.cas.session

import java.util.Base64

/**
 * 手动验证码请求异常
 *
 * 业务层在以下两种情况下抛出本异常：
 * 1. 手动模式：调用方探测到需要验证码，但 [cn.edu.shmtu.cas.captcha.CaptchaResolver] 为 null 或未自动处理
 * 2. 远程 OCR 模式下调用方选择走 UI 兜底
 *
 * 抛出后 UI 层应弹出验证码输入框，用户输入后调用
 * [cn.edu.shmtu.cas.auth.EpayAuth.submitLogin] 继续流程。
 *
 * 对齐 Rust 版本的 `MANUAL_CAPTCHA_REQUIRED|image|execution` 协议字符串，
 * 但以结构化方式承载。
 */
class ManualCaptchaRequiredException(
    /** 验证码图片的 base64 编码（PNG） */
    val captchaImageBase64: String,
    /** CAS execution token（一次性） */
    val execution: String,
    /** 触发异常的账号 ID（Android 域内 Long） */
    val accountId: Long,
    /** 触发异常的账号标签（用于 UI 展示） */
    val accountLabel: String,
    /** 原始图片字节，便于需要重新显示的 UI 二次使用 */
    val captchaImageBytes: ByteArray,
) : Exception("MANUAL_CAPTCHA_REQUIRED") {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ManualCaptchaRequiredException
        return captchaImageBase64 == other.captchaImageBase64 &&
                execution == other.execution &&
                accountId == other.accountId &&
                accountLabel == other.accountLabel &&
                captchaImageBytes.contentEquals(other.captchaImageBytes)
    }

    override fun hashCode(): Int {
        var result = captchaImageBase64.hashCode()
        result = 31 * result + execution.hashCode()
        result = 31 * result + accountId.hashCode()
        result = 31 * result + accountLabel.hashCode()
        result = 31 * result + captchaImageBytes.contentHashCode()
        return result
    }

    companion object {
        /**
         * 从原始字节构造
         */
        fun of(imageBytes: ByteArray, execution: String, accountId: Long, accountLabel: String): ManualCaptchaRequiredException {
            val b64 = Base64.getEncoder().encodeToString(imageBytes)
            return ManualCaptchaRequiredException(b64, execution, accountId, accountLabel, imageBytes)
        }
    }
}
