package business.digitalfood.watch.activity

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Text
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import business.digitalfood.watch.DataHolder
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.regex.Pattern
import kotlin.properties.Delegates

class ContactActivity: ComponentActivity() {

    companion object {
        const val GOOGLE_RECOGNITION_SERVICE_NAME = "com.google.android.googlequicksearchbox/com.google.android.voicesearch.serviceapi.GoogleRecognitionService"

        lateinit var nameRegex: Pattern
        lateinit var emailRegex: Pattern
        lateinit var phoneNumberRegex: Pattern
        lateinit var streetRegex: Pattern
        lateinit var zipRegex: Pattern
        lateinit var cityRegex: Pattern
        lateinit var instructionsRegex: Pattern

        lateinit var name: String
        lateinit var email: String
        lateinit var street: String
        lateinit var zip: String
        lateinit var city: String
        lateinit var phoneNumber: String
        lateinit var instructions: String

        var nameSpeechRecognizer: SpeechRecognizer? = null
        var phoneNumberSpeechRecognizer: SpeechRecognizer? = null
        var emailSpeechRecognizer: SpeechRecognizer? = null
        var streetSpeechRecognizer: SpeechRecognizer? = null
        var zipSpeechRecognizer: SpeechRecognizer? = null
        var citySpeechRecognizer: SpeechRecognizer? = null
        var instructionsSpeechRecognizer: SpeechRecognizer? = null

        fun isNameInitialized() = ::name.isInitialized
        fun isEmailInitialized() = ::email.isInitialized
        fun isStreetInitialized() = ::street.isInitialized
        fun isZipInitialized() = ::zip.isInitialized
        fun isCityInitialized() = ::city.isInitialized
        fun isPhoneNumberInitialized() = ::phoneNumber.isInitialized
        fun isInstructionsInitialized() = ::instructions.isInitialized
    }

    private val nameLive = MutableLiveData(DataHolder.sharedPreferences.getString(DataHolder.guiConfig["main"]!!["savedNameKey"] as String, null) ?: (DataHolder.restaurantConfig["sampleUserContactData"] as Map<String, Object>)["nameSample"] as String)
    private val emailLive = MutableLiveData(
        if (DataHolder.email != null) {
            DataHolder.email!!
        } else {
            DataHolder.sharedPreferences.getString(DataHolder.guiConfig["main"]!!["savedEmailKey"] as String, null) ?: (DataHolder.restaurantConfig["sampleUserContactData"] as Map<String, Object>)["emailAddressSample"] as String
        }
    )
    private val streetLive = MutableLiveData(DataHolder.sharedPreferences.getString(DataHolder.guiConfig["main"]!!["savedStreetKey"] as String, null) ?: (DataHolder.restaurantConfig["sampleUserContactData"] as Map<String, Object>)["streetSample"] as String)
    private val zipLive = MutableLiveData(DataHolder.sharedPreferences.getString(DataHolder.guiConfig["main"]!!["savedZipKey"] as String, null) ?: (DataHolder.restaurantConfig["sampleUserContactData"] as Map<String, Object>)["zipSample"] as String)
    private val cityLive = MutableLiveData(DataHolder.sharedPreferences.getString(DataHolder.guiConfig["main"]!!["savedCityKey"] as String, null) ?: (DataHolder.restaurantConfig["sampleUserContactData"] as Map<String, Object>)["citySample"] as String)
    private val instructionsLive = MutableLiveData(DataHolder.sharedPreferences.getString(DataHolder.guiConfig["main"]!!["savedInstructionsKey"] as String, null) ?: (DataHolder.restaurantConfig["sampleUserContactData"] as Map<String, Object>)["instructionsSample"] as String)
    private val phoneNumberLive = MutableLiveData(
        if (DataHolder.phoneNumber != null) {
            DataHolder.phoneNumber!!
        } else {
            DataHolder.sharedPreferences.getString(DataHolder.guiConfig["main"]!!["savedPhoneNumberKey"] as String, null) ?: (DataHolder.restaurantConfig["sampleUserContactData"] as Map<String, Object>)["phoneNumberSample"] as String
        }
    )

    private var useInternationalNames by Delegates.notNull<Boolean>()

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

