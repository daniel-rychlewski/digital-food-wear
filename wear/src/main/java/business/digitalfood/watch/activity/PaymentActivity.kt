package business.digitalfood.watch.activity;

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import business.digitalfood.watch.DataHolder
import business.digitalfood.watch.DataHolder.activeCardPaymentMode
import business.digitalfood.watch.DataHolder.stripeConfig
import business.digitalfood.watch.activity.CartActivity.Companion.deliveryModeSelected
import business.digitalfood.watch.activity.CartActivity.Companion.foodOverview
import business.digitalfood.watch.activity.CartActivity.Companion.orderNumber
import business.digitalfood.watch.activity.CartActivity.Companion.handleOrderNowButtonClicked
import business.digitalfood.watch.activity.CartActivity.Companion.summarySection
import business.digitalfood.watch.activity.CartActivity.Companion.totalPrice
import business.digitalfood.watch.activity.CartActivity.Companion.useInternationalNames
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

class PaymentActivity : ComponentActivity() {
    private lateinit var session: GeckoSession

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val extras = intent.extras
        if (extras != null) {
            val paymentLinkUrl = extras.getString("paymentLinkUrl")
            setContent {
                ConstructScreen(paymentLinkUrl!!)
            }
        }
    }

    @Composable
    fun ConstructScreen(paymentLinkUrl: String) {
        val view = GeckoView(applicationContext)
        session = GeckoSession()
        val runtime = GeckoRuntime.getDefault(applicationContext)

        session.open(runtime)
        view.setSession(session)
        session.loadUri(paymentLinkUrl)

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { view }
        )
    }

    override fun onPause() {
        super.onPause()
        if (isFinishing) {
            val secretKey = (stripeConfig["apiKey"]!![activeCardPaymentMode!!] as Map<String, Object>)["secretKey"].toString()

            DataHolder.retrieveCheckoutSession(DataHolder.paymentLinkId!!, secretKey) { isPaymentSuccessful ->

                if (isPaymentSuccessful) {
                    handleOrderNowButtonClicked(
                        deliveryModeSelected,
                        orderNumber,
                        foodOverview,
                        summarySection,
                        totalPrice,
                        this,
                        applicationContext,
                        useInternationalNames
                    )
                }
            }
        }
    }
}
