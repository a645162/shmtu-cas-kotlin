package cn.edu.shmtu.cas.captcha

class ExprCaptchaResolver(
    private val exprProvider: (ByteArray) -> String
) : CaptchaResolver {
    override suspend fun resolve(imageData: ByteArray): Result<CaptchaAnswer> {
        return try {
            val expr = exprProvider(imageData)
            val answer = Captcha.getExprResultByExprString(expr)
            Result.success(CaptchaAnswer(answer, CaptchaAnswerKind.ANSWER))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
