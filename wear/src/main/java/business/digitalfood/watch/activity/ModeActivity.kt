package business.digitalfood.watch.activity

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import business.digitalfood.watch.DataHolder
import kotlinx.coroutines.launch

class ModeActivity: ComponentActivity() {

    companion object {
        lateinit var mode: String

        fun isModeInitialized() = ::mode.isInitialized
    }

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

    override fun onDestroy() {
        super.onDestroy()

        var editor: SharedPreferences.Editor = DataHolder.sharedPreferences.edit()

        if (isModeInitialized()) {
            editor = editor.putString(DataHolder.guiConfig["main"]!!["savedDeliveryModeKey"] as String, mode)
            DataHolder.deliveryMode = mode
        }

        editor.apply()

        DataHolder.deliveryModeError.postValue(!DataHolder.isModeSelected())
    }

    @OptIn(ExperimentalWearFoundationApi::class)
    @Composable
    fun ConstructScreen(useInternationalNames: Boolean) {
        val radioOptions = DataHolder.guiConfig["mode"]!!["modeOptions"] as List<Map<String, String>>

        val (selectedOption, onOptionSelected) = remember { mutableStateOf(DataHolder.sharedPreferences.getString(
            DataHolder.guiConfig["main"]!!["savedDeliveryModeKey"] as String, null) ?: DataHolder.guiConfig["mode"]!!["defaultMode"] as String) }

        mode = selectedOption

        val scalingLazyListState = rememberScalingLazyListState()

        Scaffold(positionIndicator = {
            PositionIndicator(scalingLazyListState = scalingLazyListState)
        }) {
            val focusRequester = rememberActiveFocusRequester()
            val coroutineScope = rememberCoroutineScope()
            ScalingLazyColumn(
                modifier = if (applicationContext.resources.configuration.isScreenRound) {
                    Modifier.padding(
                        start = (DataHolder.ROUND_WEAR_PADDING_FACTOR * applicationContext.resources.configuration.screenWidthDp).dp,
                        end = (DataHolder.ROUND_WEAR_PADDING_FACTOR * applicationContext.resources.configuration.screenWidthDp).dp,
                        top = (DataHolder.ROUND_WEAR_PADDING_FACTOR * applicationContext.resources.configuration.screenHeightDp).dp,
                        bottom = (DataHolder.ROUND_WEAR_PADDING_FACTOR * applicationContext.resources.configuration.screenHeightDp).dp,
                    )
                    .onRotaryScrollEvent {
                        coroutineScope.launch {
                            scalingLazyListState.scrollBy(it.verticalScrollPixels)
                            scalingLazyListState.animateScrollBy(it.verticalScrollPixels)
                        }
                        true
                    }
                } else {
                    Modifier
                }
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .focusable(),
                state = scalingLazyListState
            ) {
                item {
                    Text(
                        DataHolder.guiConfig["chip"]!![DataHolder.internationalizeLabel("deliveryModeData", useInternationalNames)] as String,
                        fontSize = (DataHolder.guiConfig["mode"]!!["modeDataFontSize"] as Long).toInt().sp,
                        modifier = Modifier.padding(bottom = (DataHolder.guiConfig["mode"]!!["modeTitleBottomPadding"] as Long).toInt().dp)
                    )
                }
                radioOptions.forEach { option ->
                    val radioOption = (option as Map<String, Object>)
                    val radioText: String = (radioOption[DataHolder.internationalizeLabel("text", useInternationalNames)] as String).replace("\\n", "\n").replace("\\", "")
                    val radioId: String = radioOption["id"] as String
                    item {
                        Column(modifier = Modifier.padding(
                            (DataHolder.guiConfig["mode"]!!["radioColumnPadding"] as Long).toInt().dp
                        )) {
                            Row(
                                modifier = Modifier
                                    .selectable(
                                        selected = (radioId == selectedOption),
                                        onClick = {
                                            onOptionSelected(radioId)
                                        }
                                    )
                                    .padding(horizontal = (DataHolder.guiConfig["mode"]!!["radioHorizontalPadding"] as Long).toInt().dp)
                                    .fillMaxWidth()
                            ) {
                                RadioButton(
                                    selected = (radioId == selectedOption),
                                    onClick = { onOptionSelected(radioId) },
                                    modifier = Modifier.fillMaxSize(
                                        (DataHolder.guiConfig["mode"]!!["radioButtonFillSizeFraction"] as Long).toFloat()
                                    ),
                                    colors = RadioButtonDefaults.colors(
                                        Color(android.graphics.Color.parseColor(DataHolder.guiConfig["main"]!!["deliveryModeCardColor"] as String))
                                    )
                                )
                                Text(
                                    text = radioText,
                                    modifier = Modifier.padding(
                                        start = (DataHolder.guiConfig["mode"]!!["radioTextStartPadding"] as Long).toInt().dp,
                                        top = if (radioText.contains("\n")) {
                                            (DataHolder.guiConfig["mode"]!!["radioTextTopPadding"] as Long).toInt().dp
                                        } else {
                                            (DataHolder.guiConfig["mode"]!!["radioTextTopPaddingSingleLine"] as Long).toInt().dp
                                        }
                                    ),
                                    fontSize = (DataHolder.guiConfig["mode"]!!["radioTextFontSize"] as Long).toInt().sp
                                )
                            }
                        }
                   }
                }
            }
        }
    }
}