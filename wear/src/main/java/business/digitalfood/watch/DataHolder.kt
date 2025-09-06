package business.digitalfood.watch

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.MutableLiveData
import business.digitalfood.watch.activity.ContactActivity
import business.digitalfood.watch.model.Food
import business.digitalfood.watch.model.OptionContent
import com.stripe.Stripe
import com.stripe.model.PaymentLink
import com.stripe.model.Price
import com.stripe.param.PaymentLinkCreateParams
import com.stripe.param.PriceCreateParams
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.DateFormatSymbols
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future

object DataHolder {
    // The cart contains the food that the user orders. It maps id to amount.
    var cart: MutableMap<Int, Int> = LinkedHashMap()
    // Upon app startup, the available food is populated from Firestore.
    var food: MutableList<Food> = mutableListOf()
    /* Upon app startup, the available options are populated from Firestore.
    They map the id of the option to the option for the purpose of easier lookup by id. */
    val options: MutableMap<Int, OptionContent> = HashMap()
    /* Mapping of a food to its selected options. E.g.,
    { 10 -> { [32, 111], [34], [35, 111, 112], [35] } }
    For the food with id 10, which has two options, i.e. one single-option (containing options with the ids 32, 34, 35)
    and one multi-option (containing options with the ids 111, 112), and has been selected 4 times. */
    var foodToOptionsMapping: MutableMap<Int, MutableList<MutableList<Int>>> = LinkedHashMap()

    var guiConfig: HashMap<String, HashMap<*, *>> = HashMap()
    var restaurantConfig: HashMap<String, HashMap<*, *>> = HashMap()
    var emailConfig: HashMap<String, HashMap<*, *>> = HashMap()
    var stripeConfig: HashMap<String, HashMap<*, *>> = HashMap()
    var whatsAppConfig: HashMap<String, HashMap<*, *>> = HashMap()
    var commission: HashMap<String, HashMap<*, *>> = HashMap()

    var minimumAllowedAppVersion: String? = null
    var activeCardPaymentMode: String? = null
    var cryptoApiKey: String? = null
    var currencyExchangeApiKey: String? = null

    var useWhatsApp: Boolean? = null
    var isActive: Boolean? = null
    var isCardPaymentSupported: Boolean? = null
    var isCryptoPaymentSupported: Boolean? = null

    var paymentLinkId: String? = null

    var globalAddress: HashMap<String, String> = HashMap()

    var foodVat = MutableLiveData<String>()

    var name: String? = null
    var email: String? = null
    var street: String? = null
    var zip = MutableLiveData<String>()
    var city: String? = null
    var phoneNumber: String? = null
    var instructions: String? = null

    var deliveryMode: String? = null

    val totalPrice = MutableLiveData(0.0)
    val priceLine = MutableLiveData<String>()

    val contactDataError = MutableLiveData(false)
    val deliveryModeError = MutableLiveData(false)

    val minimumOrderValueText = MutableLiveData<String>()

    const val ROUND_WEAR_PADDING_FACTOR = 0.146467f

    const val NAME_SPEECH_REQUEST_CODE = 1
    const val PHONE_NUMBER_SPEECH_REQUEST_CODE = 2
    const val EMAIL_SPEECH_REQUEST_CODE = 3
    const val STREET_SPEECH_REQUEST_CODE = 4
    const val ZIP_SPEECH_REQUEST_CODE = 5
    const val CITY_SPEECH_REQUEST_CODE = 6
    const val INSTRUCTIONS_SPEECH_REQUEST_CODE = 7

    lateinit var sharedPreferences: SharedPreferences

    // All chosen options of a main food shall be below the main food. After that, the next main food will be shown, and all chosen options for that shall be below it, etc.
    fun reorderCart() {
        val newCart = linkedMapOf<Int, Int>()
        val mainFoodMap = linkedMapOf<Int, Int>()

        cart.forEach { (id, amount) ->
            if (food.find { it.id == id } != null) {
                mainFoodMap[id] = amount
            }
        }

        mainFoodMap.forEach { (id, amount) ->
            newCart[id] = amount
            val options = foodToOptionsMapping.entries.find { it.key == id }?.value?.flatMap { it }?.distinct() ?: emptyList()
            options.forEach { option ->
                val found = cart.entries.find { it.key == option }
                if (found != null) {
                    newCart[found.key] = found.value
                }
            }
        }


        cart = newCart
    }

