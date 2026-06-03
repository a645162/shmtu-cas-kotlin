package cn.edu.shmtu.cas.auth.common

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.logging.Logger

class CookieManager {

    private companion object {
        val log = Logger.getLogger(CookieManager::class.java.name)
        val json = Json { ignoreUnknownKeys = true }
    }

    @Serializable
    private data class CookieEntry(val value: String)

    private val cookieString = StringBuilder()

    fun restore(jsonStr: String): Result<Unit> {
        return try {
            if (jsonStr.isBlank()) return Result.success(Unit)
            val map = mutableMapOf<String, String>()
            try {
                val entries: Map<String, CookieEntry> = json.decodeFromString(jsonStr.trim())
                entries.forEach { (key, entry) -> map[key] = entry.value }
            } catch (_: Exception) {
                // 兼容格式: "key1=value1; key2=value2"
                jsonStr.trim().split(";").map { it.trim() }.filter { it.contains("=") }.forEach {
                    val parts = it.split("=", limit = 2)
                    if (parts.size == 2) map[parts[0].trim()] = parts[1].trim()
                }
            }
            cookieString.clear()
            cookieString.append(map.entries.joinToString("; ") { "${it.key}=${it.value}" })
            Result.success(Unit)
        } catch (e: Exception) {
            log.warning("[CookieManager] restore failed: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * 接受 CAS 登录后返回的 Set-Cookie 合并字符串（也可能带 Path/Domain 等属性），
     * 对应 `CasAuth.casLogin` 第三个返回值的格式。
     */
    fun restoreFromCookieString(cookieStr: String): Result<Unit> = restore(cookieStr)

    fun extract(): String {
        val map = mutableMapOf<String, CookieEntry>()
        for (pair in cookieString.toString().split(";")) {
            val trimmed = pair.trim()
            if (trimmed.contains("=")) {
                val idx = trimmed.indexOf("=")
                val key = trimmed.substring(0, idx).trim()
                val value = trimmed.substring(idx + 1).trim()
                if (key.isNotEmpty()) map[key] = CookieEntry(value)
            }
        }
        return try { json.encodeToString(map) } catch (_: Exception) { "{}" }
    }

    fun addFromSetCookie(headerVal: String) {
        val trimmed = headerVal.trim()
        val nameValuePart = trimmed.split(";").firstOrNull()?.trim() ?: return
        val eqIdx = nameValuePart.indexOf("=")
        if (eqIdx <= 0) return
        val name = nameValuePart.substring(0, eqIdx).trim()
        val value = nameValuePart.substring(eqIdx + 1).trim()
        if (name.isEmpty() || value.isEmpty()) return

        val current = cookieString.toString()
            .split(";").map { it.trim() }
            .filter { !it.startsWith("$name=") && it.isNotEmpty() }

        cookieString.clear()
        cookieString.append(if (current.isEmpty()) "$name=$value" else current.joinToString("; ") + "; $name=$value")
    }

    fun addAllFromSetCookieHeaders(headers: List<String>) { headers.forEach { addFromSetCookie(it) } }

    fun get(): String = cookieString.toString()

    fun isEmpty(): Boolean = cookieString.isEmpty() || cookieString.toString().isBlank()

    fun clear() { cookieString.clear() }
}