    override fun onDestroy() {
        super.onDestroy()

        var editor: SharedPreferences.Editor = DataHolder.sharedPreferences.edit()
        if (isNameInitialized()) {
            editor = editor.putString(DataHolder.guiConfig["main"]!!["savedNameKey"] as String, name)
            DataHolder.name = name
        }
        if (isEmailInitialized()) {
            editor = editor.putString(DataHolder.guiConfig["main"]!!["savedEmailKey"] as String, email)
            DataHolder.email = email
        }
        if (isStreetInitialized()) {
            editor = editor.putString(DataHolder.guiConfig["main"]!!["savedStreetKey"] as String, street)
            DataHolder.street = street
        }
        if (isZipInitialized()) {
            editor = editor.putString(DataHolder.guiConfig["main"]!!["savedZipKey"] as String, zip)
            DataHolder.zip.postValue(zip)
            DataHolder.minimumOrderValueText.postValue(getMinimumOrderValueText(applicationContext, useInternationalNames))
        }
        if (isCityInitialized()) {
            editor = editor.putString(DataHolder.guiConfig["main"]!!["savedCityKey"] as String, city)
            DataHolder.city = city
        }
        if (isPhoneNumberInitialized()) {
            editor = editor.putString(DataHolder.guiConfig["main"]!!["savedPhoneNumberKey"] as String, phoneNumber)
            DataHolder.phoneNumber = phoneNumber
        }
        if (isInstructionsInitialized()) {
            editor = editor.putString(DataHolder.guiConfig["main"]!!["savedInstructionsKey"] as String, instructions)
            DataHolder.instructions = instructions
        }
        editor.apply()

        nameSpeechRecognizer?.destroy()
        phoneNumberSpeechRecognizer?.destroy()
        emailSpeechRecognizer?.destroy()
        streetSpeechRecognizer?.destroy()
        zipSpeechRecognizer?.destroy()
        citySpeechRecognizer?.destroy()
        instructionsSpeechRecognizer?.destroy()

        DataHolder.contactDataError.postValue(!DataHolder.isContactDataValid())
    }

