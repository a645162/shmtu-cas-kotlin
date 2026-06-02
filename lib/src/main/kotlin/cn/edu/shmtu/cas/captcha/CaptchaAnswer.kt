package cn.edu.shmtu.cas.captcha

/**
 * 验证码答案类型
 */
enum class CaptchaAnswerKind {
    /** 算式如 "12+34=" */
    EXPRESSION,
    /** 已是最终答案 */
    ANSWER
}

/**
 * 验证码解析结果
 *
 * 对齐 Rust 版本的 CaptchaAnswer。
 * OCR 服务返回的是算式表达式还是最终答案，由 kind 区分。
 * intoFinalAnswer() 统一提取最终结果。
 */
data class CaptchaAnswer(
    val value: String,
    val kind: CaptchaAnswerKind = CaptchaAnswerKind.EXPRESSION
) {
    /**
     * 将 EXPRESSION 类型转换为 ANSWER 类型。
     * 例如 "12+34=" → "34" (ANSWER)
     */
    fun intoFinalAnswer(): CaptchaAnswer {
        if (kind == CaptchaAnswerKind.ANSWER) return this
        val result = Captcha.getExprResultByExprString(value)
        return if (result.isNotEmpty()) CaptchaAnswer(result, CaptchaAnswerKind.ANSWER) else this
    }
}