    // Calculates the number of products in the cart as displayed to the user. This does not include options.
    fun calculateNumberOfProducts(cart: Map<Int, Int>, options: Map<Int, OptionContent>): Int {
        if (cart.isEmpty()) {
            return 0
        }
        var count = 0
        cart.entries.forEach { entry ->
            if (!options.keys.contains(entry.key)) {
                count += entry.value
            }
        }
        return count
    }

    fun calculateCartPrice(cart: Map<Int, Int>): Double {
        var total = 0.0
        cart.forEach{ (id, quantity) ->
            run {
                val price: Double? = food.find { it.id == id }?.price
                if (price != null) {
                    total += price * quantity
                } else {
                    val optionPrice: Double = options.filter { it.key == id }[id]?.price ?: 0.0
                    total += optionPrice * quantity
                }
            }
        }
        return total
    }

    fun formatPrice(price: Double, context: Context, includeCurrency: Boolean = true): String {
        val formatter: DecimalFormat = NumberFormat.getCurrencyInstance(
            context.resources.configuration.locales[0]
        ) as DecimalFormat
        val symbols: DecimalFormatSymbols = formatter.decimalFormatSymbols
        symbols.currencySymbol = ""
        formatter.decimalFormatSymbols = symbols

        var formattedPrice: String = formatter.format(price).trim()
        if (includeCurrency) {
            formattedPrice += " " + restaurantConfig["currency"]
        }

        return formattedPrice
    }

    fun getPaymentLink(unitAmount: Long, orderName: String, currency: String, useInternationalNames: Boolean): String {
        Stripe.apiKey = ((stripeConfig["apiKey"]!![activeCardPaymentMode!!] as Map<String, Object>)["secretKey"].toString())

        val paymentSuccessMessage = stripeConfig[internationalizeLabel("paymentSuccessMessageWearOs", useInternationalNames)].toString()

        val priceCreateParams: PriceCreateParams =
            PriceCreateParams.builder()
                .setCurrency(currency)
                .setUnitAmount(unitAmount)
                .setProductData(
                    PriceCreateParams.ProductData.builder().setName(orderName).build()
                )
                .build()

        val pool = Executors.newFixedThreadPool(3) // creates a pool of threads for the Future to draw from

        val priceFuture: Future<Price?>? = pool.submit(Callable<Price?> { Price.create(priceCreateParams) })
        val price: Price = priceFuture!!.get()!!

        val paymentLinkParams =
            PaymentLinkCreateParams.builder()
                .addLineItem(
                    PaymentLinkCreateParams.LineItem.builder()
                        .setPrice(price.id)
                        .setQuantity(1L)
                        .build()
                )
                .setAfterCompletion(
                    PaymentLinkCreateParams.AfterCompletion.builder()
                        .setType(PaymentLinkCreateParams.AfterCompletion.Type.HOSTED_CONFIRMATION)
                        .setHostedConfirmation(
                            PaymentLinkCreateParams.AfterCompletion.HostedConfirmation.builder()
                                .setCustomMessage(paymentSuccessMessage)
                                .build()
                        )
                        .build()
                )
                .addPaymentMethodType(PaymentLinkCreateParams.PaymentMethodType.CARD)
                .build()

        val paymentLinkFuture: Future<PaymentLink?>? = pool.submit(Callable<PaymentLink?> { PaymentLink.create(paymentLinkParams) })
        val paymentLink = paymentLinkFuture!!.get()!!
        pool.shutdown()

        paymentLinkId = paymentLink.id

        return paymentLink.url
    }

