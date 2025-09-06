package business.digitalfood.watch.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Text
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import business.digitalfood.watch.DataHolder
import business.digitalfood.watch.DataHolder.ROUND_WEAR_PADDING_FACTOR
import kotlinx.coroutines.launch
import java.nio.charset.Charset

class TermsOfUseActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val extras = intent.extras
        if (extras != null) {
            val useInternationalNames = extras.getString("useInternationalNames").toBoolean()
            setContent {
                ConstructScreen(useInternationalNames)
            }
        }
    }

    @OptIn(ExperimentalWearFoundationApi::class)
    @Composable
    fun ConstructScreen(useInternationalNames: Boolean) {
        val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
        val focusRequester = rememberActiveFocusRequester()
        val coroutineScope = rememberCoroutineScope()

        Scaffold(positionIndicator = {
            PositionIndicator(scalingLazyListState = listState)
        }) {
            ScalingLazyColumn(
                state = listState,
                modifier = if (applicationContext.resources.configuration.isScreenRound) {
                    Modifier.padding(
                        start = (ROUND_WEAR_PADDING_FACTOR * applicationContext.resources.configuration.screenWidthDp).dp,
                        end = (ROUND_WEAR_PADDING_FACTOR * applicationContext.resources.configuration.screenWidthDp).dp,
                        top = (ROUND_WEAR_PADDING_FACTOR * applicationContext.resources.configuration.screenHeightDp).dp,
                        bottom = (ROUND_WEAR_PADDING_FACTOR * applicationContext.resources.configuration.screenHeightDp).dp,
                    )
                    .onRotaryScrollEvent {
                        coroutineScope.launch {
                            listState.scrollBy(it.verticalScrollPixels)
                            listState.animateScrollBy(it.verticalScrollPixels)
                        }
                        true
                    }
                } else {
                    Modifier
                }
                .focusRequester(focusRequester)
                .focusable()
            ) {
                item(0) {
                    Text(
                        DataHolder.guiConfig["terms"]!![DataHolder.internationalizeLabel("termsOfUseHeadlineText", useInternationalNames)].toString(),
                        fontSize = (DataHolder.guiConfig["terms"]!!["titleFontSize"] as Long).toInt().sp,
                    )
                }
                item(1) {
                    val tos = if (useInternationalNames) { assets.open("tosEn.txt") } else { assets.open("tos.txt") }
                    var tosText = tos.readBytes().toString(Charset.defaultCharset())

                    tosText = tosText.replace("#globalAddress1", DataHolder.globalAddress["line1"].toString())
                    tosText = tosText.replace("#globalAddress2", DataHolder.globalAddress["line2"].toString())
                    tosText = tosText.replace("#globalAddress3", DataHolder.globalAddress["line3"].toString())
                    tosText = tosText.replace("#globalEmailAddress", DataHolder.globalAddress["email"].toString())
                    tosText = tosText.replace("#restaurantAddress1", DataHolder.restaurantConfig["address1"].toString())
                    tosText = tosText.replace("#restaurantAddress2", DataHolder.restaurantConfig["address2"].toString())
                    tosText = tosText.replace("#restaurantAddress3", DataHolder.restaurantConfig["address3"].toString())
                    tosText = tosText.replace("#restaurantPhoneNumber", DataHolder.restaurantConfig["phoneNumber"].toString())

                    Text(tosText, fontSize = (DataHolder.guiConfig["terms"]!!["textFontSize"] as Long).toInt().sp, modifier = Modifier.padding(top = (DataHolder.guiConfig["terms"]!!["paddingTop"] as Long).toInt().dp))
                }
            }
        }
    }
}