package cn.edu.shmtu.cas.classifier

import kotlinx.serialization.Serializable

@Serializable
data class PositionInfo(
    val position: String,
    val room: String
)

class PositionTranslator private constructor(
    private val keywords: Map<String, PositionInfo>
) {

    companion object {
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
     *
     * 可选参数 [matchTrace] — 若非 null,函数在内部命中时回调 (mode, keyword, info),
     *   mode ∈ {"EXACT", "FUZZY"},方便上层 (app 模块) 打印 Log.d 定位翻译路径。
     *   不传则零开销,不影响 lib 单元测试。
     */
    fun translate(
        targetUser: String,
        matchTrace: ((mode: String, keyword: String, info: PositionInfo) -> Unit)? = null
    ): PositionInfo? {
        val trimmed = targetUser.trim()
        // 1) 精确匹配
        keywords[trimmed]?.let {
            matchTrace?.invoke("EXACT", trimmed, it)
            return it
        }
        // 2) 模糊匹配 (按 LinkedHashMap 顺序遍历 — 与 Tauri HashMap 行为大致一致)
        for ((keyword, info) in keywords) {
            if (trimmed.contains(keyword)) {
                matchTrace?.invoke("FUZZY", keyword, info)
                return info
            }
        }
        return null
    }

    fun getAllKeywords(): Map<String, PositionInfo> = keywords
}
