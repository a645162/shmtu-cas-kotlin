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

        fun fromJson(jsonStr: String): PositionTranslator {
            val rule = json.decodeFromString<PositionRuleFile>(jsonStr)
            return PositionTranslator(rule.keywords)
        }
    }

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
