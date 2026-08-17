package cn.yuandianai.juziscan.payment

import android.app.Activity
import com.alipay.sdk.app.PayTask

class AlipayClient {
    fun pay(activity: Activity, orderString: String): Map<String, String> {
        return PayTask(activity).payV2(orderString, true)
    }
}
