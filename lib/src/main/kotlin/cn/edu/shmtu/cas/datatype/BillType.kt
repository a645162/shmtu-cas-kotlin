package cn.edu.shmtu.cas.datatype

enum class BillType(val tabNo: String, val label: String) {
    ALL("0", "全部"),
    NOT_PAID("1", "未支付"),
    SUCCESS("2", "成功"),
    FAILURE("3", "失败")
}
