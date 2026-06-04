package cn.edu.shmtu.cas.classifier

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 用餐时段分类器 — 严格对齐 Tauri `BillClassifier::classify_meal`。
 *
 * 数据来源: schedule.toml(结构与 Tauri `[[schedule]]` + `[schedule.timetable.{breakfast,lunch,dinner,midnight_snack}]` 完全一致)。
 *
 * 匹配规则:
 *   1) 遍历所有 schedule 段,选取日期落在 valid_date 范围内(字符串字典序比较;end_date="now" 表示无上限)
 *   2) 在选中段内按 breakfast/lunch/dinner/midnight_snack 顺序检查
 *   3) start_time ≤ time < end_time(左闭右开)
 *   4) 命中即返回时段名(如 "早餐"),无命中返回 null
 */
class MealClassifier(
    private val rules: List<ScheduleRule>
) {

    data class MealSlot(
        val name: String,
        val startTime: String,
        val endTime: String
    )

    data class Timetable(
        val breakfast: MealSlot? = null,
        val lunch: MealSlot? = null,
        val dinner: MealSlot? = null,
        val midnightSnack: MealSlot? = null
    ) {
        fun allSlots(): List<MealSlot> = listOfNotNull(breakfast, lunch, dinner, midnightSnack)
    }

    data class ValidDate(
        val startDate: String,   // 格式: "yyyy.M.d"
        val endDate: String      // 格式: "yyyy.M.d" 或 "now"
    )

    data class ScheduleRule(
        val validDate: ValidDate,
        val timetable: Timetable
    )

    companion object {
        /**
         * 从 Tauri schedule.toml 字符串加载,与 Rust `from_toml` 等价。
         * 也支持从 rules.toml 合并文件中只挑 schedule 段。
         */
        fun fromToml(tomlStr: String): MealClassifier {
            val root = TomlLightweight.parse(tomlStr)
            // schedule 段在 [[schedule]] 下,解析后是 List<Map<String, Any?>>
            @Suppress("UNCHECKED_CAST")
            val arr = (root["schedule"] as? List<Map<String, Any?>>) ?: emptyList()
            val rules = arr.map { item ->
                @Suppress("UNCHECKED_CAST")
                val validDateRaw = (item["valid_date"] as? Map<String, Any?>) ?: emptyMap()
                val validDate = ValidDate(
                    startDate = (validDateRaw["start_date"] as? String) ?: "",
                    endDate = (validDateRaw["end_date"] as? String) ?: "now"
                )
                @Suppress("UNCHECKED_CAST")
                val ttRaw = (item["timetable"] as? Map<String, Any?>) ?: emptyMap()
                val timetable = Timetable(
                    breakfast = ttRaw["breakfast"]?.let { slotOf(it) },
                    lunch = ttRaw["lunch"]?.let { slotOf(it) },
                    dinner = ttRaw["dinner"]?.let { slotOf(it) },
                    midnightSnack = ttRaw["midnight_snack"]?.let { slotOf(it) }
                )
                ScheduleRule(validDate, timetable)
            }
            return MealClassifier(rules)
        }

        private fun slotOf(raw: Any?): MealSlot? {
            @Suppress("UNCHECKED_CAST")
            val m = raw as? Map<String, Any?> ?: return null
            return MealSlot(
                name = (m["name"] as? String) ?: return null,
                startTime = (m["start_time"] as? String) ?: return null,
                endTime = (m["end_time"] as? String) ?: return null
            )
        }

        /**
         * 默认 schedule(与 Tauri 当前默认 schedule.toml 完全一致),用于 assets 加载失败时回退。
         */
        fun defaultRules(): MealClassifier = fromToml(
            """
            [[schedule]]
            [schedule.valid_date]
            start_date = "2019.9.1"
            end_date = "now"

            [schedule.timetable.breakfast]
            name = "早餐"
            start_time = "6:30"
            end_time = "8:30"

            [schedule.timetable.lunch]
            name = "午餐"
            start_time = "10:45"
            end_time = "12:30"

            [schedule.timetable.dinner]
            name = "晚餐"
            start_time = "16:30"
            end_time = "18:15"

            [schedule.timetable.midnight_snack]
            name = "夜宵"
            start_time = "18:15"
            end_time = "21:00"
            """.trimIndent()
        )

        private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy.M.d")
    }

    /**
     * 对一个时间戳做时段分类。
     * @param timestamp 秒级 epoch(本地时区)
     * @return 时段名(早餐/午餐/晚餐/夜宵)或 null
     */
    fun classify(timestamp: Long): String? {
        if (timestamp <= 0L) return null
        val dt = LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.systemDefault())
        val currentDate = dt.format(DATE_FMT)
        val timeStr = "%02d:%02d".format(dt.hour, dt.minute)

        for (rule in rules) {
            if (!isDateValid(currentDate, rule.validDate)) continue
            for (slot in rule.timetable.allSlots()) {
                if (timeStr >= slot.startTime && timeStr < slot.endTime) {
                    return slot.name
                }
            }
        }
        return null
    }

    private fun isDateValid(current: String, valid: ValidDate): Boolean {
        val startOk = current >= valid.startDate
        val endOk = if (valid.endDate == "now") true else current <= valid.endDate
        return startOk && endOk
    }
}
