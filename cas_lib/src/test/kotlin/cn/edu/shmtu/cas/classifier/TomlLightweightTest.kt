package cn.edu.shmtu.cas.classifier

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TomlLightweightTest {

    @Test
    fun `quoted dotted keys remain one path segment`() {
        val parsed = TomlLightweight.parse(
            """
            [position.keywords."A.食堂1楼大餐厅"]
            building = "海馨楼"
            room = "海馨第1食堂"
            """.trimIndent()
        )

        val position = parsed["position"] as? Map<*, *>
        val keywords = position?.get("keywords") as? Map<*, *>
        val roomInfo = keywords?.get("A.食堂1楼大餐厅") as? Map<*, *>

        assertNotNull(roomInfo)
        assertEquals("海馨楼", roomInfo["building"])
        assertEquals("海馨第1食堂", roomInfo["room"])
    }

    @Test
    fun `database bill rules toml matches expected counts`() {
        val rulesToml = repoFile("database/bill/rules.toml")
        val classifier = BillClassifier.fromToml(rulesToml)
        val translator = PositionTranslator.fromToml(rulesToml)
        val mealClassifier = MealClassifier.fromToml(rulesToml)

        assertEquals(12, classifier.ruleCount())
        assertEquals(15, translator.getAllKeywords().size)
        assertEquals(1, mealClassifier.ruleCount())
        assertEquals("海馨第1食堂", translator.translate("A食堂1楼大餐厅")?.room)
        assertEquals("公共浴室", translator.translate("新淋浴热水（21-26）")?.position)
    }

    @Test
    fun `database bill split toml files all parse correctly`() {
        val positionToml = repoFile("database/bill/position.toml")
        val scheduleToml = repoFile("database/bill/schedule.toml")

        val translator = PositionTranslator.fromToml(positionToml)
        val mealClassifier = MealClassifier.fromToml(scheduleToml)

        assertEquals(22, translator.getAllKeywords().size)
        assertEquals("海联2楼", translator.translate("C食堂2楼")?.room)
        assertEquals(1, mealClassifier.ruleCount())
        assertTrue(
            mealClassifier.getAllRules().first().timetable.allSlots().size >= 4,
            "schedule.toml 应至少包含四个用餐时段"
        )
    }

    private fun repoFile(relativePath: String): String {
        val path = Path.of(System.getProperty("user.dir"))
            .resolve("../../../../")
            .resolve(relativePath)
            .normalize()
        return Files.readString(path)
    }
}
