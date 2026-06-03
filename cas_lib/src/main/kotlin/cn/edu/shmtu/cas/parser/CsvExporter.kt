package cn.edu.shmtu.cas.parser

import cn.edu.shmtu.cas.datatype.BillItem
import java.io.File

/**
 * CSV 导出器
 *
 * 对齐 Rust 版本的 parser/export.rs (CsvExporter)。
 * 纯 Kotlin 实现，不依赖第三方 CSV 库。
 */
class CsvExporter(
    private val headers: List<String> = DEFAULT_HEADERS,
    private val fields: List<String> = DEFAULT_FIELDS
) {

    companion object {
        private val DEFAULT_HEADERS = listOf(
            "日期", "时间", "时间(格式化)", "日期时间",
            "时间戳", "交易名称", "交易号", "对方", "金额", "付款方式", "状态"
        )
        private val DEFAULT_FIELDS = listOf(
            "date_str", "time_str", "time_str_formatted", "date_time_formatted",
            "timestamp", "item_type", "number", "target_user",
            "money_str", "method", "status"
        )
    }

    fun export(path: String, bills: List<BillItem>) {
        val file = File(path)
        file.parentFile?.mkdirs()
        file.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(joinCsv(headers))
            for (bill in bills) {
                writer.newLine()
                writer.write(joinCsv(fields.map { bill.getField(it) }))
            }
        }
    }

    fun toCsvString(bills: List<BillItem>): String {
        val sb = StringBuilder()
        sb.appendLine(joinCsv(headers))
        for (bill in bills) {
            sb.appendLine(joinCsv(fields.map { bill.getField(it) }))
        }
        return sb.toString()
    }

    private fun joinCsv(row: List<String>): String =
        row.joinToString(",") { escapeCsv(it) }

    private fun escapeCsv(value: String): String {
        if (value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')) {
            return "\"" + value.replace("\"", "\"\"") + "\""
        }
        return value
    }
}
