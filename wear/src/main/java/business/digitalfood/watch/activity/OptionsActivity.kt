package business.digitalfood.watch.activity

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import business.digitalfood.watch.DataHolder
import kotlin.String

class OptionsActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FoodActivity.optionSelectionSuccessful = false

        val extras = intent.extras
        if (extras != null) {
            val id = extras.getString("id")
            val optionScreenNumber = extras.getString("optionScreenNumber")!!.toInt()
            val useInternationalNames = extras.getString("useInternationalNames").toBoolean()
            setContent {
                ConstructScreen(
                    "" + id,
                    optionScreenNumber,
                    applicationContext,
                    useInternationalNames
                )
            }
        }
    }

    @Composable
    fun ConstructScreen(id: String, optionScreenNumber: Int, context: Context, useInternationalNames: Boolean) {
        val foodOptions = DataHolder.food
            .find { it.id == Integer.parseInt(id) }
            ?.options

        if (foodOptions != null) {
            val foodOpt = (foodOptions.options as List<HashMap<*, *>>)[optionScreenNumber]
            val config = foodOpt["config"] as HashMap<*, *>
            val content = foodOpt["content"]
            val mode = config["mode"] // single or multi
            val name = config["name"] as String
            val nameEn = config["nameEn"] as String?
            val titleHasImage = config["hasImage"] as Boolean?
            val titleBackgroundPath = config["background"] as String?

            var backgroundBitmap: BitmapPainter? = null // if image fetched from the cloud into the shared preferences
            val titleBackground: Int = if (titleHasImage == true) { // if image exists in the resources folder (standard case)
                context.resources.getIdentifier("@drawable/$titleBackgroundPath", null, context.packageName)
            } else {
                -1
            }
            val base64ImageString = DataHolder.sharedPreferences.getString(titleBackgroundPath, null)
            if (base64ImageString != null) {
                val decodedByteArray = android.util.Base64.decode(base64ImageString, android.util.Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(decodedByteArray, 0, decodedByteArray.size)
                backgroundBitmap = BitmapPainter(bitmap.asImageBitmap())
            }

            if (optionScreenNumber == 0) {
                // increase parent quantity before selection so that it gets updated on the screen. Upon unsuccessful selection, this is rolled back
                DataHolder.cart[id.toInt()] = (DataHolder.cart[id.toInt()] ?: 0) + 1
                if (DataHolder.foodToOptionsMapping[id.toInt()] == null) {
                    DataHolder.foodToOptionsMapping[id.toInt()] = ArrayList()
                }
                DataHolder.totalPrice.postValue(DataHolder.calculateCartPrice(DataHolder.cart))
            }

            val listState = rememberScalingLazyListState()

            Scaffold(
                positionIndicator = {
                    PositionIndicator(scalingLazyListState = listState)
                }) {

                if (mode == "single") {
                    ScalingLazyColumn(
                        modifier = if (applicationContext.resources.configuration.isScreenRound) {
                            Modifier.padding(
                                start = ((DataHolder.ROUND_WEAR_PADDING_FACTOR / 5) * context.resources.configuration.screenWidthDp).dp,
                                end = ((DataHolder.ROUND_WEAR_PADDING_FACTOR / 5) * context.resources.configuration.screenWidthDp).dp,
                            ).fillMaxSize()
                        } else {
                            Modifier.fillMaxSize()
                        },
                        autoCentering = AutoCenteringParams(itemIndex = 0),
                        state = listState
                    ) {

                        val contentModifier = Modifier
                            .height((DataHolder.guiConfig["main"]!!["optionsHeight"] as Long).toInt().dp)
                            .padding(bottom = (DataHolder.guiConfig["main"]!!["paddingBottom"] as Long).toInt().dp)
                            .fillMaxWidth()

                        item(-1) {
                            OptionChip(
                                id.toInt(),
                                null,
                                name,
                                nameEn ?: "",
                                null,
                                titleBackground,
                                backgroundBitmap,
                                useInternationalNames,
                                contentModifier,
                                optionScreenNumber == 0,
                                null,
                                null
                            )
                        }

                        (content as List<*>).forEachIndexed { key, it ->

                            val optionEntry = it as HashMap<*,*>

                            val entryHasImage = optionEntry["hasImage"] as Boolean?
                            val entryBackgroundPath = optionEntry["background"] as String?

                            var entryBitmap: BitmapPainter? = null // if image fetched from the cloud into the shared preferences
                            val entryBackground: Int = if (entryHasImage == true) { // if image exists in the resources folder (standard case)
                                context.resources.getIdentifier("@drawable/$entryBackgroundPath", null, context.packageName)
                            } else {
                                -1
                            }

                            val entryBase64ImageString = DataHolder.sharedPreferences.getString(entryBackgroundPath, null)
                            if (entryBase64ImageString != null) {
                                val decodedByteArray = android.util.Base64.decode(entryBase64ImageString, android.util.Base64.DEFAULT)
                                val bitmap = BitmapFactory.decodeByteArray(decodedByteArray, 0, decodedByteArray.size)
                                entryBitmap = BitmapPainter(bitmap.asImageBitmap())
                            }

                            item(key) {
                                val price: String =
                                    DataHolder.formatPrice(if (optionEntry["price"] is String) { (optionEntry["price"] as String).toDouble() } else { (optionEntry["price"] as Number).toDouble() }, context)
                                OptionChip(
                                    id.toInt(),
                                    optionEntry["id"] as Long,
                                    optionEntry["name"] as String,
                                    if (optionEntry["nameEn"] != null) { optionEntry["nameEn"] as String } else { "" },
                                    price,
                                    entryBackground,
                                    entryBitmap,
                                    useInternationalNames,
                                    contentModifier,
                                    optionScreenNumber == 0,
                                    optionEntry["availableFrom"] as String?,
                                    optionEntry["availableTo"] as String?
                                )
                            }
                        }
                    }
                } else if (mode == "multi") {
                    FoodActivity.optionSelectionSuccessful = true // user can choose anything in a multi-option, it is a valid selection. Furthermore, exiting the multi-options menu is the correct way to exit the screen, not an exceptional case of cancelling the selection (as in the single option chips)
                    if (optionScreenNumber == 0) {
                        DataHolder.foodToOptionsMapping[id.toInt()]?.add(ArrayList())
                    }

                    ScalingLazyColumn(
                        modifier = if (applicationContext.resources.configuration.isScreenRound) {
                            Modifier.padding(
                                start = ((DataHolder.ROUND_WEAR_PADDING_FACTOR / 5) * context.resources.configuration.screenWidthDp).dp,
                                end = ((DataHolder.ROUND_WEAR_PADDING_FACTOR / 5) * context.resources.configuration.screenWidthDp).dp,
                            ).fillMaxSize()
                        } else {
                            Modifier.fillMaxSize()
                        },
                        autoCentering = AutoCenteringParams(itemIndex = 0),
                        state = listState
                    ) {

                        val contentModifier = Modifier
                            .height((DataHolder.guiConfig["main"]!!["optionsHeight"] as Long).toInt().dp)
                            .padding(bottom = (DataHolder.guiConfig["main"]!!["paddingBottom"] as Long).toInt().dp)
                            .fillMaxWidth()

                        item(-1) {
                            OptionChip(
                                id.toInt(),
                                null,
                                name,
                                nameEn ?: "",
                                null,
                                titleBackground,
                                backgroundBitmap,
                                useInternationalNames,
                                contentModifier,
                                optionScreenNumber == 0,
                                null,
                                null
                            )
                        }

                        (content as List<*>).forEachIndexed { key, it ->

                            val optionEntry = it as HashMap<*,*>

                            item(key) {
                                val price: String =
                                    DataHolder.formatPrice(if (optionEntry["price"] is String) { (optionEntry["price"] as String).toDouble() } else { (optionEntry["price"] as Number).toDouble() }, context)
                                OptionMultiChip(
                                    id.toInt(),
                                    optionEntry["id"] as Long,
                                    optionEntry["name"] as String,
                                    if (optionEntry["nameEn"] != null) { optionEntry["nameEn"] as String } else { "" },
                                    price,
                                    useInternationalNames,
                                    contentModifier,
                                    optionEntry["availableFrom"] as String?,
                                    optionEntry["availableTo"] as String?
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}