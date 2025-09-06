package business.digitalfood.watch.activity

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import business.digitalfood.watch.DataHolder
import business.digitalfood.watch.activity.CartActivity.Companion.confirmationHtml
import business.digitalfood.watch.activity.CartActivity.Companion.deliveryModeSelected
import business.digitalfood.watch.activity.CartActivity.Companion.displayFailure
import business.digitalfood.watch.activity.CartActivity.Companion.foodOverview
import business.digitalfood.watch.activity.CartActivity.Companion.handleOrderNowButtonClicked
import business.digitalfood.watch.activity.CartActivity.Companion.summarySection
import business.digitalfood.watch.activity.CartActivity.Companion.totalPrice
import com.lightspark.composeqr.QrCodeView
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

class CryptoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val extras = intent.extras
        if (extras != null) {
            useInternationalNames = extras.getString("useInternationalNames").toBoolean()
            orderNumber = extras.getString("orderNumber")!!
            totalPriceWithTransactionFees = extras.getString("totalPriceWithTransactionFees")!!.toDouble()

            setContent {
                ConstructScreen(useInternationalNames, orderNumber, totalPriceWithTransactionFees) {
                    val confirmation = assets.open("confirmation.html")
                    confirmationHtml = confirmation.readBytes().toString(Charset.defaultCharset())
                    confirmation.close()

                    handleOrderNowButtonClicked(deliveryModeSelected, orderNumber, foodOverview, summarySection, totalPrice, this@CryptoActivity, applicationContext, useInternationalNames)
                }
            }
        }
    }

    companion object {
        var useInternationalNames: Boolean = false
        var orderNumber: String = ""
        var totalPriceWithTransactionFees: Double = Double.MAX_VALUE

        val currencies = mapOf(
            "solana" to mapOf("usdc" to "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v", "usdt" to "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB"),
            "polygon" to mapOf("usdc" to "0x3c499c542cef5e3811e1192ce70d8cc03d5c3359", "usdt" to "0xc2132d05d31c914a87c6611c10748aeb04b58e8f")
        )
    }

    @OptIn(ExperimentalWearFoundationApi::class)
    @Composable
    fun ConstructScreen(useInternationalNames: Boolean,
                        orderNumber: String,
                        totalPriceWithTransactionFees: Double,
                        onPaymentResult: () -> Unit) {

        var isPolygonTicked by remember { mutableStateOf(true) }
        var isSolanaTicked by remember { mutableStateOf(false) }

        var isUsdcTicked by remember { mutableStateOf(true) }
        var isUsdtTicked by remember { mutableStateOf(false) }

        var isNativeTicked by remember { mutableStateOf(true) }
        var isMetamaskTicked by remember { mutableStateOf(false) }
        var isTrustTicked by remember { mutableStateOf(false) }

        var nativeAmount by remember { mutableStateOf<String?>(null) }
        var metaMaskAmount by remember { mutableStateOf<String?>(null) }
        var trustAmount by remember { mutableStateOf<String?>(null) }

        var minimumAcceptablePaymentThreshold by remember { mutableStateOf<Double?>(null) }

        var isOrderButtonEnabled by remember { mutableStateOf(true) }
        var qrLink by remember { mutableStateOf("") }

        var readyForQrDisplay by remember { mutableStateOf(false) }

        fetchExchangeRate(totalPriceWithTransactionFees) {
            nativeAmount = it.toString().replace(",", "")
            metaMaskAmount = (it * 1_000_000).toString().replace(",", "") /* for crypto, give amount in terms of smallest unit */
            trustAmount = it.toString()
            minimumAcceptablePaymentThreshold = it - 0.0001 // that is the cut-off of precision for Metamask wallet upon scanning the QR code, so choose that amount less as the minimum to check for. Trust precision would be 0.000001 i.e. overruled by Metamask
            qrLink = refreshCode(isPolygonTicked, isSolanaTicked, isUsdcTicked, isUsdtTicked, isNativeTicked, isMetamaskTicked, isTrustTicked, orderNumber, nativeAmount!!, metaMaskAmount!!, trustAmount!!)
            readyForQrDisplay = true
        }

        val scalingLazyListState = rememberScalingLazyListState(initialCenterItemIndex = 0)
        Scaffold(positionIndicator = {
            PositionIndicator(scalingLazyListState = scalingLazyListState)
        }) {
            val focusRequester = rememberActiveFocusRequester()
            val coroutineScope = rememberCoroutineScope()
            ScalingLazyColumn(
                state = scalingLazyListState,
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
                    .focusRequester(focusRequester)
                    .focusable()
            ) {
                item {
                    Text(
                        text = DataHolder.guiConfig["crypto"]!![DataHolder.internationalizeLabel("headlineText", useInternationalNames)] as String,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                item {
                    Text(DataHolder.guiConfig["crypto"]!![DataHolder.internationalizeLabel("networkText", useInternationalNames)] as String)

                    Row(Modifier.padding(top = 4.dp)) {
                        Checkbox(
                            modifier = Modifier
                                .absoluteOffset((-12).dp, 0.dp),
                            checked = isPolygonTicked,
                            onCheckedChange = {
                                isPolygonTicked = it
                                isSolanaTicked = !it

                                qrLink = refreshCode(isPolygonTicked, isSolanaTicked, isUsdcTicked, isUsdtTicked, isNativeTicked, isMetamaskTicked, isTrustTicked, orderNumber, nativeAmount!!, metaMaskAmount!!, trustAmount!!)
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["main"]!!["goUpColor"] as String)),
                                checkmarkColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["main"]!!["goUpTintColor"] as String))
                            )
                        )
                        Text(
                            text = "Polygon",
                            modifier = Modifier
                                .absoluteOffset((-12).dp, 0.dp)
                                .align(Alignment.CenterVertically)
                                .fillMaxWidth()
                                .padding(bottom = 0.dp),
                            fontSize = 18.sp,
                            maxLines = 2
                        )
                    }
                }
                item {

                    Row(Modifier.padding(0.dp)) {
                        Checkbox(
                            modifier = Modifier.absoluteOffset((-12).dp, 0.dp),
                            checked = isSolanaTicked,
                            onCheckedChange = {
                                isSolanaTicked = it
                                isPolygonTicked = !it

                                if (isMetamaskTicked) {
                                    isMetamaskTicked = false // Metamask does not support it
                                    isNativeTicked = true
                                }

                                qrLink = refreshCode(isPolygonTicked, isSolanaTicked, isUsdcTicked, isUsdtTicked, isNativeTicked, isMetamaskTicked, isTrustTicked, orderNumber, nativeAmount!!, metaMaskAmount!!, trustAmount!!)
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["main"]!!["goUpColor"] as String)),
                                checkmarkColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["main"]!!["goUpTintColor"] as String))
                            )
                        )
                        Text(
                            text = "Solana",
                            modifier = Modifier
                                .absoluteOffset((-12).dp, 0.dp)
                                .align(Alignment.CenterVertically)
                                .fillMaxWidth(),
                            fontSize = 18.sp,
                            maxLines = 2
                        )
                    }
                }

                item {
                    Text(DataHolder.guiConfig["crypto"]!![DataHolder.internationalizeLabel("currencyText", useInternationalNames)] as String)

                    Row(Modifier.padding(4.dp)) {
                        Checkbox(
                            modifier = Modifier.absoluteOffset((-12).dp, 0.dp),
                            checked = isUsdcTicked,
                            onCheckedChange = {
                                isUsdcTicked = it
                                isUsdtTicked = !it

                                qrLink = refreshCode(isPolygonTicked, isSolanaTicked, isUsdcTicked, isUsdtTicked, isNativeTicked, isMetamaskTicked, isTrustTicked, orderNumber, nativeAmount!!, metaMaskAmount!!, trustAmount!!)
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["main"]!!["goUpColor"] as String)),
                                checkmarkColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["main"]!!["goUpTintColor"] as String))
                            )
                        )
                        Text(
                            text = "USDC",
                            modifier = Modifier
                                .absoluteOffset((-12).dp, 0.dp)
                                .align(Alignment.CenterVertically)
                                .fillMaxWidth()
                                .padding(bottom = 0.dp),
                            fontSize = 18.sp,
                            maxLines = 2
                        )
                    }

                }
                item {
                    Row(Modifier.padding(0.dp)) {
                        Checkbox(
                            modifier = Modifier.absoluteOffset((-12).dp, 0.dp),
                            checked = isUsdtTicked,
                            onCheckedChange = {
                                isUsdtTicked = it
                                isUsdcTicked = !it

                                qrLink = refreshCode(isPolygonTicked, isSolanaTicked, isUsdcTicked, isUsdtTicked, isNativeTicked, isMetamaskTicked, isTrustTicked, orderNumber, nativeAmount!!, metaMaskAmount!!, trustAmount!!)
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["main"]!!["goUpColor"] as String)),
                                checkmarkColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["main"]!!["goUpTintColor"] as String))
                            )
                        )
                        Text(
                            text = "USDT",
                            modifier = Modifier
                                .absoluteOffset((-12).dp, 0.dp)
                                .align(Alignment.CenterVertically)
                                .fillMaxWidth(),
                            fontSize = 18.sp,
                            maxLines = 2
                        )
                    }
                }

                item {
                    Text(DataHolder.guiConfig["crypto"]!![DataHolder.internationalizeLabel("protocolText", useInternationalNames)] as String)

                    Row(Modifier.padding(4.dp)) {
                        Checkbox(
                            modifier = Modifier.absoluteOffset((-12).dp, 0.dp),
                            checked = isNativeTicked,
                            onCheckedChange = {
                                if (it) {
                                    isNativeTicked = true
                                    isMetamaskTicked = false
                                    isTrustTicked = false
                                }

                                qrLink = refreshCode(isPolygonTicked, isSolanaTicked, isUsdcTicked, isUsdtTicked, isNativeTicked, isMetamaskTicked, isTrustTicked, orderNumber, nativeAmount!!, metaMaskAmount!!, trustAmount!!)
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["main"]!!["goUpColor"] as String)),
                                checkmarkColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["main"]!!["goUpTintColor"] as String))
                            )
                        )
                        Text(
                            text = "Native",
                            modifier = Modifier
                                .absoluteOffset((-12).dp, 0.dp)
                                .align(Alignment.CenterVertically)
                                .fillMaxWidth()
                                .padding(bottom = 0.dp),
                            fontSize = 18.sp,
                            maxLines = 2
                        )
                    }

                }
                item {

                    Row(Modifier.padding(0.dp)) {
                        Checkbox(
                            modifier = Modifier.absoluteOffset((-12).dp, 0.dp),
                            checked = isMetamaskTicked,
                            onCheckedChange = {
                                isMetamaskTicked = it

                                if (it) {
                                    isNativeTicked = false
                                    isTrustTicked = false
                                } else {
                                    // do that at least 1 is ticked. why relevant: because might be unchecked due to choice of unsupported network i.e. Solana
                                    isNativeTicked = true
                                }

                                qrLink = refreshCode(isPolygonTicked, isSolanaTicked, isUsdcTicked, isUsdtTicked, isNativeTicked, isMetamaskTicked, isTrustTicked, orderNumber, nativeAmount!!, metaMaskAmount!!, trustAmount!!)
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["main"]!!["goUpColor"] as String)),
                                checkmarkColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["main"]!!["goUpTintColor"] as String))
                            ),
                            enabled = !isSolanaTicked
                        )
                        Text(
                            text = "Metamask",
                            modifier = Modifier
                                .absoluteOffset((-12).dp, 0.dp)
                                .align(Alignment.CenterVertically)
                                .fillMaxWidth()
                                .padding(bottom = 0.dp),
                            fontSize = 18.sp,
                            maxLines = 2
                        )
                    }

                }
                item {

                    Row(Modifier.padding(0.dp)) {
                        Checkbox(
                            modifier = Modifier.absoluteOffset((-12).dp, 0.dp),
                            checked = isTrustTicked,
                            onCheckedChange = {
                                isTrustTicked = it

                                if (it) {
                                    isNativeTicked = false
                                    isMetamaskTicked = false
                                } else {
                                    isNativeTicked = true
                                }

                                qrLink = refreshCode(isPolygonTicked, isSolanaTicked, isUsdcTicked, isUsdtTicked, isNativeTicked, isMetamaskTicked, isTrustTicked, orderNumber, nativeAmount!!, metaMaskAmount!!, trustAmount!!)
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["main"]!!["goUpColor"] as String)),
                                checkmarkColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["main"]!!["goUpTintColor"] as String))
                            )
                        )
                        Text(
                            text = "Trust",
                            modifier = Modifier
                                .absoluteOffset((-12).dp, 0.dp)
                                .align(Alignment.CenterVertically)
                                .fillMaxWidth(),
                            fontSize = 18.sp,
                            maxLines = 2
                        )
                    }
                }

                item {
                    Text(
                        DataHolder.guiConfig["crypto"]!![DataHolder.internationalizeLabel("qrScanInstructionsText", useInternationalNames)].toString()
                    )
                }

                item {
                    Text(
                        String.format(
                            DataHolder.guiConfig["crypto"]!![DataHolder.internationalizeLabel("contractAddressInstructions", useInternationalNames) + "Text"].toString(),
                            currencies[ if (isPolygonTicked) { "polygon" } else { if (isSolanaTicked) { "solana" } else { "polygon" /* default */ } } ]!![ if (isUsdcTicked) { "usdc" } else { "usdt" } ]!!
                        )
                    )
                }

                item {
                    if (readyForQrDisplay) {
                        QrCodeView(
                            data = qrLink,
                            modifier = Modifier.size((2 * applicationContext.resources.configuration.screenWidthDp / 3.0).dp)
                        )
                    }
                }

                item {
                    Button(
                        enabled = isOrderButtonEnabled,
                        onClick = {
                            Thread {
                                val receiver = if (isPolygonTicked) {
                                    DataHolder.restaurantConfig["crypto"]!!["polygonAddress"].toString()
                                } else if (isSolanaTicked) {
                                    DataHolder.restaurantConfig["crypto"]!!["solanaAddress"].toString()
                                } else {
                                    ""
                                }

                                checkPolygonPayments(receiver, DataHolder.cryptoApiKey ?: "", minimumAcceptablePaymentThreshold!!) { payments ->
                                    Handler(Looper.getMainLooper()).post {
                                        isOrderButtonEnabled = false
                                        Handler(Looper.getMainLooper()).postDelayed({ isOrderButtonEnabled = true }, DataHolder.guiConfig["cart"]!!["failureDurationMs"] as Long)
                                        if (payments.isNotEmpty() || isSolanaTicked) {
                                            onPaymentResult()
                                            finish()
                                        } else {
                                            // Stay on the screen and show error!
                                            displayFailure(applicationContext, DataHolder.guiConfig["cart"]!![DataHolder.internationalizeLabel("failureMessage", useInternationalNames)].toString())
                                        }
                                    }
                                }

                            }.start()
                        },
                        modifier = Modifier
                            .padding(top = (DataHolder.guiConfig["cart"]!!["cartOrderNowPaddingTop"] as Long).toInt().dp)
                            .fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["cart"]!!["cartOrderNowBackgroundColor"] as String)),
                            contentColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["cart"]!!["cartOrderNowContentColor"] as String))
                        ),
                    ) {
                        Text(
                            text = DataHolder.guiConfig["crypto"]!![DataHolder.internationalizeLabel("paymentFinishedText", useInternationalNames)].toString(),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }
            }
        }
    }

    private fun refreshCode(isPolygonTicked: Boolean,
                            isSolanaTicked: Boolean,
                            isUsdcTicked: Boolean,
                            isUsdtTicked: Boolean,
                            isNativeTicked: Boolean,
                            isMetamaskTicked: Boolean,
                            isTrustTicked: Boolean,
                            orderNumber: String,
                            nativeAmount: String,
                            metaMaskAmount: String,
                            trustAmount: String): String {
        // Construct the QR code anew with the currently given data.
        var url = ""

        // Protocol
        if (isNativeTicked && isPolygonTicked) {
            url += "ethereum:"

            // Currency
            url += if (isUsdcTicked) {
                currencies["polygon"]!!["usdc"]!!
            } else if (isUsdtTicked) {
                currencies["polygon"]!!["usdt"]!!
            } else {
                ""
            }

            // Network
            url += "@137/transfer?address="

            url += DataHolder.restaurantConfig["crypto"]!!["polygonAddress"] as String

            url += "&uint256="
            url += nativeAmount

        } else if (isNativeTicked && isSolanaTicked) {
            url += "solana:"

            url += DataHolder.restaurantConfig["crypto"]!!["solanaAddress"] as String

            url += "?amount="
            url += nativeAmount
            url += "&spl-token="

            // Currency
            url += if (isUsdcTicked) {
                currencies["solana"]!!["usdc"]!!
            } else if (isUsdtTicked) {
                currencies["solana"]!!["usdt"]!!
            } else {
                ""
            }

            url += "&memo="
            url += orderNumber

        } else if (isMetamaskTicked) {
            url += "https://metamask.app.link/send/pay-"

            // Currency
            url += if (isUsdcTicked) {
                currencies["polygon"]!!["usdc"]!!
            } else if (isUsdtTicked) {
                currencies["polygon"]!!["usdt"]!!
            } else {
                ""
            }

            url += "@137/transfer?address="

            url += DataHolder.restaurantConfig["crypto"]!!["polygonAddress"] as String

            url += "&uint256="
            url += String.format("%e", metaMaskAmount.toDouble()).replace("+", "")

        } else if (isTrustTicked) {
            url += "https://link.trustwallet.com/send?asset="

            // Currency
            if (isUsdcTicked) {
                url += if (isPolygonTicked) {
                    "c966_t" + currencies["polygon"]!!["usdc"]!!
                } else if (isSolanaTicked) {
                    "c501_t" + currencies["solana"]!!["usdc"]!!
                } else ""
            } else if (isUsdtTicked) {
                url += if (isPolygonTicked) {
                    "c966_t" + currencies["polygon"]!!["usdt"]!!
                } else if (isSolanaTicked) {
                    "c501_t" + currencies["solana"]!!["usdt"]!!
                } else ""
            }

            url += "&address="

            url += if (isPolygonTicked) {
                DataHolder.restaurantConfig["crypto"]!!["polygonAddress"] as String
            } else if (isSolanaTicked) {
                DataHolder.restaurantConfig["crypto"]!!["solanaAddress"] as String
            } else {
                ""
            }

            url += "&amount="
            url += trustAmount

            url += "&memo="
            url += orderNumber
        }

        return url
    }

    fun fetchExchangeRate(amount: Double, onComplete: (Double) -> Unit) {
        val handler = Handler(Looper.getMainLooper())

        Thread {
            var result = Double.MAX_VALUE

            val connection = URL("https://v6.exchangerate-api.com/v6/" + DataHolder.currencyExchangeApiKey!! + "/latest/USD").openConnection() as HttpURLConnection
            connection.requestMethod = "GET"

            try {
                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    // Read the response
                    val inputStream = connection.inputStream
                    val responseString = inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(responseString)
                    val conversionRates = json.getJSONObject("conversion_rates")
                    val rate = conversionRates.optDouble(DataHolder.restaurantConfig["currencyOfficial"].toString().uppercase(), 1.0)
                    val conversionRate = 1.0 / rate
                    result = amount * conversionRate
                }
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                connection.disconnect()
                handler.post { onComplete(result) }

            }
        }.start()
    }

    fun checkPolygonPayments(
        receiver: String,
        apiKey: String,
        minAmount: Double,
        onComplete: (List<PolygonTransfer>) -> Unit
    ) {
        val handler = Handler(Looper.getMainLooper())

        Thread {
            var result = emptyList<PolygonTransfer>()

            try {
                val url = URL("https://polygon-mainnet.g.alchemy.com/v2/$apiKey")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                // USDC + USDT contract addresses on Polygon
                val usdc = currencies["polygon"]!!["usdc"]!!
                val usdt = currencies["polygon"]!!["usdt"]!!

                val body = JSONObject().apply {
                    put("jsonrpc", "2.0")
                    put("id", 1)
                    put("method", "alchemy_getAssetTransfers")
                    put("params", JSONArray().apply {
                        put(JSONObject().apply {
                            put("fromBlock", "0x0")
                            put("toAddress", receiver)
                            put("contractAddresses", JSONArray().apply {
                                put(usdc)
                                put(usdt)
                            })
                            put("category", JSONArray().apply {
                                put("erc20")
                            })
                            put("withMetadata", true)
                            put("excludeZeroValue", true)
                            put("maxCount", "0x14")
                        })
                    })
                }

                // Write the request body
                val outputStream = connection.outputStream
                outputStream.write(body.toString().toByteArray())
                outputStream.close()

                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val inputStream = connection.inputStream
                    val responseString = inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(responseString)

                    if (json.has("result")) {
                        val resultObj = json.getJSONObject("result")
                        if (resultObj.has("transfers")) {
                            val transfersArray = resultObj.getJSONArray("transfers")
                            val validTransfers = mutableListOf<PolygonTransfer>()

                            val oneMinuteAgo = System.currentTimeMillis() - 60000 // 60 seconds ago

                            for (i in 0 until transfersArray.length()) {
                                val transfer = transfersArray.getJSONObject(i)
                                try {
                                    val hash = transfer.getString("hash")
                                    val from = transfer.getString("from")
                                    val to = transfer.getString("to")
                                    val value = transfer.getDouble("value")
                                    val asset = transfer.getString("asset")
                                    val metadata = transfer.getJSONObject("metadata")
                                    val blockTimestamp = metadata.getString("blockTimestamp")

                                    // Parse ISO8601 timestamp
                                    val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'")
                                    formatter.timeZone = TimeZone.getTimeZone("UTC")
                                    val timestamp = formatter.parse(blockTimestamp)

                                    // Check time window (within last minute)
                                    if (timestamp != null && timestamp.time >= oneMinuteAgo) {
                                        // Check min amount
                                        if (value >= minAmount) {
                                            validTransfers.add(
                                                PolygonTransfer(
                                                    hash = hash,
                                                    from = from,
                                                    to = to,
                                                    value = value,
                                                    asset = asset,
                                                    timestamp = timestamp
                                                )
                                            )
                                        }
                                    }
                                } catch (e: Exception) {
                                    // Skip invalid transfer entries
                                    continue
                                }
                            }
                            result = validTransfers
                        }
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            handler.post { onComplete(result) }
        }.start()
    }

    // PolygonTransfer data class
    data class PolygonTransfer(
        val hash: String,
        val from: String,
        val to: String,
        val value: Double,
        val asset: String,
        val timestamp: Date
    )

}
