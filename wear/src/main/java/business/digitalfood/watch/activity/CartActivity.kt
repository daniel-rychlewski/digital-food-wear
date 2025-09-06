package business.digitalfood.watch.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.activity.ConfirmationActivity
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import business.digitalfood.watch.BuildConfig
import business.digitalfood.watch.Constants
import business.digitalfood.watch.DataHolder
import business.digitalfood.watch.DataHolder.stripeConfig
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Random
import kotlin.math.ceil
import kotlin.streams.asSequence

class CartActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val extras = intent.extras
        if (extras != null) {
            useInternationalNames = extras.getString("useInternationalNames").toBoolean()
            setContent {
                ConstructScreen(useInternationalNames)
            }
        }
    }

    @Composable
    fun ConstructScreen(useInternationalNames: Boolean) {
        val isEnableCardPaymentCheckboxTicked = remember { mutableStateOf(false) }
        val isEnableCryptoPaymentCheckboxTicked = remember { mutableStateOf(false) }

        var orderedItemsAmount = 0
        DataHolder.cart.entries.stream().filter { it.value > 0 }.forEach { entry -> orderedItemsAmount += entry.value }

        val headingFontSize = (DataHolder.guiConfig["cart"]!!["headingFontSize"] as Long).toInt().sp
        val tableFontSize = (DataHolder.guiConfig["cart"]!!["tableFontSize"] as Long).toInt().sp
        val deliveryFontSize = (DataHolder.guiConfig["cart"]!!["deliveryFontSize"] as Long).toInt().sp
        val tableOptionFontSize = (DataHolder.guiConfig["cart"]!!["tableOptionFontSize"] as Long).toInt().sp
        val termsOfUseSize = (DataHolder.guiConfig["cart"]!!["termsOfUseSize"] as Long).toInt().sp
        val descriptionPadding = Modifier.padding(top = (DataHolder.guiConfig["cart"]!!["descriptionPadding"] as Long).toInt().dp)
        val descriptionPaddingFirst = Modifier.padding(top = (DataHolder.guiConfig["cart"]!!["descriptionPaddingFirst"] as Long).toInt().dp)

        val sourceCharset = DataHolder.guiConfig["cart"]!!["sourceCharset"] as String
        orderNumber = Random().ints(DataHolder.guiConfig["cart"]!!["sourceCharsetLength"] as Long, 0, sourceCharset.length).asSequence().map(sourceCharset::get).joinToString("")

        var isOrderButtonEnabled by remember { mutableStateOf(true) }

        (DataHolder.guiConfig["mode"]!!["modeOptions"] as List<Map<String, String>>)
            .forEach { map ->
                map.forEach {
                    if (it.key == "id" && DataHolder.deliveryMode == it.value) {
                        deliveryModeSelected = map[DataHolder.internationalizeLabel("text", useInternationalNames)].toString()
                    }
                }
            }

        val scalingLazyListState = rememberScalingLazyListState(initialCenterItemIndex = 0)
        Scaffold(
            positionIndicator = {
                PositionIndicator(scalingLazyListState = scalingLazyListState)
            }
        ) {
            ScalingLazyColumn(
                state = scalingLazyListState,
                contentPadding = PaddingValues(
                    start = if (applicationContext.resources.configuration.isScreenRound) {
                        (DataHolder.ROUND_WEAR_PADDING_FACTOR * applicationContext.resources.configuration.screenWidthDp).dp
                    } else {
                        (DataHolder.guiConfig["cart"]!!["contentPaddingStart"] as Long).toInt().dp
                    },
                    end = if (applicationContext.resources.configuration.isScreenRound) {
                        (DataHolder.ROUND_WEAR_PADDING_FACTOR * applicationContext.resources.configuration.screenWidthDp).dp
                    } else {
                        (DataHolder.guiConfig["cart"]!!["contentPaddingEnd"] as Long).toInt().dp
                    }
                )
            ) {
                item {
                    val tableModifier = Modifier.padding(bottom = (DataHolder.guiConfig["cart"]!!["cartSummaryPaddingBottom"] as Long).toInt().dp)
                    Row(modifier = tableModifier) {
                        // Cart summary
                        Text(
                            text = DataHolder.guiConfig["cart"]!![DataHolder.internationalizeLabel("cartSummaryText", useInternationalNames)].toString().format(
                                if (useInternationalNames) {
                                    deliveryModeSelected.lowercase(Locale.getDefault())
                                } else {
                                    deliveryModeSelected
                                }
                            ),
                            fontSize = headingFontSize,
                            modifier = Modifier.wrapContentWidth(Alignment.Start)
                        )
                    }
                }

                totalPrice = if (DataHolder.deliveryMode == "delivery") {
                    ((DataHolder.restaurantConfig["deliveryFees"] as Map<String, Object>)[DataHolder.zip.value] ?: "0.0").toString().toDouble()
                } else {
                    0.0
                }

                foodOverview = ""

                DataHolder.reorderCart()

                val leftWeight = 0.5f
                val centerWeight = 1.9f
                val rightWeight = 1f

                DataHolder.cart.forEach { (id, amount) ->
                    if (amount > 0) {
                        val priceFormatted: String = (amount.times(
                            if (DataHolder.food.find {it.id == id} != null) {
                                DataHolder.food.find {it.id == id}?.price ?: 0.0
                            } else {
                                DataHolder.options[id]?.price ?: 0.0
                            }
                        )).let {
                            totalPrice += it
                            DataHolder.formatPrice(it, applicationContext, false)
                        }

                        val isMainFood: Boolean = DataHolder.food.find { it.id == id } != null

                        var foodName: String? = ""
                        if (isMainFood) {
                            if (useInternationalNames) {
                                foodName = DataHolder.food.find { it.id == id }?.nameEn
                            } else {
                                foodName = DataHolder.food.find { it.id == id }?.name
                            }
                        } else {
                            if (useInternationalNames) {
                                foodName = DataHolder.options[id]?.nameEn
                            } else {
                                foodName = DataHolder.options[id]?.name
                            }
                        }

                        item {
                            val rowModifier = Modifier.padding(top = if (isMainFood) {
                                (DataHolder.guiConfig["cart"]!!["mainFoodTopPadding"] as Long).toInt().dp
                            } else {
                                (DataHolder.guiConfig["cart"]!!["optionTopPadding"] as Long).toInt().dp
                            })

                            Row(modifier = rowModifier) {
                                Text(
                                    text = "" + amount,
                                    fontSize = if (isMainFood) tableFontSize else tableOptionFontSize,
                                    modifier = Modifier.weight(leftWeight)
                                )

                                Text(
                                    text = foodName!!,
                                    fontSize = if (isMainFood) tableFontSize else tableOptionFontSize,
                                    modifier = Modifier.weight(centerWeight)
                                )

                                Text(
                                    " "
                                )

                                Text(
                                    text = priceFormatted,
                                    fontSize = if (isMainFood) tableFontSize else tableOptionFontSize,
                                    modifier = Modifier.weight(rightWeight)
                                )
                            }
                        }

                        foodOverview += "\n" +
                                "                    <tr>\n" +
                                "                        <td style=\"text-align:center;vertical-align:top;"+ if (isMainFood) {"font-weight:bold;"} else {""} +"font-size:15px;width:45px;\">\n" +
                                "                            "+amount+"\n" +
                                "                        </td>\n" +
                                "                        <td></td>\n" +
                                "                        <td style=\""+if (isMainFood) {"text-align:left;vertical-align:top;"} else {""}+"\">\n" +
                                "                            <span style=\""+ if (isMainFood) {"font-size:15px;font-weight:bold;"} else {"font-size:14px;"} +"\">\n" +
                                "                                "+foodName+"\n" +
                                "                            </span>\n" +
                                "                        </td>\n" +
                                "                        <td></td>\n" +
                                "                        <td></td>\n" +
                                "                        <td style=\"text-align:right;padding-right:15px;vertical-align:top;"+ if (isMainFood) {"font-weight:bold;"} else {""} +"width:60px;font-size:15px;\">\n" +
                                "                            "+priceFormatted+"\n" +
                                "                        </td>\n" +
                                "                    </tr>"
                    }
                }
                foodOverview +=
                            "    <tr>\n" +
                            "                        <td colspan=\"7\"></td>\n" +
                            "                    </tr>"

                summarySection = "<tr>\n" +
                        "        <td>\n" +
                        "            <table style=\"margin-right:0;margin-left:auto;\">\n" +
                        "                <tbody>"

                val currency = DataHolder.restaurantConfig["currency"].toString()

                if (DataHolder.deliveryMode == "delivery") {
                    val deliveryFeeModifier = Modifier.padding(top = (DataHolder.guiConfig["cart"]!!["deliveryFeePaddingTop"] as Long).toInt().dp)
                    val deliveryFeeName: String = DataHolder.guiConfig["cart"]!![DataHolder.internationalizeLabel("deliveryFeeTitle", useInternationalNames)] as String
                    val deliveryFeeFormatted: String = DataHolder.formatPrice(((DataHolder.restaurantConfig["deliveryFees"] as Map<String, Object>)[DataHolder.zip.value] ?: "0.0").toString().toDouble(), applicationContext, false)

                    item {
                        Row(modifier = deliveryFeeModifier) {
                            Text(
                                text = "",
                                modifier = Modifier.weight(leftWeight)
                            )
                            Text(
                                text = deliveryFeeName,
                                fontSize = tableFontSize,
                                modifier = Modifier.weight(centerWeight)
                            )
                            Text(
                                " "
                            )
                            Text(
                                text = deliveryFeeFormatted,
                                fontSize = tableFontSize,
                                modifier = Modifier.weight(rightWeight)
                            )
                        }
                    }

                    summarySection += "\n" +
                            "                    <tr>\n" +
                            "                        <td style=\"text-align:right;font-size:18px;\">"+deliveryFeeName+"</td>\n" +
                            "                        <td style=\"text-align:right;font-size:18px;\">"+deliveryFeeFormatted+"</td>\n" +
                            "                        <td style=\"text-align:right;font-size:18px;\">"+currency+"</td>\n" +
                            "                    </tr>"
                }

                var transactionFee = 0.0
                var cartTotalPriceWithTransactionFees = ""
                var totalPriceWithTransactionFees = totalPrice

                if (isEnableCardPaymentCheckboxTicked.value) {
                    totalPriceWithTransactionFees = totalPrice * (1.0 / (stripeConfig["processingFees"]!!["factor"] as Double)) + (stripeConfig["processingFees"]!!["addedAmount"] as Number).toDouble()
                    transactionFee = totalPriceWithTransactionFees - totalPrice
                    cartTotalPriceWithTransactionFees = DataHolder.formatPrice(totalPriceWithTransactionFees, applicationContext, false)

                    val transactionFeeModifier = Modifier.padding(top = (DataHolder.guiConfig["cart"]!!["deliveryFeePaddingTop"] as Long).toInt().dp)
                    val transactionFeeName = DataHolder.guiConfig["cart"]!![DataHolder.internationalizeLabel("transactionFeeTitle", useInternationalNames)].toString()
                    val transactionFeeFormatted = DataHolder.formatPrice(transactionFee, applicationContext, false)

                    item {
                        Row(modifier = transactionFeeModifier) {
                            Text(
                                text = "",
                                modifier = Modifier.weight(leftWeight)
                            )
                            Text(
                                text = transactionFeeName,
                                fontSize = tableFontSize,
                                modifier = Modifier.weight(centerWeight)
                            )
                            Text(
                                " "
                            )
                            Text(
                                text = transactionFeeFormatted,
                                fontSize = tableFontSize,
                                modifier = Modifier.weight(rightWeight)
                            )
                        }
                    }

                    summarySection += "\n" +
                            "                    <tr>\n" +
                            "                        <td style=\"text-align:right;font-size:18px;\">"+transactionFeeName+"</td>\n" +
                            "                        <td style=\"text-align:right;font-size:18px;\">"+transactionFeeFormatted+"</td>\n" +
                            "                        <td style=\"text-align:right;font-size:18px;\">"+currency+"</td>\n" +
                            "                    </tr>"
                }

                val cartTotalModifier = Modifier.padding(top = (DataHolder.guiConfig["cart"]!!["cartTotalPaddingTop"] as Long).toInt().dp)
                val cartTotalSummaryText: String = DataHolder.guiConfig["cart"]!![DataHolder.internationalizeLabel("cartTotalSummaryText", useInternationalNames)] as String
                val cartTotalSummaryExclGasText: String = DataHolder.guiConfig["cart"]!![DataHolder.internationalizeLabel("cartTotalSummaryExclGas", useInternationalNames) + "Text"] as String
                val cartTotalPrice: String = DataHolder.formatPrice(totalPrice, applicationContext, false)
                val cartTotalInvoiceText: String = (DataHolder.guiConfig["cart"]!![DataHolder.internationalizeLabel(if (isEnableCryptoPaymentCheckboxTicked.value) { "cartTotalInvoiceExclGasText" } else { "cartTotalInvoiceText" }, useInternationalNames)] as String).format(DataHolder.foodVat.value)

                item {
                    Row(modifier = cartTotalModifier) {
                        Text(
                            text = "" + orderedItemsAmount, fontSize = tableFontSize, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(leftWeight)
                        )
                        Text(
                            text = if (isEnableCryptoPaymentCheckboxTicked.value) cartTotalSummaryExclGasText else cartTotalSummaryText,
                            fontSize = tableFontSize,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(centerWeight)
                        )
                        Text(
                            " "
                        )
                        Text(
                            text = if (isEnableCardPaymentCheckboxTicked.value) { cartTotalPriceWithTransactionFees } else { cartTotalPrice },
                            fontSize = tableFontSize,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(rightWeight)
                        )
                    }
                }

                summarySection += "\n" +
                        "                    <tr>\n" +
                        "                        <td style=\"text-align:right;font-weight:bold;font-size:18px;\">"+cartTotalInvoiceText+"</td>\n" +
                        "                        <td style=\"text-align:right;font-weight:bold;font-size:18px;\">"+if (isEnableCardPaymentCheckboxTicked.value) { cartTotalPriceWithTransactionFees } else { cartTotalPrice }+"</td>\n" +
                        "                        <td style=\"text-align:right;font-weight:bold;font-size:18px;\">"+currency+"</td>\n" +
                        "                    </tr>"

                summarySection += "\n                </tbody>\n" +
                                    "            </table>\n" +
                                    "        </td>\n" +
                                    "    </tr>\n" +
                                    "    <tr>\n" +
                                    "        <td>\n" +
                                    "            <br>\n" +
                                    "        </td>\n" +
                                    "    </tr>"

                if (DataHolder.isCardPaymentSupported == true) {
                    item {
                        Row(Modifier.padding(0.dp)) {
                            Checkbox(
                                modifier = Modifier.absoluteOffset((-12).dp, 0.dp),
                                checked = isEnableCardPaymentCheckboxTicked.value,
                                onCheckedChange = {
                                    isEnableCardPaymentCheckboxTicked.value = it

                                    if (DataHolder.isCryptoPaymentSupported == true && it) {
                                        isEnableCryptoPaymentCheckboxTicked.value = false
                                    }

                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["main"]!!["goUpColor"] as String)),
                                    checkmarkColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["main"]!!["goUpTintColor"] as String))
                                )
                            )
                            Text(
                                text = DataHolder.guiConfig["cart"]!![DataHolder.internationalizeLabel("useOnlinePayment", useInternationalNames)] as String,
                                modifier = Modifier
                                    .absoluteOffset((-12).dp, 0.dp)
                                    .align(Alignment.CenterVertically)
                                    .fillMaxWidth(),
                                fontSize = tableFontSize,
                                maxLines = 2
                            )
                        }
                    }
                }

                if (DataHolder.isCryptoPaymentSupported == true) {
                    item {
                        Row(Modifier.padding(0.dp)) {
                            Checkbox(
                                modifier = Modifier.absoluteOffset((-12).dp, 0.dp),
                                checked = isEnableCryptoPaymentCheckboxTicked.value,
                                onCheckedChange = {
                                    isEnableCryptoPaymentCheckboxTicked.value = it

                                    if (DataHolder.isCardPaymentSupported == true && it) {
                                        isEnableCardPaymentCheckboxTicked.value = false
                                    }
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["main"]!!["goUpColor"] as String)),
                                    checkmarkColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["main"]!!["goUpTintColor"] as String))
                                )
                            )
                            Text(
                                text = DataHolder.guiConfig["cart"]!![DataHolder.internationalizeLabel("useCryptoPayment", useInternationalNames)] as String,
                                modifier = Modifier
                                    .absoluteOffset((-12).dp, 0.dp)
                                    .align(Alignment.CenterVertically)
                                    .fillMaxWidth(),
                                fontSize = tableFontSize,
                                maxLines = 2
                            )
                        }
                    }
                }

                item {
                    Column(modifier = Modifier.padding(top = (DataHolder.guiConfig["cart"]!!["deliveryPaddingTop"] as Long).toInt().dp)) {
                        if (DataHolder.deliveryMode == "collection") {
                            Text(
                                text = (DataHolder.guiConfig["cart"]!![DataHolder.internationalizeLabel("collectionAddress", useInternationalNames)] as String),
                                fontSize = deliveryFontSize,
                                fontWeight = FontWeight.Bold,
                                modifier = descriptionPaddingFirst
                            )
                            Text(
                                text = (DataHolder.restaurantConfig["address1"].toString() + "\n" + DataHolder.restaurantConfig["address2"] + "\n" + DataHolder.restaurantConfig["address3"]),
                                fontSize = deliveryFontSize
                            )
                            Text(
                                text = (DataHolder.guiConfig["cart"]!![DataHolder.internationalizeLabel("yourDetails", useInternationalNames)] as String),
                                fontSize = deliveryFontSize,
                                fontWeight = FontWeight.Bold,
                                modifier = descriptionPadding
                            )
                            Text(
                                text = (DataHolder.guiConfig["cart"]!![DataHolder.internationalizeLabel("name", useInternationalNames)] as String) + DataHolder.name
                                        + "\n" + (DataHolder.guiConfig["cart"]!![DataHolder.internationalizeLabel("email", useInternationalNames)] as String) + DataHolder.email
                                        + "\n" + (DataHolder.guiConfig["cart"]!![DataHolder.internationalizeLabel("phone", useInternationalNames)] as String) + DataHolder.phoneNumber,
                                fontSize = deliveryFontSize
                            )
                            Text(
                                text = (DataHolder.guiConfig["cart"]!![DataHolder.internationalizeLabel("availablePaymentMethodsCollection", useInternationalNames)] as String),
                                fontSize = deliveryFontSize,
                                fontWeight = FontWeight.Bold,
                                modifier = descriptionPadding
                            )
                            Text(
                                text = (DataHolder.restaurantConfig[DataHolder.internationalizeLabel("paymentOptionsCollection", useInternationalNames)] as List<String>).joinToString(", "),
                                fontSize = deliveryFontSize
                            )
                            Text(
                                text = (DataHolder.guiConfig["cart"]!![DataHolder.internationalizeLabel("usualCollectionTime", useInternationalNames)] as String),
                                fontWeight = FontWeight.Bold,
                                fontSize = deliveryFontSize,
                                modifier = descriptionPadding
                            )
                            Text(
                                text = (DataHolder.restaurantConfig[DataHolder.internationalizeLabel("collectionTime", useInternationalNames)] as String),
                                fontSize = deliveryFontSize,
                            )
                        } else if (DataHolder.deliveryMode == "dine-in") {
                            Text(
                                text = (DataHolder.guiConfig["cart"]!![DataHolder.internationalizeLabel("dineInAddress", useInternationalNames)] as String),
                                fontSize = deliveryFontSize,
                                fontWeight = FontWeight.Bold,
                                modifier = descriptionPaddingFirst
                            )
                            Text(
                                text = (DataHolder.restaurantConfig["address1"].toString() + "\n" + DataHolder.restaurantConfig["address2"] + "\n" + DataHolder.restaurantConfig["address3"]),
                                fontSize = deliveryFontSize
                            )
                            Text(
                                text = (DataHolder.guiConfig["cart"]!![DataHolder.internationalizeLabel("yourDetails", useInternationalNames)] as String),
                                fontSize = deliveryFontSize,
                                fontWeight = FontWeight.Bold,
                                modifier = descriptionPadding
                            )
                            Text(
                                text = (DataHolder.guiConfig["cart"]!![DataHolder.internationalizeLabel("name", useInternationalNames)] as String) + DataHolder.name
                                        + "\n" + (DataHolder.guiConfig["cart"]!![DataHolder.internationalizeLabel("email", useInternationalNames)] as String) + DataHolder.email
                                        + "\n" + (DataHolder.guiConfig["cart"]!![DataHolder.internationalizeLabel("phone", useInternationalNames)] as String) + DataHolder.phoneNumber,
                                fontSize = deliveryFontSize
                            )
                            Text(
                                text = (DataHolder.guiConfig["cart"]!![DataHolder.internationalizeLabel("availablePaymentMethodsDineIn", useInternationalNames)] as String),
                                fontSize = deliveryFontSize,
                                fontWeight = FontWeight.Bold,
                                modifier = descriptionPadding
                            )
                            Text(
                                text = (DataHolder.restaurantConfig[DataHolder.internationalizeLabel("paymentOptionsDineIn", useInternationalNames)] as List<String>).joinToString(", "),
                                fontSize = deliveryFontSize
                            )
                            Text(
                                text = (DataHolder.guiConfig["cart"]!![DataHolder.internationalizeLabel("usualDineInTime", useInternationalNames)] as String),
                                fontWeight = FontWeight.Bold,
                                fontSize = deliveryFontSize,
                                modifier = descriptionPadding
                            )
                            Text(
                                text = (DataHolder.restaurantConfig[DataHolder.internationalizeLabel("dineInTime", useInternationalNames)] as String),
                                fontSize = deliveryFontSize,
                            )
                        } else if (DataHolder.deliveryMode == "delivery") {
                            Text(
                                text = (DataHolder.guiConfig["cart"]!![DataHolder.internationalizeLabel("deliveryAddress", useInternationalNames)] as String),
                                fontSize = deliveryFontSize,
                                fontWeight = FontWeight.Bold,
                                modifier = descriptionPaddingFirst
                            )
                            Text(
                                text = (DataHolder.street + "\n" + DataHolder.zip.value + " " + DataHolder.city),
                                fontSize = deliveryFontSize
                            )
                            Text(
                                text = (DataHolder.guiConfig["cart"]!![DataHolder.internationalizeLabel("yourDetails", useInternationalNames)] as String),
                                fontSize = deliveryFontSize,
                                fontWeight = FontWeight.Bold,
                                modifier = descriptionPadding
                            )
                            Text(
                                text = (DataHolder.guiConfig["cart"]!![DataHolder.internationalizeLabel("name", useInternationalNames)] as String) + DataHolder.name
                                        + "\n" + (DataHolder.guiConfig["cart"]!![DataHolder.internationalizeLabel("email", useInternationalNames)] as String) + DataHolder.email
                                        + "\n" + (DataHolder.guiConfig["cart"]!![DataHolder.internationalizeLabel("phone", useInternationalNames)] as String) + DataHolder.phoneNumber,
                                fontSize = deliveryFontSize
                            )
                            Text(
                                text = (DataHolder.guiConfig["cart"]!![DataHolder.internationalizeLabel("availablePaymentMethodsDelivery", useInternationalNames)] as String),
                                fontSize = deliveryFontSize,
                                fontWeight = FontWeight.Bold,
                                modifier = descriptionPadding
                            )
                            Text(
                                text = ((DataHolder.restaurantConfig[DataHolder.internationalizeLabel("paymentOptionsDelivery", useInternationalNames)] as List<String>).joinToString(", ")),
                                fontSize = deliveryFontSize
                            )
                            Text(
                                text = (DataHolder.guiConfig["cart"]!![DataHolder.internationalizeLabel("usualDeliveryTime", useInternationalNames)] as String),
                                fontWeight = FontWeight.Bold,
                                fontSize = deliveryFontSize,
                                modifier = descriptionPadding
                            )
                            Text(
                                text = (DataHolder.restaurantConfig[DataHolder.internationalizeLabel("deliveryTime", useInternationalNames)] as String),
                                fontSize = deliveryFontSize,
                            )
                        }
                    }
                }

                item {
                    Column(modifier = Modifier.padding(top = (DataHolder.guiConfig["cart"]!!["termsOfUsePaddingTop"] as Long).toInt().dp)) {
                        Text(
                            text = String.format(
                                DataHolder.guiConfig["cart"]!![DataHolder.internationalizeLabel("termsOfUseConfirm", useInternationalNames)] as String,
                                DataHolder.guiConfig["cart"]!![DataHolder.internationalizeLabel("cartOrderNow", useInternationalNames)] as String
                            ),
                            fontSize = termsOfUseSize
                        )
                    }
                }

                item {
                    Column {
                        var standardButtonHeight by remember { mutableStateOf(56.dp) }
                        var cryptoButtonHeight by remember { mutableStateOf(56.dp) }
                        if (!isEnableCryptoPaymentCheckboxTicked.value) {
                            Button(
                                enabled = isOrderButtonEnabled,
                                onClick = {
                                    isOrderButtonEnabled = false
                                    Handler(Looper.getMainLooper()).postDelayed({ isOrderButtonEnabled = true }, DataHolder.guiConfig["cart"]!!["failureDurationMs"] as Long)

                                    val confirmation = assets.open("confirmation.html")
                                    confirmationHtml = confirmation.readBytes().toString(Charset.defaultCharset())
                                    confirmation.close()

                                    if (isEnableCardPaymentCheckboxTicked.value) {
                                        val paymentLinkUrl: String = DataHolder.getPaymentLink(
                                            ceil(totalPriceWithTransactionFees * 100).toLong(),
                                            DataHolder.email!!,
                                            DataHolder.restaurantConfig["currencyOfficial"].toString(),
                                            useInternationalNames
                                        ) + "?prefilled_email=" + DataHolder.email

                                        val intent = Intent(this@CartActivity, PaymentActivity::class.java).apply {
                                            putExtra("paymentLinkUrl", paymentLinkUrl)
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        startActivity(intent)
                                    } else {
                                        handleOrderNowButtonClicked(deliveryModeSelected, orderNumber, foodOverview, summarySection, totalPrice, this@CartActivity, applicationContext, useInternationalNames)
                                    }
                                },
                                modifier = Modifier
                                    .padding(top = (DataHolder.guiConfig["cart"]!!["cartOrderNowPaddingTop"] as Long).toInt().dp)
                                    .fillMaxWidth()
                                    .height(standardButtonHeight),
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["cart"]!!["cartOrderNowBackgroundColor"] as String)),
                                    contentColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["cart"]!!["cartOrderNowContentColor"] as String))
                                ),
                            ) {
                                Text(
                                    text = String.format(
                                        DataHolder.guiConfig["cart"]!![DataHolder.internationalizeLabel("cartOrderNow", useInternationalNames)].toString().split("(").joinToString("\n("),
                                        DataHolder.formatPrice(if (transactionFee != 0.0) { totalPriceWithTransactionFees } else { totalPrice }, applicationContext, true)
                                    ),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(4.dp),
                                    onTextLayout = { layoutResult ->
                                        standardButtonHeight = if (layoutResult.lineCount >= 3) 74.dp else 56.dp
                                    }
                                )
                            }
                        } else {
                            Button(
                                enabled = isOrderButtonEnabled,
                                onClick = {
                                    val intent = Intent(this@CartActivity, CryptoActivity::class.java).apply {
                                        putExtra("useInternationalNames", "" + useInternationalNames)
                                        putExtra("orderNumber", "" + orderNumber)
                                        putExtra("totalPriceWithTransactionFees", "" + totalPriceWithTransactionFees)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    startActivity(intent)
                                },
                                modifier = Modifier
                                    .padding(top = (DataHolder.guiConfig["cart"]!!["cartOrderNowPaddingTop"] as Long).toInt().dp)
                                    .fillMaxWidth()
                                    .height(cryptoButtonHeight),
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["cart"]!!["cartOrderNowBackgroundColor"] as String)),
                                    contentColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["cart"]!!["cartOrderNowContentColor"] as String))
                                ),
                            ) {
                                Text(
                                    text = String.format(
                                        DataHolder.guiConfig["cart"]!![DataHolder.internationalizeLabel("cartOrderNowExclGas", useInternationalNames)].toString().split("(").joinToString("\n("),
                                        DataHolder.formatPrice(if (transactionFee != 0.0) { totalPriceWithTransactionFees } else { totalPrice }, applicationContext, true)
                                    ),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(4.dp),
                                    onTextLayout = { layoutResult ->
                                        cryptoButtonHeight = if (layoutResult.lineCount >= 3) 74.dp else 56.dp
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        var deliveryModeSelected: String = ""
        var orderNumber: String = ""
        var foodOverview: String = ""
        var summarySection: String = ""
        var totalPrice: Double = 0.0
        var useInternationalNames: Boolean = false

        var confirmationHtml: String = ""

        fun handleOrderNowButtonClicked(deliveryModeSelected: String, orderNumber: String, foodOverview: String, summarySection: String, totalPrice: Double, activity: Activity, applicationContext: Context, useInternationalNames: Boolean) {
            // With the initially fetched opening times from starting the app (from perhaps a long time ago - might wanna re-fetch upon the click of the cartOrderNow button), check if the restaurant is still open. The app might have been open in the background for a long time.
            val isOpeningTimeSatisfied = isRestaurantCurrentlyOpen()
            if (isOpeningTimeSatisfied) {
                @Suppress("KotlinConstantConditions")
                if (BuildConfig.FLAVOR != "prod") {
                    val testEmailText = DataHolder.guiConfig["cart"]!![DataHolder.internationalizeLabel("testEmailText", useInternationalNames)] as String
                    confirmationHtml = confirmationHtml.replace("#test\n",
                        "<table style=\"width: 100.0%;font-family: Calibri , serif;\">\n" +
                                "    <tbody>\n" +
                                "        <tr>\n" +
                                "            <td style=\"text-align: left;font-size: 22.0px;font-weight: bold;\">"+testEmailText+"</td>\n" +
                                "        </tr>\n" +
                                "        <tr>\n" +
                                "            <td>\n" +
                                "                <hr>\n" +
                                "            </td>\n" +
                                "        </tr>\n" +
                                "    </tbody>\n" +
                                "</table>\n"
                    )
                } else {
                    confirmationHtml = confirmationHtml.replace("#test\n", "")
                }

                confirmationHtml = confirmationHtml.replace("#restaurantAddress1", DataHolder.restaurantConfig["address1"].toString())
                confirmationHtml = confirmationHtml.replace("#restaurantAddress2", DataHolder.restaurantConfig["address2"].toString())
                confirmationHtml = confirmationHtml.replace("#restaurantAddress3", DataHolder.restaurantConfig["address3"].toString())
                confirmationHtml = confirmationHtml.replace("#restaurantVat", DataHolder.restaurantConfig["vatNumber"].toString())
                confirmationHtml = confirmationHtml.replace("#restaurantPhoneNumber", DataHolder.restaurantConfig["phoneNumber"].toString())
                confirmationHtml = confirmationHtml.replace("#name", DataHolder.name.toString())

                if (DataHolder.deliveryMode == "delivery") {
                    confirmationHtml = confirmationHtml.replace("#customerAddress", "\n" +
                            "        <tr>\n" +
                            "            <td style=\"text-align:left;\">" + DataHolder.street.toString() + "</td>\n" +
                            "        </tr>\n" +
                            "        <tr>\n" +
                            "            <td style=\"text-align:left;\">" + DataHolder.zip.value + " " + DataHolder.city + "</td>\n" +
                            "        </tr>")
                } else {
                    confirmationHtml = confirmationHtml.replace("#customerAddress", "")
                }

                confirmationHtml = confirmationHtml.replace("#phone", DataHolder.phoneNumber.toString())
                confirmationHtml = confirmationHtml.replace("#email", DataHolder.email.toString())
                confirmationHtml = confirmationHtml.replace("#orderNumberKey", DataHolder.guiConfig["cart"]!![DataHolder.internationalizeLabel("orderNumber", useInternationalNames)] as String)
                confirmationHtml = confirmationHtml.replace("#orderNumber", orderNumber)
                confirmationHtml = confirmationHtml.replace("#orderTimeKey", DataHolder.guiConfig["cart"]!![DataHolder.internationalizeLabel("orderTime", useInternationalNames)] as String)
                confirmationHtml = confirmationHtml.replace("#orderTime", SimpleDateFormat(DataHolder.guiConfig["cart"]!!["dateFormatPattern"] as String, Locale.getDefault()).format(Date()))
                confirmationHtml = confirmationHtml.replace("#deliveryModeKey", DataHolder.guiConfig["cart"]!![DataHolder.internationalizeLabel("deliveryMode", useInternationalNames)] as String)
                confirmationHtml = confirmationHtml.replace("#deliveryMode", deliveryModeSelected)
                confirmationHtml = confirmationHtml.replace("#foodOverview", foodOverview)
                confirmationHtml = confirmationHtml.replace("#summarySection", summarySection)

                var confirmationSubject = DataHolder.guiConfig["cart"]!![DataHolder.internationalizeLabel("confirmationSubject", useInternationalNames)] as String
                @Suppress("KotlinConstantConditions")
                if (BuildConfig.FLAVOR != "prod") {
                    confirmationSubject = "[TEST] $confirmationSubject"
                }
                val confirmationSubjectForUser = confirmationSubject.format(DataHolder.restaurantConfig["address1"].toString())
                val confirmationSubjectForRestaurant = confirmationSubject.format(DataHolder.name.toString())

                if (DataHolder.useWhatsApp == true) {

                    @Suppress("KotlinConstantConditions")
                    if (BuildConfig.FLAVOR == "prod") {
                        updateCumulatedOrdersPrice(
                            DataHolder.commission["endpoint"]!!["url"] as String,
                            DataHolder.commission["endpoint"]!!["method"] as String,
                            DataHolder.commission["apiKey"].toString(),
                            Constants.COLLECTION,
                            Constants.DOCUMENT,
                            totalPrice
                        ) { updatePriceSuccess ->

                            sendWhatsAppMessage(
                                DataHolder.whatsAppConfig["endpoint"]!!["url"] as String,
                                DataHolder.whatsAppConfig["endpoint"]!!["method"] as String,
                                DataHolder.whatsAppConfig["apiKey"].toString(),
                                Constants.COLLECTION,
                                Constants.DOCUMENT,
                                confirmationSubjectForUser,
                                confirmationSubjectForRestaurant,
                                convertFromHtmlToWhatsAppFormat(confirmationHtml),
                                useInternationalNames
                            ) { sendWhatsAppSuccess ->

                                if (updatePriceSuccess && sendWhatsAppSuccess) {
                                    displaySuccess(activity, applicationContext, useInternationalNames)
                                } else {
                                    displayFailure(applicationContext, DataHolder.guiConfig["cart"]!![DataHolder.internationalizeLabel("failureMessage", useInternationalNames)].toString())
                                }

                            }
                        }
                    } else {
                        sendWhatsAppMessage(
                            DataHolder.whatsAppConfig["endpoint"]!!["url"] as String,
                            DataHolder.whatsAppConfig["endpoint"]!!["method"] as String,
                            DataHolder.whatsAppConfig["apiKey"].toString(),
                            Constants.COLLECTION,
                            Constants.DOCUMENT,
                            confirmationSubjectForUser,
                            confirmationSubjectForRestaurant,
                            convertFromHtmlToWhatsAppFormat(confirmationHtml),
                            useInternationalNames
                        ) { sendWhatsAppSuccess ->

                            if (sendWhatsAppSuccess) {
                                displaySuccess(activity, applicationContext, useInternationalNames)
                            } else {
                                displayFailure(applicationContext, DataHolder.guiConfig["cart"]!![DataHolder.internationalizeLabel("failureMessage", useInternationalNames)].toString())
                            }

                        }
                    }
                } else {

                    @Suppress("KotlinConstantConditions")
                    if (BuildConfig.FLAVOR == "prod") {
                        updateCumulatedOrdersPrice(
                            DataHolder.commission["endpoint"]!!["url"] as String,
                            DataHolder.commission["endpoint"]!!["method"] as String,
                            DataHolder.commission["apiKey"].toString(),
                            Constants.COLLECTION,
                            Constants.DOCUMENT,
                            totalPrice
                        ) { updatePriceSuccess ->

                            sendEmail(
                                DataHolder.emailConfig["endpoint"]!!["url"] as String,
                                DataHolder.emailConfig["endpoint"]!!["method"] as String,
                                DataHolder.emailConfig["apiKey"].toString(),
                                Constants.COLLECTION,
                                Constants.DOCUMENT,
                                DataHolder.email.toString(),
                                confirmationHtml,
                                confirmationSubjectForUser,
                                confirmationSubjectForRestaurant,

                                ) { sendEmailSuccess ->

                                if (updatePriceSuccess && sendEmailSuccess) {
                                    displaySuccess(activity, applicationContext, useInternationalNames)
                                } else {
                                    displayFailure(applicationContext, DataHolder.guiConfig["cart"]!![DataHolder.internationalizeLabel("failureMessage", useInternationalNames)].toString())
                                }
                            }
                        }
                    } else {
                        sendEmail(
                            DataHolder.emailConfig["endpoint"]!!["url"] as String,
                            DataHolder.emailConfig["endpoint"]!!["method"] as String,
                            DataHolder.emailConfig["apiKey"].toString(),
                            Constants.COLLECTION,
                            Constants.DOCUMENT,
                            DataHolder.email.toString(),
                            confirmationHtml,
                            confirmationSubjectForUser,
                            confirmationSubjectForRestaurant,

                            ) { sendEmailSuccess ->

                            if (sendEmailSuccess) {
                                displaySuccess(activity, applicationContext, useInternationalNames)
                            } else {
                                displayFailure(applicationContext, DataHolder.guiConfig["cart"]!![DataHolder.internationalizeLabel("failureMessage", useInternationalNames)].toString())
                            }
                        }
                    }
                }
            } else {
                displayFailure(applicationContext, DataHolder.guiConfig["cart"]!![DataHolder.internationalizeLabel("restaurantClosedMessage", useInternationalNames)].toString())
            }
        }

        private fun convertFromHtmlToWhatsAppFormat(confirmationHtml: String): List<String> {
            val document: Document = Jsoup.parse(confirmationHtml)
            // Graph API: "Param text cannot have new-line/tab characters or more than 4 consecutive spaces". Sudden change in 01.2025. Therefore, use ArrayList<String> instead of a single String so that can use multiple parameters for line breaks.
            val result: ArrayList<String> = ArrayList()

            // Select all tables
            val tables = document.select("table[style='width:100%;font-family:Calibri, serif;']")

            // 1. Restaurant Information Table
            if (tables.isNotEmpty()) {
                val stringBuilder = StringBuilder()
                val restaurantTable = tables[0]
                for (row in restaurantTable.select("tr")) {
                    val cell = row.select("td").firstOrNull()
                    if (cell != null) {
                        val text = cell.text().trim()
                        val style = cell.attr("style")
                        if (text.isNotEmpty()) {
                            if (style.contains("font-weight:bold", ignoreCase = true)) {
                                stringBuilder.append("*$text*    ")
                            } else {
                                stringBuilder.append("$text    ")
                            }
                        }
                    }
                }
                while (stringBuilder.contains("        ")) {
                    stringBuilder.replace(stringBuilder.indexOf("        "), stringBuilder.indexOf("        ") + 8, "    ")
                }
                result.add(stringBuilder.toString().trim())
            }

            // 2. Customer Information Table
            if (tables.size > 1) {
                val stringBuilder = StringBuilder()
                val customerTable = tables[1]
                for (row in customerTable.select("tr")) {
                    val cell = row.select("td").firstOrNull()
                    if (cell != null) {
                        val text = cell.text().trim()
                        val style = cell.attr("style")
                        if (text.isNotEmpty()) {
                            if (style.contains("font-weight:bold", ignoreCase = true)) {
                                stringBuilder.append("*$text*    ")
                            } else {
                                stringBuilder.append("$text    ")
                            }
                        }
                    }
                }
                while (stringBuilder.contains("        ")) {
                    stringBuilder.replace(stringBuilder.indexOf("        "), stringBuilder.indexOf("        ") + 8, "    ")
                }
                result.add(stringBuilder.toString().trim())
            }

            // 3. Order Details Table
            if (tables.size > 2) {
                var stringBuilder = StringBuilder()
                val deliveryModeBuilder = StringBuilder()
                val lastTable = tables[2]
                val rows = lastTable.select("tr")

                val orderDetailsTable = rows[1].select("table")[0]
                val foodTable = rows[6].select("table")[0]
                val totalTable = rows[rows.size - 3]

                val orderDetailsEntries = orderDetailsTable.select("tr")
                for (index in 0 until orderDetailsEntries.size) {
                    // Check if this is the last entry
                    val isLastEntry = index == orderDetailsEntries.size - 1

                    orderDetailsEntries[index].select("td").forEachIndexed { detailsIndex, entry ->
                        if (detailsIndex == 0) {
                            // First column: bold text
                            if (isLastEntry) {
                                deliveryModeBuilder.append("*${entry.text()}* ")
                            } else {
                                stringBuilder.append("*${entry.text()}* ")
                            }
                        } else if (detailsIndex == 1) {
                            // Second column: regular text with extra spaces
                            if (isLastEntry) {
                                deliveryModeBuilder.append("${entry.text()}    ")
                            } else {
                                stringBuilder.append("${entry.text()}    ")
                            }
                        }
                    }
                }
                while (stringBuilder.contains("        ")) {
                    stringBuilder.replace(stringBuilder.indexOf("        "), stringBuilder.indexOf("        ") + 8, "    ")
                }
                result.add(stringBuilder.toString().trim())

                while (deliveryModeBuilder.contains("        ")) {
                    deliveryModeBuilder.replace(deliveryModeBuilder.indexOf("        "), deliveryModeBuilder.indexOf("        ") + 8, "    ")
                }
                result.add(deliveryModeBuilder.toString().trim())

                stringBuilder = StringBuilder()
                val foodTableEntries = foodTable.select("tr")
                for (index in 0 until foodTableEntries.size - 1) {
                    foodTableEntries[index].select("td").forEachIndexed { foodIndex, it ->
                        if (foodIndex == 0) {
                            if (it.attr("style").contains("font-weight:bold", ignoreCase = true)) {
                                stringBuilder.append("*"+it.text()+"x ")
                            } else {
                                stringBuilder.append(it.text()+"x ")
                            }
                        } else if (foodIndex == 2) {
                            stringBuilder.append(it.text().trim() + ": ")
                        } else if (foodIndex == 5) {
                            if (it.attr("style").contains("font-weight:bold", ignoreCase = true)) {
                                stringBuilder.append(it.text()+"*"+"    ")
                            } else {
                                stringBuilder.append(it.text()+"    ")
                            }
                        }
                    }
                }
                while (stringBuilder.contains("        ")) {
                    stringBuilder.replace(stringBuilder.indexOf("        "), stringBuilder.indexOf("        ") + 8, "    ")
                }
                result.add(stringBuilder.toString().trim())

                stringBuilder = StringBuilder()
                if (DataHolder.deliveryMode == "delivery") {
                    val deliveryFeeTable = rows[rows.size - 4]
                    val deliveryFeeTableEntries = deliveryFeeTable.select("td")
                    deliveryFeeTableEntries.forEachIndexed { index, entry ->
                        if (index == 0) {
                            stringBuilder.append("${entry.text()} ")
                        } else {
                            stringBuilder.append(entry.text())
                        }
                    }
                    while (stringBuilder.contains("        ")) {
                        stringBuilder.replace(stringBuilder.indexOf("        "), stringBuilder.indexOf("        ") + 8, "    ")
                    }
                    stringBuilder.append("    ")
                }
                val totalTableEntries = totalTable.select("td")
                totalTableEntries.forEachIndexed { index, entry ->
                    if (index == 0) {
                        stringBuilder.append("*${entry.text()} ")
                    } else if (index == 1) {
                        stringBuilder.append(entry.text())
                    } else {
                        stringBuilder.append("${entry.text()}*")
                    }
                }
                while (stringBuilder.contains("        ")) {
                    stringBuilder.replace(stringBuilder.indexOf("        "), stringBuilder.indexOf("        ") + 8, "    ")
                }
                result.add(stringBuilder.toString().trim())
            }

            return result
        }

        private fun displaySuccess(activity: Activity, applicationContext: Context, useInternationalNames: Boolean) {
            val intent = Intent(applicationContext, ConfirmationActivity::class.java)
            intent.putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE, ConfirmationActivity.SUCCESS_ANIMATION)
            intent.putExtra(ConfirmationActivity.EXTRA_MESSAGE, DataHolder.guiConfig["cart"]!![DataHolder.internationalizeLabel("successMessage", useInternationalNames)].toString())
            intent.putExtra(ConfirmationActivity.EXTRA_ANIMATION_DURATION_MILLIS, (DataHolder.guiConfig["cart"]!!["confirmationDurationMs"] as Long).toInt())
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            applicationContext.startActivity(intent)
            DataHolder.playHaptic(applicationContext, true)

            Handler(Looper.getMainLooper()).postDelayed(
                { activity.finishAffinity() },
                DataHolder.guiConfig["cart"]!!["confirmationDurationMs"] as Long
            )
        }

        fun displayFailure(applicationContext: Context, message: String) {
            val intent = Intent(applicationContext, ConfirmationActivity::class.java)
            intent.putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE, ConfirmationActivity.FAILURE_ANIMATION)
            intent.putExtra(ConfirmationActivity.EXTRA_MESSAGE, message)
            intent.putExtra(ConfirmationActivity.EXTRA_ANIMATION_DURATION_MILLIS, (DataHolder.guiConfig["cart"]!!["failureDurationMs"] as Long).toInt())
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            applicationContext.startActivity(intent)
            DataHolder.playHaptic(applicationContext, false)
        }

        fun updateCumulatedOrdersPrice(
            url: String,
            method: String,
            apiKey: String,
            collection: String,
            document: String,
            price: Double,
            onComplete: (Boolean) -> Unit
        ) {
            val handler = Handler(Looper.getMainLooper())

            Thread {
                var success = true

                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = method
                connection.setRequestProperty("apiKey", apiKey)
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                val jsonObject = JSONObject().apply {
                    put("collection", collection)
                    put("document", document)
                    put("price", price)
                }

                val body = jsonObject.toString()

                try {
                    val outputStream = OutputStreamWriter(connection.outputStream)
                    outputStream.write(body)
                    outputStream.flush()

                    val responseCode = connection.responseCode
                    if (responseCode != 200) {
                        success = false
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                    success = false
                } finally {
                    connection.disconnect()
                    handler.post { onComplete(success) }
                }
            }.start()
        }

        fun sendWhatsAppMessage(
            url: String,
            method: String,
            apiKey: String,
            collection: String,
            document: String,
            userTitle: String,
            restaurantTitle: String,
            message: List<String>,
            useInternationalNames: Boolean,
            onComplete: (Boolean) -> Unit
        ) {
            val handler = Handler(Looper.getMainLooper())

            Thread {
                var success = true

                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = method
                connection.setRequestProperty("apiKey", apiKey)
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                val jsonObject = JSONObject().apply {
                    put("collection", collection)
                    put("document", document)
                    put("phoneNumber", DataHolder.phoneNumber)
                    put("userTitle", userTitle)
                    put("restaurantTitle", restaurantTitle)
                    put("messageFirst", message[0])
                    put("messageSecond", message[1])
                    put("messageThird", message[2])
                    put("messageFourth", message[3])
                    put("messageFifth", message[4])
                    put("messageSixth", message[5])
                    put("language", if (useInternationalNames) { "en" } else { Constants.LOCAL_LANGUAGE })
                    put("templateName", DataHolder.whatsAppConfig["templateName"].toString())
                }

                val body = jsonObject.toString()

                try {
                    val outputStream = OutputStreamWriter(connection.outputStream)
                    outputStream.write(body)
                    outputStream.flush()

                    val responseCode = connection.responseCode
                    if (responseCode != 200) {
                        success = false
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                    success = false
                } finally {
                    connection.disconnect()
                    handler.post { onComplete(success) }
                }
            }.start()
        }

        fun sendEmail(
            url: String,
            method: String,
            apiKey: String,
            collection: String,
            document: String,
            email: String,
            message: String,
            userSubject: String,
            restaurantSubject: String,
            onComplete: (Boolean) -> Unit
        ) {
            val handler = Handler(Looper.getMainLooper())

            Thread {
                var success = true

                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = method
                connection.setRequestProperty("apiKey", apiKey)
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                val jsonObject = JSONObject().apply {
                    put("collection", collection)
                    put("document", document)
                    put("email", email)
                    put("message", message)
                    put("userSubject", userSubject)
                    put("restaurantSubject", restaurantSubject)
                }

                val body = jsonObject.toString()

                try {
                    val outputStream = OutputStreamWriter(connection.outputStream)
                    outputStream.write(body)
                    outputStream.flush()

                    val responseCode = connection.responseCode
                    if (responseCode != 200) {
                        success = false
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                    success = false
                } finally {
                    connection.disconnect()
                    handler.post { onComplete(success) }

                }
            }.start()
        }
    }
}
