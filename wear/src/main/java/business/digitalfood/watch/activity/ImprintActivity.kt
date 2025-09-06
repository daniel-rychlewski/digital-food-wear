package business.digitalfood.watch.activity

import android.content.Context
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
import kotlinx.coroutines.launch
import java.nio.charset.Charset

class ImprintActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val extras = intent.extras
        if (extras != null) {
            val useInternationalNames = extras.getString("useInternationalNames").toBoolean()
            setContent {
                ConstructScreen(applicationContext, useInternationalNames)
            }
        }
    }

    @OptIn(ExperimentalWearFoundationApi::class)
    @Composable
    fun ConstructScreen(context: Context, useInternationalNames: Boolean) {
        val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
        val focusRequester = rememberActiveFocusRequester()
        val coroutineScope = rememberCoroutineScope()

        Scaffold(positionIndicator = {
            PositionIndicator(scalingLazyListState = listState)
        }) {
            ScalingLazyColumn(
                state = listState,
                modifier = if (context.resources.configuration.isScreenRound) {
                    Modifier.padding(
                        start = (DataHolder.ROUND_WEAR_PADDING_FACTOR * context.resources.configuration.screenWidthDp).dp,
                        end = (DataHolder.ROUND_WEAR_PADDING_FACTOR * context.resources.configuration.screenWidthDp).dp,
                        top = (DataHolder.ROUND_WEAR_PADDING_FACTOR * context.resources.configuration.screenHeightDp).dp,
                        bottom = (DataHolder.ROUND_WEAR_PADDING_FACTOR * context.resources.configuration.screenHeightDp).dp,
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
                        DataHolder.guiConfig["imprint"]!![DataHolder.internationalizeLabel("legalNoticeHeadline", useInternationalNames)].toString(),
                        fontSize = (DataHolder.guiConfig["imprint"]!!["titleFontSize"] as Long).toInt().sp
                    )
                }
                item(1) {
                    val imprint = if (useInternationalNames) { assets.open("imprintEn.txt") } else { assets.open("imprint.txt") }
                    var imprintText = imprint.readBytes().toString(Charset.defaultCharset())

                    imprintText = imprintText.replace("#globalAddress1", DataHolder.globalAddress["line1"].toString())
                    imprintText = imprintText.replace("#globalAddress2", DataHolder.globalAddress["line2"].toString())
                    imprintText = imprintText.replace("#globalAddress3", DataHolder.globalAddress["line3"].toString())
                    imprintText = imprintText.replace("#globalEmailAddress", DataHolder.globalAddress["email"].toString())

                    Text(imprintText, fontSize = (DataHolder.guiConfig["imprint"]!!["textFontSize"] as Long).toInt().sp, modifier = Modifier.padding(top = (DataHolder.guiConfig["imprint"]!!["textPaddingTop"] as Long).toInt().dp))
                }
            }
        }
    }
}