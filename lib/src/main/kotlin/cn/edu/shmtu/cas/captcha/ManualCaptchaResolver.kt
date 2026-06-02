package cn.edu.shmtu.cas.captcha

/**
 * 手动验证码解析器
 *
 * 由调用方提供 handler 回调，用于 CLI 交互、UI 弹窗等场景。
 * 对齐 Rust 版本的 ManualCaptchaResolver。
 *
 * 用法：
 * ```kotlin
 * val resolver = ManualCaptchaResolver { imageData ->
 *     // 保存图片、显示给用户、等待输入...
 *     CaptchaAnswer(userInput, CaptchaAnswerKind.ANSWER)
 * }
 * ```
 */
class ManualCaptchaResolver(
    private val handler: suspend (ByteArray) -> CaptchaAnswer
) : CaptchaResolver {
    override suspend fun resolve(imageData: ByteArray): Result<CaptchaAnswer> {
        return try {
            Result.success(handler(imageData))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
