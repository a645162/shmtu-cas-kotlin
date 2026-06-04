package cn.edu.shmtu.cas.classifier

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PositionInfo(
    val position: String,
    val room: String
)

@Serializable
private data class PositionRuleFile(
    val field: String = "target",
    val keywords: Map<String, PositionInfo> = emptyMap()
)

class PositionTranslator private constructor(
    private val keywords: Map<String, PositionInfo>
) {

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** 旧版 JSON 加载,保持向后兼容 */
        fun fromJson(jsonStr: String): PositionTranslator {
            val rule = json.decodeFromString<PositionRuleFile>(jsonStr)
            return PositionTranslator(rule.keywords)
        }

        /**
         * 从 Tauri 兼容的 TOML 字符串加载:
         *   - [position] 段(field 字段,仅占位说明用)
         *   - [position.keywords."<target>"] 段(building + room)
         * 完全对齐 Rust `PositionTranslator::from_toml`。
         */
        fun fromToml(tomlStr: String): PositionTranslator {
            val root = TomlLightweight.parse(tomlStr)
            @Suppress("UNCHECKED_CAST")
            val posTable = (root["position"] as? Map<String, Any?>) ?: emptyMap()
            @Suppress("UNCHECKED_CAST")
            val rawKeywords = (posTable["keywords"] as? Map<String, Any?>) ?: emptyMap()
            val out = LinkedHashMap<String, PositionInfo>(rawKeywords.size)
            for ((target, raw) in rawKeywords) {
                @Suppress("UNCHECKED_CAST")
                val m = raw as Map<String, Any?>
                val building = (m["building"] as? String) ?: ""
                val room = (m["room"] as? String) ?: ""
                out[target] = PositionInfo(building, room)
            }
            return PositionTranslator(out)
        }

        /** 从 rules.toml 合并文件中仅取出 position 段 */
        fun fromRulesToml(tomlStr: String): PositionTranslator = fromToml(tomlStr)
    }

    /**
     * 翻译 target_user 字段 → (building, room)。
     * 与 Tauri `translate` 行为一致:
     *   1) 精确匹配 (trim 后)
     *   2) 模糊匹配:target_user 是否包含任一 keyword
     */
    fun translate(targetUser: String): PositionInfo? {
        val trimmed = targetUser.trim()
        keywords[trimmed]?.let { return it }
        for ((keyword, info) in keywords) {
            if (trimmed.contains(keyword)) return info
        }
        return null
    }

    fun getAllKeywords(): Map<String, PositionInfo> = keywords
}
