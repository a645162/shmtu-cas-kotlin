package cn.edu.shmtu.cas.classifier

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class BillCategory {
    DEPOSIT, ELECTRICITY, BATH, HOT_WATER, CAKE, CANTEEN,
    LIBRARY, HOSPITAL, SHOP, LAUNDRY, NETWORK, TRANSPORT, OTHER;

    /** 对齐 Tauri 端 display_name(中文标签) */
    val displayName: String
        get() = when (this) {
            DEPOSIT -> "充值"
            ELECTRICITY -> "电费"
            BATH -> "洗澡"
            HOT_WATER -> "热水"
            CAKE -> "西点"
            CANTEEN -> "食堂"
            LIBRARY -> "图书馆"
            HOSPITAL -> "校医院"
            SHOP -> "超市"
            LAUNDRY -> "洗衣"
            NETWORK -> "网络"
            TRANSPORT -> "交通"
            OTHER -> "其他"
        }

    /** 对齐 Tauri 端 emoji */
    val emoji: String
        get() = when (this) {
            DEPOSIT -> "💰"
            ELECTRICITY -> "⚡"
            BATH -> "🚿"
            HOT_WATER -> "♨️"
            CAKE -> "🍰"
            CANTEEN -> "🍚"
            LIBRARY -> "📚"
            HOSPITAL -> "🏥"
            SHOP -> "🛒"
            LAUNDRY -> "👕"
            NETWORK -> "🌐"
            TRANSPORT -> "🚌"
            OTHER -> "💳"
        }

    companion object {
        fun fromString(s: String): BillCategory = when (s.lowercase()) {
            "deposit" -> DEPOSIT
            "electricity" -> ELECTRICITY
            "bath" -> BATH
            "hot_water" -> HOT_WATER
            "cake" -> CAKE
            "canteen" -> CANTEEN
            "library" -> LIBRARY
            "hospital" -> HOSPITAL
            "shop" -> SHOP
            "laundry" -> LAUNDRY
            "network" -> NETWORK
            "transport" -> TRANSPORT
            else -> OTHER
        }
    }
}

/**
 * 类型分类规则 — JSON 版(向后兼容)。
 *
 * 字段:
 *   - name: 关键词数组,匹配 bill.item_type
 *   - target: 关键词数组,匹配 bill.target_user
 *
 * 注意:旧版语义会把 name 同时拿去匹配 item_type / target_user,
 *   这与 Tauri `match_field` 互斥语义不一致(已弃用,改用 [fromToml])。
 */
@Serializable
data class CategoryRule(
    val name: List<String> = emptyList(),
    val target: List<String> = emptyList()
)

/**
 * 内部统一表示,无论 JSON 还是 TOML 加载,最终都规范化成这种结构。
 * matchField 决定 keyword 数组对应 bill 的哪一列(item_type | target_user),
 * 与 Tauri `from_toml` 行为完全一致。
 */
data class InternalRule(
    val matchField: String,         // "item_type" | "target_user"
    val matchNames: List<String>,   // 对应 item_type
    val matchTargets: List<String>  // 对应 target_user
)

class BillClassifier(
    private val categories: Map<String, InternalRule>
) {

    /**
     * 暴露给调用方的"内部 key → 显示名"映射,用于
     * 与 Tauri `get_category_distribution` 输出一致。
     */
    fun displayNameOf(categoryKey: String): String = when (categoryKey.lowercase()) {
        "deposit" -> "充值"
        "electricity" -> "电费"
        "bath" -> "洗澡"
        "hot_water" -> "热水"
        "cake" -> "西点"
        "canteen" -> "食堂"
        "library" -> "图书馆"
        "hospital" -> "校医院"
        "shop" -> "超市"
        "laundry" -> "洗衣"
        "network" -> "网络"
        "transport" -> "交通"
        else -> "其他"
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * 旧版 JSON 加载(保持向后兼容,语义同 Tauri BillClassifier.from_json):
         *   name 数组去匹配 item_type, target 数组去匹配 target_user,
         *   两组互不越界,与 Tauri `from_toml` 行为一致。
         */
        fun fromJson(jsonStr: String): BillClassifier {
            val map = json.decodeFromString<Map<String, CategoryRule>>(jsonStr)
            val internal = LinkedHashMap<String, InternalRule>(map.size)
            for ((k, v) in map) {
                internal[k] = InternalRule(
                    matchField = if (v.name.isNotEmpty()) "item_type" else "target_user",
                    matchNames = v.name,
                    matchTargets = v.target
                )
            }
            return BillClassifier(internal)
        }

        /**
         * 从 Tauri 兼容的 TOML 字符串加载,完全对齐 Rust `from_toml`:
         *   - 顶层 [type.X] 段
         *   - 每个段含 match_field ("item_type" | "target_user") + match_names + match_targets
         * 命中顺序: 解析时按文件出现顺序保留 (LinkedHashMap),首次命中即返回。
         */
        fun fromToml(tomlStr: String): BillClassifier {
            val root = TomlLightweight.parse(tomlStr)
            @Suppress("UNCHECKED_CAST")
            val typeMap = (root["type"] as? Map<String, Any?>) ?: emptyMap()
            val internal = LinkedHashMap<String, InternalRule>(typeMap.size)
            for ((k, raw) in typeMap) {
                @Suppress("UNCHECKED_CAST")
                val m = raw as Map<String, Any?>
                val field = (m["match_field"] as? String) ?: "item_type"
                val names = (m["match_names"] as? List<String>) ?: emptyList()
                val targets = (m["match_targets"] as? List<String>) ?: emptyList()
                internal[k] = InternalRule(field, names, targets)
            }
            return BillClassifier(internal)
        }

        /**
         * 从 rules.toml 同时含 type + position + schedule 的合并文件,
         * 取出 type 段加载,与 Tauri `commands/classify.rs` 行为一致。
         */
        fun fromRulesToml(tomlStr: String): BillClassifier = fromToml(tomlStr)
    }

    /**
     * 分类 — 严格对齐 Tauri `BillClassifier::classify`:
     *   对每个规则按 match_field 决定要查的字段;
     *   item_type 数组用 contains 子串匹配 item_type;
     *   target_user 数组用 contains 子串匹配 target_user;
     *   首次命中即返回对应 category 枚举;无命中返回 OTHER。
     */
    fun classify(itemType: String, targetUser: String): BillCategory {
        for ((catKey, rule) in categories) {
            val hit = when (rule.matchField) {
                "item_type" -> rule.matchNames.any { itemType.contains(it) }
                "target_user", "target" -> rule.matchTargets.any { targetUser.contains(it) }
                else -> false
            }
            if (hit) return BillCategory.fromString(catKey)
        }
        return BillCategory.OTHER
    }

    /**
     * 返回 category 内部 key(小写字符串,例如 "canteen"),用于上层按 key group by。
     * 找不到匹配的 key 时返回 "other"。
     */
    fun classifyKey(itemType: String, targetUser: String): String {
        for ((catKey, rule) in categories) {
            val hit = when (rule.matchField) {
                "item_type" -> rule.matchNames.any { itemType.contains(it) }
                "target_user", "target" -> rule.matchTargets.any { targetUser.contains(it) }
                else -> false
            }
            if (hit) return catKey
        }
        return "other"
    }

    fun ruleCount(): Int = categories.size
}
