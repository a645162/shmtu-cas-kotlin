package cn.edu.shmtu.cas.captcha

/**
 * 验证码算式解析工具。
 *
 * 输入 NCNN 模型 `predict_validate_code` 输出的 6 元数组：
 * - `[0]` operatorIndex：0=+、1=-、2=*（其它值视为不支持的算式，返回 null）
 * - `[1]` exprString  ：可选的原始表达式字符串（如 "3+5"），由 NCNN 模型附带，便于兜底
 * - `[2]` operand1   ：第一个操作数
 * - `[3]` operand2   ：第二个操作数
 * - `[4] / [5]`       ：冗余字段（部分模型版本会重复 digit 输出），本解析器忽略
 *
 * 输出形如 `3+5=8` 的字符串。
 *
 * **为什么不放 cas_lib**：6 元数组格式是 NCNN 模型绑定的，与 Android 平台强相关；
 * cas_android_lib 才是它正确的归属（OCR Demo 与主 app 都可共用）。
 */
object CaptchaOcrHelper {

    /**
     * 把 6 元识别结果拼成 `op1 op op2=answer` 字符串。
     *
     * @param predictResult NCNN `predict_validate_code` 的返回值（6 元 Object[]）
     * @return 形如 `3+5=8`，失败返回 null
     */
    fun buildExprString(predictResult: Array<Any?>?): String? {
        if (predictResult == null || predictResult.size < 4) return null

        val operatorIndex = (predictResult.getOrNull(0) as? Int) ?: return null
        val op1 = (predictResult.getOrNull(2) as? Int) ?: return null
        val op2 = (predictResult.getOrNull(3) as? Int) ?: return null

        val operator = when (operatorIndex) {
            0 -> "+"
            1 -> "-"
            2 -> "*"
            else -> return null
        }

        val answer = when (operator) {
            "+" -> op1 + op2
            "-" -> op1 - op2
            "*" -> op1 * op2
            else -> return null
        }

        return "$op1$operator$op2=$answer"
    }

    /**
     * 从已拼好的 `op1 op op2=answer` 字符串中取出最终答案。
     *
     * 委托给 [Captcha.getExprResultByExprString]，本方法只是别名方便调用方引用。
     */
    fun extractAnswer(expr: String): String = Captcha.getExprResultByExprString(expr)
}