    fun retrieveCheckoutSession(paymentLinkId: String, secretKey: String, onComplete: (Boolean) -> Unit) {
        val paymentLinksRequestEndpoint = stripeConfig["checkoutSessionsEndpoint"].toString()

        val handler = Handler(Looper.getMainLooper())

        Thread {
            var success = false

            val connection = URL("$paymentLinksRequestEndpoint?payment_link=$paymentLinkId").openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $secretKey")

            try {
                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    // Read the response
                    val inputStream = connection.inputStream
                    val responseString = inputStream.bufferedReader().use { it.readText() }

                    // Parse the JSON response
                    val responseJson = JSONObject(responseString)
                    val dataArray = responseJson.getJSONArray("data")

                    // Check each session's payment status
                    for (i in 0 until dataArray.length()) {
                        val sessionResponse = dataArray.getJSONObject(i)
                        val paymentStatus = sessionResponse.optString("payment_status")
                        if (paymentStatus == "paid" || paymentStatus == "succeeded") {
                            success = true
                            break
                        }
                    }
                } else {
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

    fun isContactDataValid(): Boolean {
        if (name == null) {
            val savedName = sharedPreferences.getString(guiConfig["main"]!!["savedNameKey"] as String, null)
            if (savedName == null) {
                return false
            }
            name = (restaurantConfig["sampleUserContactData"] as Map<String, Object>)["nameSample"] as String
        }

        if (useWhatsApp != true) {
            if (email == null) {
                val savedEmail = sharedPreferences.getString(guiConfig["main"]!!["savedEmailKey"] as String, null)
                if (savedEmail == null) {
                    return false
                }
                email = (restaurantConfig["sampleUserContactData"] as Map<String, Object>)["emailAddressSample"] as String
            }
        }

        if (deliveryMode.toString() == "delivery") {
            if (street == null) {
                val savedDelivery = sharedPreferences.getString(guiConfig["main"]!!["savedStreetKey"] as String, null)
                if (savedDelivery == null) {
                    return false
                }
                street = (restaurantConfig["sampleUserContactData"] as Map<String, Object>)["streetSample"] as String
            }

            if (zip.value == null) {
                val savedZip = sharedPreferences.getString(guiConfig["main"]!!["savedZipKey"] as String, null)
                if (savedZip == null) {
                    val isZipValid = isZipCodeValid()
                    if (!isZipValid) {
                        return isZipValid
                    }
                }
                zip.postValue((restaurantConfig["sampleUserContactData"] as Map<String, Object>)["zipSample"] as String)
            }

            if (city == null) {
                val savedCity = sharedPreferences.getString(guiConfig["main"]!!["savedCityKey"] as String, null)
                if (savedCity == null) {
                    return false
                }
                city = (restaurantConfig["sampleUserContactData"] as Map<String, Object>)["citySample"] as String
            }
        }

        if (phoneNumber == null) {
            val savedPhoneNumber = sharedPreferences.getString(guiConfig["main"]!!["savedPhoneNumberKey"] as String, null)
            if (savedPhoneNumber == null) {
                return false
            }
            phoneNumber = (restaurantConfig["sampleUserContactData"] as Map<String, Object>)["phoneNumberSample"] as String
        }

        if (instructions == null) {
            val savedInstructions = sharedPreferences.getString(guiConfig["main"]!!["savedInstructionsKey"] as String, null)
            if (savedInstructions == null) {
                return false
            }
            instructions = (restaurantConfig["sampleUserContactData"] as Map<String, Object>)["instructionsSample"] as String
        }

        if (deliveryMode.toString() == "delivery") {
            return ContactActivity.nameRegex.matcher(name!!).matches()
                    && (useWhatsApp == true || ContactActivity.emailRegex.matcher(email!!).matches())
                    && ContactActivity.phoneNumberRegex.matcher(phoneNumber!!).matches()
                    && ContactActivity.streetRegex.matcher(street!!).matches()
                    && (zip.value == null || ContactActivity.zipRegex.matcher(zip.value!!).matches())
                    && ContactActivity.cityRegex.matcher(city!!).matches()
                    && ContactActivity.instructionsRegex.matcher(instructions!!).matches()
        } else {
            return ContactActivity.nameRegex.matcher(name!!).matches()
                    && (useWhatsApp == true || ContactActivity.emailRegex.matcher(email!!).matches())
                    && ContactActivity.phoneNumberRegex.matcher(phoneNumber!!).matches()
                    && ContactActivity.instructionsRegex.matcher(instructions!!).matches()
        }
    }

    fun isZipCodeValid(): Boolean {
        val minimumOrderValueMap = restaurantConfig["minimumOrderMap"] as Map<*, *>
        return if (minimumOrderValueMap.isNotEmpty()) {
            if (zip.value != null) {
                val minimumOrderValueLookupForZipCode = minimumOrderValueMap.getOrDefault(zip.value, -1)
                minimumOrderValueLookupForZipCode != -1
            } else {
                true // it means the zip code is uninitialized, but not invalid. If it was invalid, then it should have been in the map, like {plz -> null}.
            }
        } else {
            true
        }
    }

    fun isModeSelected(): Boolean {
        return deliveryMode != null && deliveryMode != ""
    }

    fun internationalizeLabel(labelName: String, useInternationalNames: Boolean): String {
        return labelName + (if (useInternationalNames) {"En"} else {""})
    }

    /*
    * Compares two versions semantically, for example:
    * "1.0" and "1.0.1" -> returns < 0
    * "1.0.1" and "1.0" -> returns > 0
    * "1.0" and "1.0.0" -> returns 0
    * non-numeric input like "test" -> throws exception
    * further edge cases like "1a..2.3" or "1..2.3..." did not need to be considered
    */
    fun compareVersions(version1: String, version2: String): Int {
        val regex = Regex("\\d+")

        val version1Numbers = regex.findAll(version1).map { it.value.toIntOrNull() }.toList()
        val version2Numbers = regex.findAll(version2).map { it.value.toIntOrNull() }.toList()

        // Check for invalid versions
        if (version1Numbers.any { it == null } || version2Numbers.any { it == null } || version1Numbers.isEmpty() || version2Numbers.isEmpty()) {
            throw IllegalArgumentException("Invalid input: $version1 or $version2")
        }

        val minLength = minOf(version1Numbers.size, version2Numbers.size)

        for (i in 0 until minLength) {
            val result = version1Numbers[i]!!.compareTo(version2Numbers[i]!!)
            if (result != 0) {
                return result
            }
        }

        val version1Trimmed = version1Numbers.dropLastWhile { it == 0 }
        val version2Trimmed = version2Numbers.dropLastWhile { it == 0 }

        return version1Trimmed.size.compareTo(version2Trimmed.size)
    }

    fun playHaptic(context: Context, isSuccessHaptic: Boolean) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Predefined effect (closer to watchOS "failure")
            vibrator.vibrate(
                VibrationEffect.createPredefined(if (isSuccessHaptic) { VibrationEffect.EFFECT_CLICK } else { VibrationEffect.EFFECT_DOUBLE_CLICK })
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Fallback to a custom pulse
            if (isSuccessHaptic) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 100, 50, 100), -1) // double pulse
                )
            }

        } else {
            if (isSuccessHaptic) {
                @Suppress("DEPRECATION")
                vibrator.vibrate(100)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(200)
            }
        }
    }

    fun toShortDayNames(days: List<String>, useInternationalNames: Boolean, useShort: Boolean): List<String> {
        val locale = if (useInternationalNames) Locale.ENGLISH else Locale.getDefault()
        val weekdays = if (useShort) DateFormatSymbols(locale).shortWeekdays else DateFormatSymbols(locale).weekdays

        return days.map { day ->
            when (day.trim().lowercase(Locale.ENGLISH)) {
                "sunday"    -> weekdays[1]
                "monday"    -> weekdays[2]
                "tuesday"   -> weekdays[3]
                "wednesday" -> weekdays[4]
                "thursday"  -> weekdays[5]
                "friday"    -> weekdays[6]
                "saturday"  -> weekdays[7]
                else        -> day // fallback if DB value is weird
            }.replaceFirstChar { it.titlecase(locale) }
        }
    }
}