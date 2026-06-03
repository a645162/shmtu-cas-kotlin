package cn.edu.shmtu.cas.parser

import cn.edu.shmtu.cas.datatype.BillItem
import cn.edu.shmtu.cas.datatype.BillItemStatus
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 账单解析结果
 *
 * 对齐 Rust 版本的 BillParseResult
 */
data class BillParseResult(
    val bills: List<BillItem>,
    val totalPages: Int
)

class BillParser {

    private var trElementList = mutableListOf<Element>()

    fun getBillTr(htmlCode: String): BillParser {
        val document: Document = Jsoup.parse(htmlCode)
        val tbodyElement: Element? =
            document.selectFirst("#aazone\\.zone_show_box_1 > table > tbody")

        trElementList.clear()
        tbodyElement?.children()?.forEach { trElementList.add(it) }

        return this
    }

    private fun String.onlyDigits(): String = this.replace("[^\\d]".toRegex(), "")

    private fun String.onlyFloatDigits(): String = this.replace("[^\\d.]".toRegex(), "")

    private fun parseDateTime(dateStr: String, timeStr: String): Long {
        val dateTimeStr = "${dateStr.trim()} ${timeStr.trim().replace(Regex("(\\d{2})(\\d{2})(\\d{2})"), "$1:$2:$3")}"
        return try {
            val formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss")
            LocalDateTime.parse(dateTimeStr, formatter)
                .atZone(java.time.ZoneId.systemDefault())
                .toEpochSecond()
        } catch (_: Exception) { 0L }
    }

    /**
     * 解析为强类型 [BillItem] 列表（包含 timestamp 方便排序）
     */
    fun parseBillItems(htmlCode: String): List<BillItem> {
        getBillTr(htmlCode)
        return parseBillItems()
    }

    fun parseBillItems(): List<BillItem> {
        val items = mutableListOf<BillItem>()

        for (trElement in trElementList) {
            val childElement = trElement.children()
            if (childElement.count() != 7) continue

            val dateStr = childElement[0].children()[0].text().trim()
            val timeStr = childElement[0].children()[1].text().trim()
            val type = childElement[1].children()[0].text().trim()
            val number = childElement[1].children()[1].text().replace("交易号：", "").onlyDigits().trim()
            val targetUser = childElement[2].text().trim()
            val money = childElement[3].text().onlyFloatDigits().trim()
            val method = childElement[4].text().trim()
            val statusText = childElement[5].text().trim()

            val timeStrFormat = timeStr.replace(Regex("(\\d{2})(\\d{2})(\\d{2})"), "$1:$2:$3")
            val dateTimeStrFormat = "$dateStr $timeStrFormat"
            val timestamp = parseDateTime(dateStr, timeStr)
            val moneyFloat = money.toFloatOrNull() ?: 0f

            items.add(BillItem(
                dateStr = dateStr,
                timeStr = timeStr,
                timeStrFormat = timeStrFormat,
                dateTimeFormat = dateTimeStrFormat,
                timestamp = timestamp,
                billType = type,
                transactionNo = number,
                targetUser = targetUser,
                amount = money,
                money = moneyFloat,
                paymentMethod = method,
                status = BillItemStatus.fromString(statusText)
            ))
        }

        return items
    }

    /**
     * 一次性解析整页：账单条目 + 总页数
     *
     * 对齐 Rust 版本的 parse_bill_page。
     */
    fun parseBillPage(htmlCode: String): BillParseResult {
        return BillParseResult(parseBillItems(htmlCode), getPageCount(htmlCode))
    }

    /**
     * 解析为 HashMap 列表（向后兼容）
     */
    fun getBillList(htmlCode: String): MutableList<HashMap<String, String>> {
        getBillTr(htmlCode)
        return getBillList()
    }

    fun getBillList(): MutableList<HashMap<String, String>> {
        return parseBillItems().map { item ->
            HashMap<String, String>().apply {
                put("dateStr", item.dateStr)
                put("timeStr", item.timeStr)
                put("timeStrFormat", item.timeStrFormat)
                put("dateTimeStrFormat", item.dateTimeFormat)
                put("type", item.billType)
                put("number", item.transactionNo)
                put("targetUser", item.targetUser)
                put("money", item.amount)
                put("method", item.paymentMethod)
                put("status", item.status.name)
            }
        }.toMutableList()
    }

    fun getPageCount(htmlCode: String): Int {
        val document: Document = Jsoup.parse(htmlCode)
        val pageElement: Element =
            document.selectFirst("#aazone\\.zone_show_box_1 > div > table > tbody") ?: return 1

        val pageStr = pageElement.text()
        if (!pageStr.contains("/") || !pageStr.contains("首页")) return 1

        val cleaned = pageStr.substring(
            pageStr.indexOf("/") + 1,
            pageStr.indexOf("首页")
        ).replace("页", "").trim()

        return cleaned.toIntOrNull() ?: 1
    }
}
