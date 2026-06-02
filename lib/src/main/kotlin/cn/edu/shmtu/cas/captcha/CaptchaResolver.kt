package cn.edu.shmtu.cas.captcha

/**
 * 验证码解析器接口
 *
 * 对齐 Rust 版本的 CaptchaResolver trait。
 * 实现此接口即可自定义验证码处理策略：
 * - ManualCaptchaResolver: 由用户手动输入
 * - RemoteOcrCaptchaResolver: 远程 TCP OCR 服务
 * - RemoteOcrHttpCaptchaResolver: 远程 HTTP OCR 服务
 * - 自定义实现: Android UI 弹窗、Tauri IPC 等
 */
interface CaptchaResolver {
    suspend fun resolve(imageData: ByteArray): Result<CaptchaAnswer>
}
