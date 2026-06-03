package cn.edu.shmtu.cas.classifier

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class BillCategory {
    DEPOSIT, ELECTRICITY, BATH, HOT_WATER, CAKE, CANTEEN,
    LIBRARY, HOSPITAL, SHOP, LAUNDRY, NETWORK, TRANSPORT, OTHER;

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

@Serializable
data class CategoryRule(
    val name: List<String> = emptyList(),
    val target: List<String> = emptyList()
)

class BillClassifier(
    private val categories: Map<String, CategoryRule>
) {

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(jsonStr: String): BillClassifier {
            val map = json.decodeFromString<Map<String, CategoryRule>>(jsonStr)
            return BillClassifier(map)
        }
    }

    fun classify(name: String, target: String): BillCategory {
        for ((catName, rule) in categories) {
            rule.name.forEach { kw ->
                if (name.contains(kw)) return BillCategory.fromString(catName)
            }
            rule.target.forEach { kw ->
                if (target.contains(kw)) return BillCategory.fromString(catName)
            }
        }
        return BillCategory.OTHER
    }
}
