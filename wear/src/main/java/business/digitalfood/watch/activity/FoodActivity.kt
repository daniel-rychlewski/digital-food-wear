package business.digitalfood.watch.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import business.digitalfood.watch.DataHolder
import kotlin.properties.Delegates

class FoodActivity : ComponentActivity() {
    companion object {
        var optionSelectionSuccessful: Boolean? = null
        var nextOptionsScreen = 0 // to handle showing multiple option screens, one after another. starts from 0

        var id by Delegates.notNull<String>()
        var numberOfOptions by Delegates.notNull<Int>() // how many options does the food have for the user to choose from
        var useInternationalNames by Delegates.notNull<Boolean>()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val extras = intent.extras
        if (extras != null) {
            id = extras.getString("id")!!
            val name = extras.getString("name")
            val nameEn = extras.getString("nameEn")
            val description = extras.getString("description")
            val descriptionEn = extras.getString("descriptionEn")
            val isCategory = extras.getString("isCategory").toBoolean()
            numberOfOptions = extras.getString("numberOfOptions")!!.toInt()
            useInternationalNames = extras.getString("useInternationalNames").toBoolean()

            setContent {
                ConstructScreen(
                    "" + id,
                    if (useInternationalNames) { nameEn ?: "" } else { name ?: "" },
                    if (useInternationalNames) { descriptionEn ?: "" } else { description ?: "" },
                    isCategory,
                    numberOfOptions,
                    applicationContext,
                    useInternationalNames
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (optionSelectionSuccessful == false) {
            if (nextOptionsScreen > 0) {
                // rollback the preemptively increased quantities
                DataHolder.cart[id.toInt()] = DataHolder.cart[id.toInt()]!! - 1

                val mapping = DataHolder.foodToOptionsMapping[id.toInt()]
                if (mapping!!.size == DataHolder.cart[id.toInt()]!! + 1) { // ensure that the options of the correct food (i.e. where the option selection was unsuccessful) are removed, not of a different instance of the same food where the option selection was successful
                    mapping[mapping.size - 1].forEach { entry ->
                        DataHolder.cart[entry] = DataHolder.cart[entry]!! - 1
                    }
                    mapping.removeAt(mapping.size - 1)
                    DataHolder.foodToOptionsMapping[id.toInt()] = mapping
                }
                DataHolder.totalPrice.postValue(DataHolder.calculateCartPrice(DataHolder.cart))
            }
            optionSelectionSuccessful = null
            nextOptionsScreen = 0
            finish()
        } else if (optionSelectionSuccessful == true) {
            if (numberOfOptions > nextOptionsScreen) {
                // open next screen
                optionSelectionSuccessful = null
                val intent = Intent(applicationContext, OptionsActivity::class.java)
                intent.putExtra("id", "" + id)
                intent.putExtra("optionScreenNumber", "" + nextOptionsScreen)
                nextOptionsScreen++
                intent.putExtra("useInternationalNames", "" + useInternationalNames)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                applicationContext.startActivity(intent)
            } else {
                nextOptionsScreen = 0
                optionSelectionSuccessful = null
            }
        }
    }

    @Composable
    fun ConstructScreen(id: String, name: String, description: String, isCategory: Boolean, numberOfOptions: Int, context: Context, useInternationalNames: Boolean) {
        var count by remember {
            mutableStateOf(
                DataHolder.cart[id.toInt()]
            )
        }
        val scalingLazyListState = rememberScalingLazyListState(initialCenterItemIndex = 1)

        Scaffold(
            positionIndicator = {
                PositionIndicator(scalingLazyListState = scalingLazyListState)
            }
        ) {
            ScalingLazyColumn(
                state = scalingLazyListState,
                contentPadding = if (applicationContext.resources.configuration.isScreenRound) {
                    PaddingValues(
                        start = (DataHolder.ROUND_WEAR_PADDING_FACTOR * applicationContext.resources.configuration.screenWidthDp).dp,
                        end = (DataHolder.ROUND_WEAR_PADDING_FACTOR * applicationContext.resources.configuration.screenWidthDp).dp,
                        top = (DataHolder.ROUND_WEAR_PADDING_FACTOR * applicationContext.resources.configuration.screenHeightDp).dp,
                        bottom = (DataHolder.ROUND_WEAR_PADDING_FACTOR * applicationContext.resources.configuration.screenHeightDp).dp
                    )
                } else {
                    PaddingValues(
                        start = (DataHolder.guiConfig["food"]!!["contentPaddingStart"] as Long).toInt().dp,
                        top = (DataHolder.guiConfig["food"]!!["contentPaddingTop"] as Long).toInt().dp,
                        end = (DataHolder.guiConfig["food"]!!["contentPaddingEnd"] as Long).toInt().dp,
                        bottom = (DataHolder.guiConfig["food"]!!["contentPaddingBottom"] as Long).toInt().dp
                    )
                }
            ) {
                val maxIndex = if (isCategory) { 1 } else { 2 }
                for (index in 0..maxIndex) {
                    when (index) {
                        0 -> {
                            item {
                                Text(text = name, fontSize = (DataHolder.guiConfig["food"]!!["titleFontSize"] as Long).toInt().sp, modifier = Modifier.fillMaxWidth())
                            }
                        }
                        1 -> {
                            item {
                                if (description != "") {
                                    Row(Modifier.padding(bottom = (DataHolder.guiConfig["food"]!!["descriptionPaddingTop"] as Long).toInt().dp)) {
                                        Text(text = description, fontSize = (DataHolder.guiConfig["food"]!!["descriptionFontSize"] as Long).toInt().sp, modifier = Modifier.fillMaxWidth())
                                    }
                                }
                            }
                        }
                        2 -> {
                            item {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Button(
                                        onClick = {
                                            count = count!! - 1
                                            DataHolder.cart[id.toInt()] = count!!
                                            if (numberOfOptions > 0) {
                                                // need to remove more than just the main product. LIFO
                                                val mapping = DataHolder.foodToOptionsMapping[id.toInt()]
                                                mapping!![mapping.size - 1].forEach { entry ->
                                                    DataHolder.cart[entry] = DataHolder.cart[entry]!! - 1
                                                }
                                                mapping.removeAt(mapping.size - 1)
                                                DataHolder.foodToOptionsMapping[id.toInt()] = mapping
                                            }
                                            DataHolder.totalPrice.postValue(DataHolder.calculateCartPrice(DataHolder.cart))
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            backgroundColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["food"]!!["minusButtonBackgroundColor"] as String)),
                                            contentColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["food"]!!["minusButtonContentColor"] as String))
                                        ),
                                        enabled = count != null && count!! > 0
                                    ) {
                                        Text(text = "-")
                                    }

                                    Text(textAlign = TextAlign.Center, text = "" + (count ?: 0), fontSize = (DataHolder.guiConfig["food"]!!["countFontSize"] as Long).toInt().sp)

                                    Button(
                                        onClick = {
                                            if (count != null) {
                                                if (nextOptionsScreen == 0) {
                                                    count = count!! + 1
                                                }
                                            } else {
                                                count = 1
                                            }
                                            if (numberOfOptions > 0) {
                                                // add another popup for user to choose the option
                                                val intent = Intent(context, OptionsActivity::class.java)
                                                intent.putExtra("id", "" + id)
                                                intent.putExtra("optionScreenNumber", "" + nextOptionsScreen)
                                                nextOptionsScreen++
                                                intent.putExtra("useInternationalNames", "" + useInternationalNames)
                                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                context.startActivity(intent)
                                            } else {
                                                DataHolder.cart[id.toInt()] = count!!
                                                DataHolder.totalPrice.postValue(DataHolder.calculateCartPrice(DataHolder.cart))
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            backgroundColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["food"]!!["plusButtonBackgroundColor"] as String)),
                                            contentColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["food"]!!["plusButtonContentColor"] as String))
                                        )
                                    ) {
                                        Text(text = "+")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}