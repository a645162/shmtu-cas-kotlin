package cn.edu.shmtu.cas.datatype

import java.util.Locale

/**
 * 强类型账单条目
 *
 * 对齐 Rust 版本的 BillItem，但只保留 Android 端必需字段：
 * - 跳过合并账单的 number_list/is_combined（Android Room 存单条）
 * - 跳过 emoji/display_name（UI 自己做）
 * - 保留 timestamp/money 方便按时间排序、汇总
 */
data class BillItem(
    // === 时间 ===
    val dateStr: String,
    val timeStr: String,
    val timeStrFormat: String,
    val dateTimeFormat: String,
    val timestamp: Long,

    // === 交易信息 ===
    val billType: String,
    val transactionNo: String,
    val targetUser: String,

    // === 金额 ===
    val amount: String,
    val money: Float,

    // === 其他 ===
    val paymentMethod: String,
    val status: BillItemStatus
) {
    /**
     * 简洁显示格式：日期时间 | 类型 | 对方 | 金额 | 状态
     */
    override fun toString(): String =
        "$dateTimeFormat | $billType | $targetUser | $amount | $status"

    /**
     * CSV 字段访问器（对齐 Rust 的 get_field）
     */
    fun getField(name: String): String = when (name) {
        "date_str" -> dateStr
        "time_str" -> timeStr
        "time_str_formatted" -> timeStrFormat
        "date_time_formatted" -> dateTimeFormat
        "timestamp" -> timestamp.toString()
        "item_type" -> billType
        "number" -> transactionNo
        "target_user" -> targetUser
        "money_str" -> amount
        "money" -> String.format(Locale.ROOT, "%.2f", money)
        "method" -> paymentMethod
        "status", "status_str" -> status.name
        else -> ""
    }
}
