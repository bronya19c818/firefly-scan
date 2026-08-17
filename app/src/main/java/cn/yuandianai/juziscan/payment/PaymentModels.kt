package cn.yuandianai.juziscan.payment

data class CreateOrderRequest(
    val plan_code: String
)

data class OrderResponse(
    val order_no: String,
    val status: String,
    val amount: String,
    val plan_code: String,
    val payment_channel: String,
    val order_string: String
)

data class OrderDetail(
    val order_no: String,
    val status: String,
    val amount: String,
    val plan_code: String,
    val payment_channel: String,
    val created_at: String,
    val paid_at: String?
)

data class MembershipStatus(
    val is_vip: Boolean,
    val plan_code: String?,
    val status: String,
    val source: String?,
    val expire_at: String?
)

data class MembershipPlan(
    val plan_code: String,
    val title: String,
    val price: String,
    val original_price: String?,
    val bottom_label: String,
    val tag_text: String?,
    val is_default: Boolean
)

data class MembershipPlanListResponse(
    val plans: List<MembershipPlan>,
    val payment_channels: List<String>
)
