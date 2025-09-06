package business.digitalfood.watch.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import business.digitalfood.watch.Constants
import business.digitalfood.watch.DataHolder
import business.digitalfood.watch.DataHolder.compareVersions
import business.digitalfood.watch.model.Food
import business.digitalfood.watch.model.Option
import business.digitalfood.watch.model.OptionContent
import business.digitalfood.watch.model.Options
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import java.util.regex.Pattern

@SuppressLint("CustomSplashScreen")
class SplashActivity: ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val storage = Firebase.storage("redacted")

        Thread {
            val db = Firebase.firestore
            auth = Firebase.auth

            auth.signInWithEmailAndPassword(Constants.USERNAME, Constants.PASSWORD)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        db.collection(Constants.COLLECTION)
                            .document(Constants.DOCUMENT)
                            .get()
                            .addOnSuccessListener { documentSnapshot ->
                                val deferredList = mutableListOf<CompletableDeferred<Boolean>>()

                                val globalAddressDeferred = CompletableDeferred<Boolean>()
                                val globalCountriesDeferred = CompletableDeferred<Boolean>()
                                val globalEmailDeferred = CompletableDeferred<Boolean>()
                                deferredList.add(globalAddressDeferred)
                                deferredList.add(globalCountriesDeferred)
                                deferredList.add(globalEmailDeferred)

                                db.collection(Constants.GLOBAL_COLLECTION)
                                    .document(Constants.GLOBAL_DOCUMENT)
                                    .get()
                                    .addOnSuccessListener { documentSnapshot ->
                                        (documentSnapshot.data!!.entries as MutableSet<MutableMap.MutableEntry<*, *>>)
                                            .forEach { setEntry ->
                                                if ("address" == setEntry.key) {
                                                    DataHolder.globalAddress["line1"] = (setEntry.value as HashMap<String, String>)["line1"].toString()
                                                    DataHolder.globalAddress["line2"] = (setEntry.value as HashMap<String, String>)["line2"].toString()
                                                    DataHolder.globalAddress["line3"] = (setEntry.value as HashMap<String, String>)["line3"].toString()
                                                    globalAddressDeferred.complete(true)

                                                } else if ("countries" == setEntry.key) {
                                                    DataHolder.foodVat.value = ((setEntry.value as HashMap<*, *>)[Constants.COUNTRY] as HashMap<*, *>)["foodVat"].toString()
                                                    globalCountriesDeferred.complete(true)

                                                } else if ("email" == setEntry.key) {
                                                    DataHolder.globalAddress["email"] = setEntry.value.toString()
                                                    globalEmailDeferred.complete(true)
                                                }
                                            }
                                    }
                                    .addOnFailureListener { exception ->
                                        globalAddressDeferred.complete(false)
                                        globalCountriesDeferred.complete(false)
                                        globalEmailDeferred.complete(false)
                                        exception.printStackTrace()
                                    }

                                CoroutineScope(Dispatchers.IO).launch {
                                    deferredList.awaitAll()
                                    val isFoodVatDownloaded = deferredList.all { it.isCompleted }

                                    if (documentSnapshot.exists() && isFoodVatDownloaded) {
                                        val food : MutableList<Food> = mutableListOf()

                                        (documentSnapshot.data!!.entries as MutableSet<MutableMap.MutableEntry<*, *>>).forEach { setEntry ->
                                            if ("food" == setEntry.key) {
                                                val allFood = setEntry.value as List<HashMap<*, *>>
                                                allFood.forEach { v ->
                                                    val options = v["options"]
                                                    food.add(
                                                        Food(
                                                            if (v["id"] is String) { (v["id"] as String).toInt() } else { (v["id"] as Long).toInt() },
                                                            v["background"] as String?,
                                                            v["name"] as String?,
                                                            v["nameEn"] as String?,
                                                            v["description"] as String?,
                                                            v["descriptionEn"] as String?,
                                                            v["hasImage"] as Boolean?,
                                                            v["isCategory"] as Boolean?,
                                                            if (v["price"] != null) {
                                                                if (v["price"] is String) { (v["price"] as String).toDouble() } else { (v["price"] as Number).toDouble() }
                                                            } else {
                                                                null
                                                            },
                                                            if (options != null) { Options(options as List<Option>) } else { null },
                                                            v["availableFrom"] as String?,
                                                            v["availableTo"] as String?,
                                                            if (v["daysAvailable"] != null) { v["daysAvailable"] as List<String>? } else { null },
                                                            v["isHidden"] as Boolean?
                                                        )
                                                    )

                                                    if (options != null) {
                                                        (Options(options as List<Option>).options as List<*>).forEach { option ->
                                                            ((option as HashMap<*,*>)["content"] as List<HashMap<*, *>>).forEach { entry ->
                                                                val id = if (entry["id"] is String) { (entry["id"] as String).toInt() } else { (entry["id"] as Long).toInt() }
                                                                DataHolder.options[id] = OptionContent(
                                                                    id.toLong(),
                                                                    entry["name"] as String,
                                                                    entry["nameEn"] as String,
                                                                    if (entry["price"] is String) { (entry["price"] as String).toDouble() } else { (entry["price"] as Number).toDouble() },
                                                                    entry["hasImage"] as Boolean?,
                                                                    entry["background"] as String?,
                                                                    entry["availableFrom"] as String?,
                                                                    entry["availableTo"] as String?
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            } else if ("config" == setEntry.key) {
                                                (setEntry.value as HashMap<*, *>).forEach { configEntry ->
                                                    if (configEntry.key == "gui") {
                                                        DataHolder.guiConfig = configEntry.value as HashMap<String, HashMap<*, *>>

                                                        DataHolder.name = DataHolder.sharedPreferences.getString(DataHolder.guiConfig["main"]?.let { it["savedNameKey"] as String } ?: "name", null)
                                                        DataHolder.email = DataHolder.sharedPreferences.getString(DataHolder.guiConfig["main"]?.let { it["savedEmailKey"] as String } ?: "email", null)
                                                        DataHolder.street = DataHolder.sharedPreferences.getString(DataHolder.guiConfig["main"]?.let { it["savedStreetKey"] as String } ?: "street", null)
                                                        DataHolder.zip.postValue(DataHolder.sharedPreferences.getString(DataHolder.guiConfig["main"]?.let { it["savedZipKey"] as String } ?: "zip", null))
                                                        DataHolder.city = DataHolder.sharedPreferences.getString(DataHolder.guiConfig["main"]?.let { it["savedCityKey"] as String } ?: "city", null)
                                                        DataHolder.phoneNumber = DataHolder.sharedPreferences.getString(DataHolder.guiConfig["main"]?.let { it["savedPhoneNumberKey"] as String } ?: "phoneNumber", null)
                                                        DataHolder.deliveryMode = DataHolder.sharedPreferences.getString(DataHolder.guiConfig["main"]?.let { it["savedDeliveryModeKey"] as String } ?: "deliveryMode", DataHolder.guiConfig["mode"]!!["defaultMode"] as String)

                                                        ContactActivity.nameRegex = Pattern.compile(DataHolder.guiConfig["main"]?.let { it["nameRegex"] as String } ?: "^[^0-9]+$")
                                                        ContactActivity.emailRegex = Pattern.compile(DataHolder.guiConfig["main"]?.let { it["emailRegex"] as String } ?: "^[A-Za-z0-9._&%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,63}$")
                                                        ContactActivity.phoneNumberRegex = Pattern.compile(DataHolder.guiConfig["main"]?.let { it["phoneNumberRegex"] as String } ?: "^[+]?[0-9]+$")
                                                        ContactActivity.streetRegex = Pattern.compile(DataHolder.guiConfig["main"]?.let { it["streetRegex"] as String } ?: "^(?!$).+")
                                                        ContactActivity.zipRegex = Pattern.compile(DataHolder.guiConfig["main"]?.let { it["zipRegex"] as String } ?: "^(?!$).+")
                                                        ContactActivity.cityRegex = Pattern.compile(DataHolder.guiConfig["main"]?.let { it["cityRegex"] as String } ?: "^(?!$).+")
                                                        ContactActivity.instructionsRegex = Pattern.compile(DataHolder.guiConfig["main"]?.let { it["instructionsRegex"] as String } ?: "(?s).+")
                                                    } else if (configEntry.key == "restaurant") {
                                                        DataHolder.restaurantConfig = configEntry.value as HashMap<String, HashMap<*, *>>
                                                    } else if (configEntry.key == "email") {
                                                        DataHolder.emailConfig = configEntry.value as HashMap<String, HashMap<*, *>>
                                                    } else if (configEntry.key == "stripe") {
                                                        DataHolder.stripeConfig = configEntry.value as HashMap<String, HashMap<*, *>>
                                                    } else if (configEntry.key == "whatsapp") {
                                                        DataHolder.whatsAppConfig = configEntry.value as HashMap<String, HashMap<*, *>>
                                                    } else if (configEntry.key == "technical") {
                                                        DataHolder.minimumAllowedAppVersion = (configEntry.value as HashMap<String, HashMap<*, *>>)["minVersion"].toString()
                                                        DataHolder.isCardPaymentSupported = (configEntry.value as HashMap<String, HashMap<*, *>>)["isCardPaymentSupportedWearOs"] as Boolean?
                                                        DataHolder.isCryptoPaymentSupported = (configEntry.value as HashMap<String, HashMap<*, *>>)["isCryptoPaymentSupportedWearOs"] as Boolean?
                                                        DataHolder.activeCardPaymentMode = (configEntry.value as HashMap<String, HashMap<*, *>>)["activeCardPaymentMode"] as String?
                                                        DataHolder.cryptoApiKey = (configEntry.value as HashMap<String, HashMap<*, *>>)["cryptoApiKey"] as String?
                                                        DataHolder.currencyExchangeApiKey = (configEntry.value as HashMap<String, HashMap<*, *>>)["currencyExchangeApiKey"] as String?
                                                        DataHolder.isCryptoPaymentSupported = (configEntry.value as HashMap<String, HashMap<*, *>>)["isCryptoPaymentSupportedWatchOs"] as Boolean?
                                                        DataHolder.useWhatsApp = (configEntry.value as HashMap<String, HashMap<*, *>>)["useWhatsApp"] as Boolean?
                                                        DataHolder.isActive = (configEntry.value as HashMap<String, HashMap<*, *>>)["isActive"] as Boolean?
                                                    }
                                                }
                                            } else if ("commission" == setEntry.key) {
                                                DataHolder.commission = setEntry.value as HashMap<String, HashMap<*, *>>
                                            }
                                        }

                                        if (DataHolder.isActive == false) {
                                            MainActivity.whatToStart.postValue("inactive")
                                        } else {
                                            val isOpeningTimeSatisfied = isRestaurantCurrentlyOpen()

                                            if (!isOpeningTimeSatisfied) {
                                                MainActivity.whatToStart.postValue("restaurantClosedError")
                                            } else {
                                                val currentAppVersion = applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0).versionName ?: "0.0"
                                                println("Running app version $currentAppVersion. The allowed minimum is version " + DataHolder.minimumAllowedAppVersion)
                                                if (compareVersions(currentAppVersion, DataHolder.minimumAllowedAppVersion!!) < 0) {
                                                    MainActivity.whatToStart.postValue("updateError")
                                                } else {
                                                    DataHolder.food = food

                                                    MainActivity.whatToStart.postValue("app")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            .addOnFailureListener {
                                MainActivity.whatToStart.postValue("internetError")
                                println(it.message)
                            }
                    } else {
                        MainActivity.whatToStart.postValue("loginError")
                        println(task.exception?.message)
                    }

                    DataHolder.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)

                    storage.reference.child(Constants.COLLECTION).listAll()
                        .addOnSuccessListener { result ->
                            result.items.forEach { item ->

                                item.getBytes(Long.MAX_VALUE).addOnSuccessListener {
                                    var editor: SharedPreferences.Editor = DataHolder.sharedPreferences.edit()

                                    editor = editor.putString(
                                        item.toString().split("/").last().substringBeforeLast("."),
                                        android.util.Base64.encodeToString(it, android.util.Base64.DEFAULT)
                                    )

                                    editor.apply()

                                }
                            }
                        }.addOnFailureListener {
                            println(it.message)
                        }

                }
        }.start()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val splashScreen = installSplashScreen()
            splashScreen.setKeepOnScreenCondition { true }
        }

        lifecycleScope.launchWhenCreated {
            val intent = Intent(this@SplashActivity, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}