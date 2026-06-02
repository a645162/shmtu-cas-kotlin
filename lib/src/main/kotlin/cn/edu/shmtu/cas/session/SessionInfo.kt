package cn.edu.shmtu.cas.session

/**
 * 探测登录状态结果
 *
 * 对齐 Rust 版本的 LoginProbe
 */
sealed class SessionProbe {
    /** 已经登录成功 */
    data object AlreadyLoggedIn : SessionProbe()

    /** 需要登录，提供登录 URL */
    data class NeedLogin(val loginUrl: String) : SessionProbe()
}

/**
 * 提交登录结果
 *
 * 对齐 Rust 版本的 LoginSubmitResult
 */
sealed class LoginSubmitResult {
    /** 登录成功 */
    data object Success : LoginSubmitResult()

    /** 验证码错误 */
    data object ValidateCodeError : LoginSubmitResult()

    /** 密码错误 */
    data object PasswordError : LoginSubmitResult()

    /** 其他失败 */
    data class Failure(val message: String) : LoginSubmitResult()
}

/**
 * 登录挑战数据
 *
 * 对齐 Rust 版本的 LoginChallenge。
 * prepareChallenge() 的返回值——流程在此暂停，
 * 由调用方决定如何处理验证码图片。
 *
 * @property execution CAS anti-replay token
 * @property captchaImage 验证码图片原始字节数据
 */
data class LoginChallenge(
    val execution: String,
    val captchaImage: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as LoginChallenge
        return execution == other.execution && captchaImage.contentEquals(other.captchaImage)
    }

    override fun hashCode(): Int {
        var result = execution.hashCode()
        result = 31 * result + captchaImage.contentHashCode()
        return result
    }
}
