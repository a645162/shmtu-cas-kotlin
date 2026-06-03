package cn.edu.shmtu.cas.datatype

enum class BillItemStatus {
    SUCCESS,
    FAILURE,
    NOT_PAID,
    UNKNOWN;

    companion object {
        fun fromString(text: String): BillItemStatus = when {
            text.contains("成功") -> SUCCESS
            text.contains("失败") -> FAILURE
            text.contains("未支付") || text.contains("待支付") -> NOT_PAID
            else -> UNKNOWN
        }
    }
}