    @OptIn(ExperimentalWearFoundationApi::class)
    @Composable
    fun ConstructScreen(useInternationalNames: Boolean) {
        val tintContentColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["contact"]!!["microphoneTintColor"] as String))

        var nameIconTint by remember { mutableStateOf(tintContentColor) }
        var emailIconTint by remember { mutableStateOf(tintContentColor) }
        var streetIconTint by remember { mutableStateOf(tintContentColor) }
        var zipIconTint by remember { mutableStateOf(tintContentColor) }
        var cityIconTint by remember { mutableStateOf(tintContentColor) }
        var phoneNumberIconTint by remember { mutableStateOf(tintContentColor) }
        var instructionsIconTint by remember { mutableStateOf(tintContentColor) }

        name = nameLive.value.toString()
        email = emailLive.value.toString()
        phoneNumber = phoneNumberLive.value.toString()
        street = streetLive.value.toString()
        zip = zipLive.value.toString()
        city = cityLive.value.toString()
        instructions = instructionsLive.value.toString()

        var nameError by remember { mutableStateOf(!nameRegex.matcher(nameLive.value.toString()).matches()) }
        var emailError by remember { mutableStateOf(!emailRegex.matcher(emailLive.value.toString()).matches()) }
        var phoneNumberError by remember { mutableStateOf(!phoneNumberRegex.matcher(phoneNumberLive.value.toString()).matches()) }
        var streetError by remember { mutableStateOf(!streetRegex.matcher(streetLive.value.toString()).matches()) }
        var zipError by remember { mutableStateOf(!zipRegex.matcher(zipLive.value.toString()).matches()) }
        var cityError by remember { mutableStateOf(!cityRegex.matcher(cityLive.value.toString()).matches()) }
        var instructionsError by remember { mutableStateOf(!instructionsRegex.matcher(instructionsLive.value.toString()).matches()) }

        val nameColor = if (nameError) {
            Color(android.graphics.Color.parseColor(DataHolder.guiConfig["contact"]!!["nameErrorColor"] as String))
        } else {
            Color(android.graphics.Color.parseColor(DataHolder.guiConfig["contact"]!!["nameColor"] as String))
        }
        val emailColor = if (emailError) {
            Color(android.graphics.Color.parseColor(DataHolder.guiConfig["contact"]!!["emailErrorColor"] as String))
        } else {
            Color(android.graphics.Color.parseColor(DataHolder.guiConfig["contact"]!!["emailColor"] as String))
        }
        val phoneNumberColor = if (phoneNumberError) {
            Color(android.graphics.Color.parseColor(DataHolder.guiConfig["contact"]!!["phoneNumberErrorColor"] as String))
        } else {
            Color(android.graphics.Color.parseColor(DataHolder.guiConfig["contact"]!!["phoneNumberColor"] as String))
        }
        val streetColor = if (streetError) {
            Color(android.graphics.Color.parseColor(DataHolder.guiConfig["contact"]!!["streetErrorColor"] as String))
        } else {
            Color(android.graphics.Color.parseColor(DataHolder.guiConfig["contact"]!!["streetColor"] as String))
        }
        val zipColor = if (zipError) {
            Color(android.graphics.Color.parseColor(DataHolder.guiConfig["contact"]!!["zipErrorColor"] as String))
        } else {
            Color(android.graphics.Color.parseColor(DataHolder.guiConfig["contact"]!!["zipColor"] as String))
        }
        val cityColor = if (cityError) {
            Color(android.graphics.Color.parseColor(DataHolder.guiConfig["contact"]!!["cityErrorColor"] as String))
        } else {
            Color(android.graphics.Color.parseColor(DataHolder.guiConfig["contact"]!!["cityColor"] as String))
        }
        val instructionsColor = if (instructionsError) {
            Color(android.graphics.Color.parseColor(DataHolder.guiConfig["contact"]!!["instructionsErrorColor"] as String))
        } else {
            Color(android.graphics.Color.parseColor(DataHolder.guiConfig["contact"]!!["instructionsColor"] as String))
        }

        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                val errors = importContact(
                    useInternationalNames
                )
                if (errors[0] != null) {
                    nameError = errors[0]!!
                }
                if (errors[1] != null) {
                    emailError = errors[1]!!
                }
            } else {
                Toast.makeText(
                    applicationContext,
                    DataHolder.guiConfig["contact"]!![DataHolder.internationalizeLabel("accessContactsError", useInternationalNames)].toString(),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        val textWidthModifierForSingleFloatingButton = Modifier.fillMaxWidth(0.99f)
        val textWidthModifierForTwoFloatingButtons = Modifier.fillMaxWidth(0.78f)
        val fieldTextTitleSizeModifier = Modifier.fillMaxWidth(0.75f)
        val phoneFieldTextTitleSizeModifier = Modifier.fillMaxWidth(0.7f)

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
                item(0) {
                    Column {
                        Text(
                            DataHolder.guiConfig["chip"]!![DataHolder.internationalizeLabel("contactData", useInternationalNames)] as String,
                            fontSize = (DataHolder.guiConfig["contact"]!!["contactDataFontSize"] as Long).toInt().sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 14.dp)
                        )
                    }
                }
                item(1) {
                    Column {
                        Text(
                            text = DataHolder.guiConfig["contact"]!![DataHolder.internationalizeLabel("name", useInternationalNames)] as String,
                            fontSize = (DataHolder.guiConfig["contact"]!!["inputTitleFontSize"] as Long).toInt().sp,
                            modifier = fieldTextTitleSizeModifier
                        )
                        Row {
                            Box(
                                modifier = Modifier.weight(2.4f)
                            ) {
                                BasicTextField(
                                    value = nameLive.observeAsState().value.toString(),
                                    onValueChange = { text ->
                                        run {
                                            if (text != "") {
                                                nameError = !nameRegex.matcher(text).matches()
                                                nameLive.postValue(text)
                                                name = text
                                            }
                                        }
                                    },
                                    modifier = textWidthModifierForSingleFloatingButton.background(nameColor),
                                    maxLines = (DataHolder.guiConfig["contact"]!!["nameMaxLines"] as Long).toInt(),
                                    singleLine = (DataHolder.guiConfig["contact"]!!["isNameSingleLine"] as Boolean)
                                )
                            }

                            FloatingActionButton(
                                onClick = {
                                    if (nameIconTint != Color.Gray) {
                                        if (nameSpeechRecognizer == null) {
                                            nameSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(applicationContext, ComponentName.unflattenFromString(GOOGLE_RECOGNITION_SERVICE_NAME))
                                            nameSpeechRecognizer?.setRecognitionListener(object : RecognitionListener {
                                                override fun onReadyForSpeech(bundle: Bundle) {}
                                                override fun onBeginningOfSpeech() {}
                                                override fun onRmsChanged(v: Float) {}
                                                override fun onBufferReceived(bytes: ByteArray) {}
                                                override fun onEndOfSpeech() {}
                                                override fun onError(i: Int) {}
                                                override fun onResults(bundle: Bundle) {}
                                                override fun onPartialResults(bundle: Bundle) {}
                                                override fun onEvent(i: Int, bundle: Bundle) {}
                                            })
                                        }
                                        if (nameIconTint == tintContentColor) {
                                            val speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                            speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                            speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                            nameSpeechRecognizer?.startListening(speechRecognizerIntent)
                                            startActivityForResult(speechRecognizerIntent, DataHolder.NAME_SPEECH_REQUEST_CODE)
                                            nameSpeechRecognizer?.stopListening()
                                        }
                                    }
                                },
                                containerColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["contact"]!!["microphoneBackgroundColor"] as String)),
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(Dp((DataHolder.guiConfig["main"]!!["emptyCartSize"] as Long).toFloat()))
                                    .absoluteOffset(x = Dp((DataHolder.guiConfig["contact"]!!["microphoneButtonOffsetX"] as Long).toFloat()), y = Dp((DataHolder.guiConfig["contact"]!!["microphoneButtonOffsetY"] as Long).toFloat()))
                                    .weight(0.4f)
                            ) {
                                Icon(Icons.Filled.Mic, "", tint = nameIconTint)
                            }
                        }
                    }
                }
                item(2) {
                    Column {
                        Text(
                            text = DataHolder.guiConfig["contact"]!![DataHolder.internationalizeLabel("phoneNumber", useInternationalNames)] as String,
                            fontSize = (DataHolder.guiConfig["contact"]!!["inputTitleFontSize"] as Long).toInt().sp,
                            modifier = phoneFieldTextTitleSizeModifier
                        )
                        Row {
                            Box(
                                modifier = Modifier.weight(2.4f)
                            ) {
                                BasicTextField(
                                    value = phoneNumberLive.observeAsState().value.toString(),
                                    onValueChange = { text ->
                                        run {
                                            if (text != "") {
                                                phoneNumberError = !phoneNumberRegex.matcher(text).matches()
                                                phoneNumberLive.postValue(text)
                                                phoneNumber = text
                                            }
                                        }
                                    },
                                    modifier = textWidthModifierForTwoFloatingButtons.background(phoneNumberColor),
                                    maxLines = (DataHolder.guiConfig["contact"]!!["phoneNumberMaxLines"] as Long).toInt(),
                                    singleLine = DataHolder.guiConfig["contact"]!!["isPhoneNumberSingleLine"] as Boolean,
                                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Phone)
                                )
                            }
                            Scaffold(modifier = Modifier.weight(0.4f)) {
                                FloatingActionButton(
                                    onClick = {
                                        when {
                                            ContextCompat.checkSelfPermission(
                                                applicationContext,
                                                Manifest.permission.READ_CONTACTS
                                            ) == PackageManager.PERMISSION_GRANTED -> {
                                                val errors = importContact(
                                                    useInternationalNames
                                                )
                                                if (errors[0] != null) {
                                                    nameError = errors[0]!!
                                                }
                                                if (errors[1] != null) {
                                                    emailError = errors[1]!!
                                                }
                                            } else -> {
                                                launcher.launch(Manifest.permission.READ_CONTACTS)
                                            }
                                        }
                                    },
                                    containerColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["contact"]!!["importContactsBackgroundColorWearOs"] as String)),
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .size(Dp((DataHolder.guiConfig["main"]!!["emptyCartSize"] as Long).toFloat()))
                                        .absoluteOffset(
                                            x = Dp((DataHolder.guiConfig["contact"]!!["microphoneButtonOffsetX"] as Long).toFloat() - 20),
                                            y = Dp((DataHolder.guiConfig["contact"]!!["microphoneButtonOffsetY"] as Long).toFloat())
                                        )
                                ) {
                                    Icon(Icons.Filled.ImportExport, "", tint = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["contact"]!!["importContactsContentColorWearOs"] as String)))
                                }
                                FloatingActionButton(
                                    onClick = {
                                        if (phoneNumberIconTint != Color.Gray) {
                                            if (phoneNumberSpeechRecognizer == null) {
                                                phoneNumberSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(applicationContext, ComponentName.unflattenFromString(GOOGLE_RECOGNITION_SERVICE_NAME))
                                                phoneNumberSpeechRecognizer?.setRecognitionListener(object : RecognitionListener {
                                                    override fun onReadyForSpeech(bundle: Bundle) {}
                                                    override fun onBeginningOfSpeech() {}
                                                    override fun onRmsChanged(v: Float) {}
                                                    override fun onBufferReceived(bytes: ByteArray) {}
                                                    override fun onEndOfSpeech() {}
                                                    override fun onError(i: Int) {}
                                                    override fun onResults(bundle: Bundle) {}
                                                    override fun onPartialResults(bundle: Bundle) {}
                                                    override fun onEvent(i: Int, bundle: Bundle) {}
                                                })
                                            }
                                            if (phoneNumberIconTint == tintContentColor) {
                                                val speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                                speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                                speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                                phoneNumberSpeechRecognizer?.startListening(speechRecognizerIntent)
                                                startActivityForResult(speechRecognizerIntent, DataHolder.PHONE_NUMBER_SPEECH_REQUEST_CODE)
                                                phoneNumberSpeechRecognizer?.stopListening()
                                            }
                                        }
                                    },
                                    containerColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["contact"]!!["microphoneBackgroundColor"] as String)),
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .size(Dp((DataHolder.guiConfig["main"]!!["emptyCartSize"] as Long).toFloat()))
                                        .absoluteOffset(x = Dp((DataHolder.guiConfig["contact"]!!["microphoneButtonOffsetX"] as Long).toFloat()), y = Dp((DataHolder.guiConfig["contact"]!!["microphoneButtonOffsetY"] as Long).toFloat()))
                                ) {
                                    Icon(Icons.Filled.Mic, "", tint = phoneNumberIconTint)
                                }
                            }
                        }
                    }
                }
                item(3) {
                    Column {
                        Text(
                            text = DataHolder.guiConfig["contact"]!![DataHolder.internationalizeLabel("emailAddress", useInternationalNames)] as String,
                            fontSize = (DataHolder.guiConfig["contact"]!!["inputTitleFontSize"] as Long).toInt().sp,
                            modifier = fieldTextTitleSizeModifier
                        )
                        Row {
                            Box(
                                modifier = Modifier.weight(2.4f)
                            ) {
                                BasicTextField(
                                    value = emailLive.observeAsState().value.toString(),
                                    onValueChange = { text ->
                                        run {
                                            if (text != "") {
                                                emailError = !emailRegex.matcher(text).matches()
                                                emailLive.postValue(text)
                                                email = text
                                            }
                                        }
                                    },
                                    modifier = if (DataHolder.useWhatsApp != true) {
                                        textWidthModifierForSingleFloatingButton.background(emailColor)
                                    } else {
                                        textWidthModifierForSingleFloatingButton.background(Color(android.graphics.Color.parseColor(DataHolder.guiConfig["contact"]!!["emailGrayedOutColor"] as String)))
                                    },
                                    maxLines = (DataHolder.guiConfig["contact"]!!["emailMaxLines"] as Long).toInt(),
                                    singleLine = DataHolder.guiConfig["contact"]!!["isEmailSingleLine"] as Boolean,
                                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Email)
                                )
                            }
                            FloatingActionButton(
                                onClick = {
                                    if (emailIconTint != Color.Gray) {
                                        if (emailSpeechRecognizer == null) {
                                            emailSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(applicationContext, ComponentName.unflattenFromString(GOOGLE_RECOGNITION_SERVICE_NAME))
                                            emailSpeechRecognizer?.setRecognitionListener(object : RecognitionListener {
                                                override fun onReadyForSpeech(bundle: Bundle) {}
                                                override fun onBeginningOfSpeech() {}
                                                override fun onRmsChanged(v: Float) {}
                                                override fun onBufferReceived(bytes: ByteArray) {}
                                                override fun onEndOfSpeech() {}
                                                override fun onError(i: Int) {}
                                                override fun onResults(bundle: Bundle) {}
                                                override fun onPartialResults(bundle: Bundle) {}
                                                override fun onEvent(i: Int, bundle: Bundle) {}
                                            })
                                        }
                                        if (emailIconTint == tintContentColor) {
                                            val speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                            speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                            speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                            emailSpeechRecognizer?.startListening(speechRecognizerIntent)
                                            startActivityForResult(speechRecognizerIntent, DataHolder.EMAIL_SPEECH_REQUEST_CODE)
                                            emailSpeechRecognizer?.stopListening()
                                        }
                                    }
                                },
                                containerColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["contact"]!!["microphoneBackgroundColor"] as String)),
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(Dp((DataHolder.guiConfig["main"]!!["emptyCartSize"] as Long).toFloat()))
                                    .absoluteOffset(x = Dp((DataHolder.guiConfig["contact"]!!["microphoneButtonOffsetX"] as Long).toFloat()), y = Dp((DataHolder.guiConfig["contact"]!!["microphoneButtonOffsetY"] as Long).toFloat()))
                                    .weight(0.4f)
                            ) {
                                Icon(Icons.Filled.Mic, "", tint = emailIconTint)
                            }
                        }
                    }
                }
                item(4) {
                    Column {
                        Text(
                            text = DataHolder.guiConfig["contact"]!![DataHolder.internationalizeLabel("streetAndNumber", useInternationalNames)] as String,
                            fontSize = (DataHolder.guiConfig["contact"]!!["inputTitleFontSize"] as Long).toInt().sp,
                            modifier = fieldTextTitleSizeModifier
                        )
                        Row {
                            Box(
                                modifier = Modifier.weight(2.4f)
                            ) {
                                BasicTextField(
                                    value = streetLive.observeAsState().value.toString(),
                                    onValueChange = { text ->
                                        run {
                                            if (text != "") {
                                                streetError = !streetRegex.matcher(text).matches()
                                                streetLive.postValue(text)
                                                street = text
                                            }
                                        }
                                    },
                                    modifier = if (DataHolder.deliveryMode == "delivery") {
                                        textWidthModifierForSingleFloatingButton.background(streetColor)
                                    } else {
                                        textWidthModifierForSingleFloatingButton.background(Color(android.graphics.Color.parseColor(DataHolder.guiConfig["contact"]!!["streetGrayedOutColor"] as String)))
                                    },
                                    maxLines = (DataHolder.guiConfig["contact"]!!["streetMaxLines"] as Long).toInt(),
                                    singleLine = DataHolder.guiConfig["contact"]!!["isStreetSingleLine"] as Boolean
                                )
                            }
                            FloatingActionButton(
                                onClick = {
                                    if (streetIconTint != Color.Gray) {
                                        if (streetSpeechRecognizer == null) {
                                            streetSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(applicationContext, ComponentName.unflattenFromString(GOOGLE_RECOGNITION_SERVICE_NAME))
                                            streetSpeechRecognizer?.setRecognitionListener(object : RecognitionListener {
                                                override fun onReadyForSpeech(bundle: Bundle) {}
                                                override fun onBeginningOfSpeech() {}
                                                override fun onRmsChanged(v: Float) {}
                                                override fun onBufferReceived(bytes: ByteArray) {}
                                                override fun onEndOfSpeech() {}
                                                override fun onError(i: Int) {}
                                                override fun onResults(bundle: Bundle) {}
                                                override fun onPartialResults(bundle: Bundle) {}
                                                override fun onEvent(i: Int, bundle: Bundle) {}
                                            })
                                        }
                                        if (streetIconTint == tintContentColor) {
                                            val speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                            speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                            speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                            streetSpeechRecognizer?.startListening(speechRecognizerIntent)
                                            startActivityForResult(speechRecognizerIntent, DataHolder.STREET_SPEECH_REQUEST_CODE)
                                            streetSpeechRecognizer?.stopListening()
                                        }
                                    }
                                },
                                containerColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["contact"]!!["microphoneBackgroundColor"] as String)),
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(Dp((DataHolder.guiConfig["main"]!!["emptyCartSize"] as Long).toFloat()))
                                    .absoluteOffset(x = Dp((DataHolder.guiConfig["contact"]!!["microphoneButtonOffsetX"] as Long).toFloat()), y = Dp((DataHolder.guiConfig["contact"]!!["microphoneButtonOffsetY"] as Long).toFloat()))
                                    .weight(0.4f)
                            ) {
                                Icon(Icons.Filled.Mic, "", tint = streetIconTint)
                            }
                        }
                    }
                }
                item(5) {
                    Column {
                        Text(
                            text = DataHolder.guiConfig["contact"]!![DataHolder.internationalizeLabel("zip", useInternationalNames)] as String,
                            fontSize = (DataHolder.guiConfig["contact"]!!["inputTitleFontSize"] as Long).toInt().sp,
                            modifier = fieldTextTitleSizeModifier
                        )
                        Row {
                            Box(
                                modifier = Modifier.weight(2.4f)
                            ) {
                                BasicTextField(
                                    value = zipLive.observeAsState().value.toString(),
                                    onValueChange = { text ->
                                        run {
                                            if (text != "") {
                                                zipError = !zipRegex.matcher(text).matches()
                                                zipLive.postValue(text)
                                                zip = text
                                            }
                                        }
                                    },
                                    modifier = if (DataHolder.deliveryMode == "delivery") {
                                        textWidthModifierForSingleFloatingButton.background(zipColor)
                                    } else {
                                        textWidthModifierForSingleFloatingButton.background(Color(android.graphics.Color.parseColor(DataHolder.guiConfig["contact"]!!["zipGrayedOutColor"] as String)))
                                    },
                                    maxLines = (DataHolder.guiConfig["contact"]!!["zipMaxLines"] as Long).toInt(),
                                    singleLine = DataHolder.guiConfig["contact"]!!["isZipSingleLine"] as Boolean
                                )
                            }
                            FloatingActionButton(
                                onClick = {
                                    if (zipIconTint != Color.Gray) {
                                        if (zipSpeechRecognizer == null) {
                                            zipSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(applicationContext, ComponentName.unflattenFromString(GOOGLE_RECOGNITION_SERVICE_NAME))
                                            zipSpeechRecognizer?.setRecognitionListener(object : RecognitionListener {
                                                override fun onReadyForSpeech(bundle: Bundle) {}
                                                override fun onBeginningOfSpeech() {}
                                                override fun onRmsChanged(v: Float) {}
                                                override fun onBufferReceived(bytes: ByteArray) {}
                                                override fun onEndOfSpeech() {}
                                                override fun onError(i: Int) {}
                                                override fun onResults(bundle: Bundle) {}
                                                override fun onPartialResults(bundle: Bundle) {}
                                                override fun onEvent(i: Int, bundle: Bundle) {}
                                            })
                                        }
                                        if (zipIconTint == tintContentColor) {
                                            val speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                            speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                            speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                            zipSpeechRecognizer?.startListening(speechRecognizerIntent)
                                            startActivityForResult(speechRecognizerIntent, DataHolder.ZIP_SPEECH_REQUEST_CODE)
                                            zipSpeechRecognizer?.stopListening()
                                        }
                                    }
                                },
                                containerColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["contact"]!!["microphoneBackgroundColor"] as String)),
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(Dp((DataHolder.guiConfig["main"]!!["emptyCartSize"] as Long).toFloat()))
                                    .absoluteOffset(x = Dp((DataHolder.guiConfig["contact"]!!["microphoneButtonOffsetX"] as Long).toFloat()), y = Dp((DataHolder.guiConfig["contact"]!!["microphoneButtonOffsetY"] as Long).toFloat()))
                                    .weight(0.4f)
                            ) {
                                Icon(Icons.Filled.Mic, "", tint = zipIconTint)
                            }
                        }
                    }
                }
                item(6) {
                    Column {
                        Text(
                            text = DataHolder.guiConfig["contact"]!![DataHolder.internationalizeLabel("city", useInternationalNames)] as String,
                            fontSize = (DataHolder.guiConfig["contact"]!!["inputTitleFontSize"] as Long).toInt().sp,
                            modifier = fieldTextTitleSizeModifier
                        )
                        Row {
                            Box(
                                modifier = Modifier.weight(2.4f)
                            ) {
                                BasicTextField(
                                    value = cityLive.observeAsState().value.toString(),
                                    onValueChange = { text ->
                                        run {
                                            if (text != "") {
                                                cityError = !cityRegex.matcher(text).matches()
                                                cityLive.postValue(text)
                                                city = text
                                            }
                                        }
                                    },
                                    modifier = if (DataHolder.deliveryMode == "delivery") {
                                        textWidthModifierForSingleFloatingButton.background(cityColor)
                                    } else {
                                        textWidthModifierForSingleFloatingButton.background(Color(android.graphics.Color.parseColor(DataHolder.guiConfig["contact"]!!["cityGrayedOutColor"] as String)))
                                    },
                                    maxLines = (DataHolder.guiConfig["contact"]!!["cityMaxLines"] as Long).toInt(),
                                    singleLine = DataHolder.guiConfig["contact"]!!["isCitySingleLine"] as Boolean
                                )
                            }
                            FloatingActionButton(
                                onClick = {
                                    if (cityIconTint != Color.Gray) {
                                        if (citySpeechRecognizer == null) {
                                            citySpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(applicationContext, ComponentName.unflattenFromString(GOOGLE_RECOGNITION_SERVICE_NAME))
                                            citySpeechRecognizer?.setRecognitionListener(object : RecognitionListener {
                                                override fun onReadyForSpeech(bundle: Bundle) {}
                                                override fun onBeginningOfSpeech() {}
                                                override fun onRmsChanged(v: Float) {}
                                                override fun onBufferReceived(bytes: ByteArray) {}
                                                override fun onEndOfSpeech() {}
                                                override fun onError(i: Int) {}
                                                override fun onResults(bundle: Bundle) {}
                                                override fun onPartialResults(bundle: Bundle) {}
                                                override fun onEvent(i: Int, bundle: Bundle) {}
                                            })
                                        }
                                        if (cityIconTint == tintContentColor) {
                                            val speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                            speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                            speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                            citySpeechRecognizer?.startListening(speechRecognizerIntent)
                                            startActivityForResult(speechRecognizerIntent, DataHolder.CITY_SPEECH_REQUEST_CODE)
                                            citySpeechRecognizer?.stopListening()
                                        }
                                    }
                                },
                                containerColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["contact"]!!["microphoneBackgroundColor"] as String)),
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(Dp((DataHolder.guiConfig["main"]!!["emptyCartSize"] as Long).toFloat()))
                                    .absoluteOffset(x = Dp((DataHolder.guiConfig["contact"]!!["microphoneButtonOffsetX"] as Long).toFloat()), y = Dp((DataHolder.guiConfig["contact"]!!["microphoneButtonOffsetY"] as Long).toFloat()))
                                    .weight(0.4f)
                            ) {
                                Icon(Icons.Filled.Mic, "", tint = cityIconTint)
                            }
                        }
                    }
                }
                item(7) {
                    Column {
                        Text(
                            text = DataHolder.guiConfig["contact"]!![DataHolder.internationalizeLabel("instructions", useInternationalNames)] as String,
                            fontSize = (DataHolder.guiConfig["contact"]!!["inputTitleFontSize"] as Long).toInt().sp,
                            modifier = fieldTextTitleSizeModifier
                        )
                        Row {
                            Box(
                                modifier = Modifier.weight(2.4f)
                            ) {
                                BasicTextField(
                                    value = instructionsLive.observeAsState().value.toString(),
                                    onValueChange = { text ->
                                        run {
                                            if (text != "") {
                                                instructionsError = !instructionsRegex.matcher(text).matches()
                                                instructionsLive.postValue(text)
                                                instructions = text
                                            }
                                        }
                                    },
                                    modifier = textWidthModifierForSingleFloatingButton.background(instructionsColor),
                                    maxLines = (DataHolder.guiConfig["contact"]!!["instructionsMaxLines"] as Long).toInt(),
                                    singleLine = DataHolder.guiConfig["contact"]!!["isInstructionsSingleLine"] as Boolean
                                )
                            }
                            FloatingActionButton(
                                onClick = {
                                    if (instructionsIconTint != Color.Gray) {
                                        if (instructionsSpeechRecognizer == null) {
                                            instructionsSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(applicationContext, ComponentName.unflattenFromString(GOOGLE_RECOGNITION_SERVICE_NAME))
                                            instructionsSpeechRecognizer?.setRecognitionListener(object : RecognitionListener {
                                                override fun onReadyForSpeech(bundle: Bundle) {}
                                                override fun onBeginningOfSpeech() {}
                                                override fun onRmsChanged(v: Float) {}
                                                override fun onBufferReceived(bytes: ByteArray) {}
                                                override fun onEndOfSpeech() {}
                                                override fun onError(i: Int) {}
                                                override fun onResults(bundle: Bundle) {}
                                                override fun onPartialResults(bundle: Bundle) {}
                                                override fun onEvent(i: Int, bundle: Bundle) {}
                                            })
                                        }
                                        if (instructionsIconTint == tintContentColor) {
                                            val speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                            speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                            speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                            instructionsSpeechRecognizer?.startListening(speechRecognizerIntent)
                                            startActivityForResult(speechRecognizerIntent, DataHolder.INSTRUCTIONS_SPEECH_REQUEST_CODE)
                                            instructionsSpeechRecognizer?.stopListening()
                                        }
                                    }
                                },
                                containerColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["contact"]!!["microphoneBackgroundColor"] as String)),
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(Dp((DataHolder.guiConfig["main"]!!["emptyCartSize"] as Long).toFloat()))
                                    .absoluteOffset(x = Dp((DataHolder.guiConfig["contact"]!!["microphoneButtonOffsetX"] as Long).toFloat()), y = Dp((DataHolder.guiConfig["contact"]!!["microphoneButtonOffsetY"] as Long).toFloat()))
                                    .weight(0.4f)
                            ) {
                                Icon(Icons.Filled.Mic, "", tint = instructionsIconTint)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun importContact(
        useInternationalNames: Boolean
    ): Array<Boolean?> {
        var nameError: Boolean? = null
        var emailError: Boolean? = null

        val editor: SharedPreferences.Editor = DataHolder.sharedPreferences.edit()

        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            null,
            ContactsContract.CommonDataKinds.Phone.NUMBER + " LIKE ?",
            arrayOf("%" + phoneNumber.chunked(1).joinToString("%") + "%"),
            null
        )

        if (cursor != null && cursor.moveToFirst()) {
            val nameColumnIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
            if (nameColumnIndex >= 0) {
                val text = cursor.getString(nameColumnIndex)

                nameError = !nameRegex.matcher(text).matches()
                nameLive.value = text  // postValue not fast enough before screen is reconstructed after import
                name = text
            }

            val contactIdIndex =
                cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            if (contactIdIndex >= 0) {
                val contactId = cursor.getString(contactIdIndex)

                // Query for additional contact details using the contact ID
                val contactDetailsCursor = contentResolver.query(
                    ContactsContract.Data.CONTENT_URI,
                    null,
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                    arrayOf(contactId),
                    null
                )

                while (contactDetailsCursor != null && contactDetailsCursor.moveToNext()) {
                    val mimeTypeColumnIndex =
                        contactDetailsCursor.getColumnIndex(ContactsContract.Data.MIMETYPE)
                    if (mimeTypeColumnIndex >= 0) {
                        val mimeType = contactDetailsCursor.getString(mimeTypeColumnIndex)

                        when (mimeType) {
                            ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE -> {
                                val emailColumnIndex =
                                    contactDetailsCursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.DATA)
                                if (emailColumnIndex >= 0) {
                                    val text = contactDetailsCursor.getString(emailColumnIndex)

                                    emailError = !emailRegex.matcher(text).matches()
                                    emailLive.value = text // postValue not fast enough before screen is reconstructed after import
                                    email = text
                                }
                            }
                        }
                    }
                }

                Toast.makeText(
                    applicationContext,
                    DataHolder.guiConfig["contact"]!![DataHolder.internationalizeLabel("importSuccessMessage", useInternationalNames)].toString(),
                    Toast.LENGTH_SHORT
                ).show()

                contactDetailsCursor?.close()
            }
        } else {
            Toast.makeText(
                applicationContext,
                DataHolder.guiConfig["contact"]!![DataHolder.internationalizeLabel("noContactFound", useInternationalNames)].toString(),
                Toast.LENGTH_LONG
            ).show()
        }
        cursor?.close()

        editor.apply()

        return arrayOf(nameError, emailError)
    }

    @Override
    @Deprecated("method to gather microphone input")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK) {
            val results: List<String>? = data!!.getStringArrayListExtra(
                RecognizerIntent.EXTRA_RESULTS
            )
            val audioResults = results!![0]

            if (requestCode == DataHolder.NAME_SPEECH_REQUEST_CODE) {
                nameLive.postValue(audioResults)
                name = audioResults
            } else if (requestCode == DataHolder.PHONE_NUMBER_SPEECH_REQUEST_CODE) {
                phoneNumberLive.postValue(audioResults)
                phoneNumber = audioResults
            } else if (requestCode == DataHolder.EMAIL_SPEECH_REQUEST_CODE) {
                emailLive.postValue(audioResults)
                email = audioResults
            } else if (requestCode == DataHolder.STREET_SPEECH_REQUEST_CODE) {
                streetLive.postValue(audioResults)
                street = audioResults
            } else if (requestCode == DataHolder.ZIP_SPEECH_REQUEST_CODE) {
                zipLive.postValue(audioResults)
                zip = audioResults
            } else if (requestCode == DataHolder.CITY_SPEECH_REQUEST_CODE) {
                cityLive.postValue(audioResults)
                city = audioResults
            } else if (requestCode == DataHolder.INSTRUCTIONS_SPEECH_REQUEST_CODE) {
                instructionsLive.postValue(audioResults)
                instructions = audioResults
            }
        }
    }
}