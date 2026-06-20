package cn.edu.shmtu.cas.captcha

/**
 * 验证码算式解析工具。
 *
 * 输入 NCNN 模型返回的数组：
 * - v1: `[result, exprString, equalSymbol, operatorIndex, digit1, digit2]`
 * - v2: `[result, exprString, equalSymbol(-1), operatorIndex, digitLeft, digitRight]`
 *
 * 输出形如 `3+5=8` 的字符串。
 *
 * **为什么不放 cas_lib**：数组布局是 NCNN 模型绑定的，与 Android 平台强相关；
 * cas_android_lib 才是它正确的归属（OCR Demo 与主 app 都可共用）。
 */
object CaptchaOcrHelper {

    /**
     * 把 NCNN 识别结果拼成 `op1 op op2=answer` 字符串。
     *
     * 优先使用 native 层已生成好的完整表达式字符串 `[1]`；
     * 只有在其缺失时，才按统一 6 元组布局或旧 v2 5 元组布局回退重建。
     *
     * @param predictResult NCNN `predict_validate_code(_v2)` 的返回值
     * @return 形如 `3+5=8`，失败返回 null
     */
    fun buildExprString(predictResult: Array<Any?>?): String? {
        if (predictResult == null || predictResult.size < 2) return null

        val rawExpr = (predictResult.getOrNull(1) as? String)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        if (rawExpr != null) return rawExpr

        return when {
            predictResult.size >= 6 -> buildSixTupleExprString(predictResult)
            predictResult.size >= 5 -> buildLegacyV2ExprString(predictResult)
            else -> null
        }
    }

    private fun buildSixTupleExprString(predictResult: Array<Any?>): String? {
        val answer = (predictResult.getOrNull(0) as? Int) ?: return null
        val operatorIndex = (predictResult.getOrNull(3) as? Int) ?: return null
        val op1 = (predictResult.getOrNull(4) as? Int) ?: return null
        val op2 = (predictResult.getOrNull(5) as? Int) ?: return null
        val operator = operatorFromIndex(operatorIndex) ?: return null
        return "$op1 $operator $op2 = $answer"
    }

    private fun buildLegacyV2ExprString(predictResult: Array<Any?>): String? {
        val answer = (predictResult.getOrNull(0) as? Int) ?: return null
        val op1 = (predictResult.getOrNull(2) as? Int) ?: return null
        val operatorIndex = (predictResult.getOrNull(3) as? Int) ?: return null
        val op2 = (predictResult.getOrNull(4) as? Int) ?: return null
        val operator = operatorFromIndex(operatorIndex) ?: return null
        return "$op1 $operator $op2 = $answer"
    }

    private fun operatorFromIndex(operatorIndex: Int): String? = when (operatorIndex) {
        0 -> "+"
        1 -> "-"
        2 -> "*"
        else -> null
    }

    /**
     * 从已拼好的 `op1 op op2=answer` 字符串中取出最终答案。
     *
     * 委托给 [Captcha.getExprResultByExprString]，本方法只是别名方便调用方引用。
     */
    fun extractAnswer(expr: String): String = Captcha.getExprResultByExprString(expr)
}
