package cn.yuandianai.juziscan.payment

import android.app.Activity

class VipPurchaseManager(
    private val backendClient: JuZiScanBackendClient = JuZiScanBackendClient(),
    private val alipayClient: AlipayClient = AlipayClient()
) {
    fun getMembershipPlans(): MembershipPlanListResponse {
        return backendClient.getMembershipPlans()
    }

    fun getMembershipStatus(): MembershipStatus {
        return backendClient.getMembershipStatus()
    }

    fun startPurchase(activity: Activity, planCode: String): VipPurchaseResult {
        val order = backendClient.createAlipayOrder(planCode)
        val payResult = alipayClient.pay(activity, order.order_string)
        val resultStatus = payResult["resultStatus"].orEmpty()

        var orderDetail = backendClient.getOrder(order.order_no)
        var membership = getMembershipStatus()

        // 支付宝客户端返回时，服务端异步通知可能尚未处理完成。对于已支付或
        // 结果确认中的订单，短暂自动查询状态，避免把内部流程暴露给用户。
        if (resultStatus == ALIPAY_SUCCESS || resultStatus == ALIPAY_PROCESSING) {
            for (attempt in 0 until CONFIRMATION_RETRY_COUNT) {
                if (orderDetail.status == ORDER_PAID && membership.is_vip) {
                    break
                }
                Thread.sleep(CONFIRMATION_RETRY_INTERVAL_MS)
                orderDetail = backendClient.getOrder(order.order_no)
                membership = getMembershipStatus()
            }
        }

        return VipPurchaseResult(
            orderNo = order.order_no,
            alipayResultStatus = resultStatus,
            orderStatus = orderDetail.status,
            isVip = membership.is_vip,
            vipPlanCode = membership.plan_code
        )
    }

    private companion object {
        const val ALIPAY_SUCCESS = "9000"
        const val ALIPAY_PROCESSING = "8000"
        const val ORDER_PAID = "paid"
        const val CONFIRMATION_RETRY_COUNT = 5
        const val CONFIRMATION_RETRY_INTERVAL_MS = 1_000L
    }
}

data class VipPurchaseResult(
    val orderNo: String,
    val alipayResultStatus: String,
    val orderStatus: String,
    val isVip: Boolean,
    val vipPlanCode: String?
)
