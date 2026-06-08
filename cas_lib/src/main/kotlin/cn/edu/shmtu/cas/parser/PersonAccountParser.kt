package cn.edu.shmtu.cas.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * 一卡通个人账户页解析结果
 *
 * 对应 `/epay/personaccount/index` 接口的 HTML。
 *
 * 资金&安全信息:
 * - 现金资金(元)
 * - 安全保护问题状态
 * - 注册时间
 *
 * 基本信息:
 * - 学工号 / 真实姓名 / 性别 / 固话
 * - 证件类型 / 证件号码
 * - 电子邮箱 / 昵称 / 班级 / 手机 / 备注 / 用户类型
 */
data class PersonAccountInfo(
    // 头部
    val realName: String = "",
    val realNameAuthStatus: String = "",

    // 资金&安全信息
    val cashBalance: Double = 0.0,
    val cashBalanceRaw: String = "",
    val securityQuestionStatus: String = "",
    val registerDate: String = "",

    // 基本信息
    val studentId: String = "",
    val email: String = "",
    val nickname: String = "",
    val gender: String = "",
    val className: String = "",
    val mobile: String = "",
    val fixedLine: String = "",
    val idType: String = "",
    val idNumber: String = "",
    val remark: String = "",
    val userType: String = "",

    // CSRF
    val csrfToken: String = "",
    val csrfHeader: String = "X-CSRF-TOKEN"
)

/**
 * 解析一卡通 `/epay/personaccount/index` 页面。
 */
class PersonAccountParser {

    /**
     * 一次性解析整个 HTML 页面
     */
    fun parse(htmlCode: String): PersonAccountInfo {
        val document: Document = Jsoup.parse(htmlCode)

        // 1) panel-title 标题: 姓名:xxx 实名认证:已认证
        val panelTitleText = document.selectFirst(".panel-title")?.text()?.trim() ?: ""
        val realName = extractAfter(panelTitleText, "姓名：")
            ?: extractAfter(panelTitleText, "姓名:")
            ?: ""
        val realNameAuthStatus = extractAfter(panelTitleText, "实名认证:")
            ?: extractAfter(panelTitleText, "实名认证：")
            ?: ""

        // 2) CSRF token
        val csrfToken = document.selectFirst("meta[name=_csrf]")?.attr("content") ?: ""
        val csrfHeader = document.selectFirst("meta[name=_csrf_header]")?.attr("content") ?: "X-CSRF-TOKEN"

        // 3) 基本信息表 #baseinfo tbody
        val baseInfoMap = parseKeyValueTable(document.selectFirst("#baseinfo tbody"))

        // 4) 资金&安全信息表 (otherinfo) 包含两张 table,各有一个 tbody,需要合并
        val otherInfoContainer = document.selectFirst("#otherinfo")
        val otherInfoMap = if (otherInfoContainer == null) emptyMap()
        else otherInfoContainer.select("tbody").fold(linkedMapOf<String, String>()) { acc, tbody ->
            acc.putAll(parseKeyValueTable(tbody))
            acc
        }

        // 资金信息
        val cashBalanceRaw = (otherInfoMap["现金资金"] ?: "").replace("元", "").trim()
        val cashBalance = cashBalanceRaw.toDoubleOrNull() ?: 0.0

        val securityQuestionStatus = otherInfoMap["安全保护问题"] ?: ""
        val registerDate = otherInfoMap["注册时间"] ?: ""

        return PersonAccountInfo(
            realName = realName,
            realNameAuthStatus = realNameAuthStatus,
            cashBalance = cashBalance,
            cashBalanceRaw = cashBalanceRaw,
            securityQuestionStatus = securityQuestionStatus,
            registerDate = registerDate,
            studentId = baseInfoMap["学工号"] ?: "",
            email = baseInfoMap["电子邮箱"] ?: "",
            nickname = baseInfoMap["昵称"] ?: "",
            gender = baseInfoMap["性别"] ?: "",
            className = baseInfoMap["班级"] ?: "",
            mobile = baseInfoMap["手机"] ?: "",
            fixedLine = baseInfoMap["固话"] ?: "",
            idType = baseInfoMap["证件类型"] ?: "",
            idNumber = baseInfoMap["证件号码"] ?: "",
            remark = baseInfoMap["备注"] ?: "",
            userType = baseInfoMap["用户类型"] ?: "",
            csrfToken = csrfToken,
            csrfHeader = csrfHeader
        )
    }

    /**
     * 将表格中"字段名: 值"的每一行解析为 Map(自动跳过空值行)。
     */
    private fun parseKeyValueTable(tbody: Element?): Map<String, String> {
        if (tbody == null) return emptyMap()
        val result = linkedMapOf<String, String>()
        val rows = tbody.children()
        var i = 0
        while (i < rows.size) {
            val tr = rows[i]
            val tds = tr.children()
            if (tds.size >= 2) {
                val key = tds[0].text().trim().removeSuffix("：").removeSuffix(":").trim()
                val value = tds[1].text().trim()
                if (key.isNotEmpty() && key != "nbsp") {
                    result[key] = value
                }
            }
            i++
        }
        return result
    }

    private fun extractAfter(text: String, marker: String): String? {
        val idx = text.indexOf(marker)
        if (idx < 0) return null
        val start = idx + marker.length
        if (start >= text.length) return ""
        val tail = text.substring(start).trim()
        // 截断到下一个 " " / "&" / 换行 等分隔符
        val end = tail.indexOfFirst { ch -> ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t' }
        return if (end < 0) tail else tail.substring(0, end).trim()
    }
}
