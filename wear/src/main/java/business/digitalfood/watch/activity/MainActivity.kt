package business.digitalfood.watch.activity

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.RemoveShoppingCart
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.MutableLiveData
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import business.digitalfood.watch.Constants.LOCAL_LANGUAGE
import business.digitalfood.watch.DataHolder
import business.digitalfood.watch.DataHolder.formatPrice
import business.digitalfood.watch.R
import business.digitalfood.watch.model.Food
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Collections
import java.util.Locale


class MainActivity : ComponentActivity() {

    companion object {
        var whatToStart = MutableLiveData<String>()
    }

    var refreshing = MutableLiveData(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val language = applicationContext.resources.configuration.locales[0]
        val useInternationalNames = !language.language.equals(LOCAL_LANGUAGE)

        setContent {
            val startMe = whatToStart.observeAsState().value
            if (startMe == "app") {
                WearApp(DataHolder.food, applicationContext, this::refresh, useInternationalNames)
            } else if (startMe == "internetError") {
                InternetError(applicationContext)
            } else if (startMe == "loginError") {
                LoginError(applicationContext)
            } else if (startMe == "restaurantClosedError") {
                RestaurantClosedError(applicationContext)
            } else if (startMe == "updateError") {
                UpdateError(applicationContext)
            } else if (startMe == "inactive") {
                InactiveScreen(applicationContext)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (refreshing.value == false) {
            DataHolder.cart = Collections.synchronizedMap(HashMap())
            DataHolder.totalPrice.postValue(DataHolder.calculateCartPrice(DataHolder.cart))
            DataHolder.foodToOptionsMapping = Collections.synchronizedMap(HashMap(HashMap()))
        }
    }

    private fun refresh() {
        DataHolder.contactDataError.postValue(false)
        DataHolder.deliveryModeError.postValue(false)
        val intent = Intent(applicationContext, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    @OptIn(ExperimentalWearFoundationApi::class)
    @Composable
    fun WearApp(food: MutableList<Food>, context: Context, refresh: () -> Unit, useInternationalNames: Boolean) {
        var contactDataError = DataHolder.contactDataError.observeAsState().value!!
        val contactCardColor = if (contactDataError) {
            Color(android.graphics.Color.parseColor(DataHolder.guiConfig["main"]!!["contactCardErrorColor"] as String))
        } else {
            Color(android.graphics.Color.parseColor(DataHolder.guiConfig["main"]!!["contactCardColor"] as String))
        }

        var deliveryModeDataError = DataHolder.deliveryModeError.observeAsState().value!!
        val deliveryModeCardColor = if (deliveryModeDataError) {
            Color(android.graphics.Color.parseColor(DataHolder.guiConfig["main"]!!["deliveryModeCardErrorColor"] as String))
        } else {
            Color(android.graphics.Color.parseColor(DataHolder.guiConfig["main"]!!["deliveryModeCardColor"] as String))
        }

        val listState = rememberScalingLazyListState(initialCenterItemIndex = -1)

        LaunchedEffect(refreshing.value) {
            if (refreshing.value == true) {
                refreshing.postValue(false)
            }
        }

        @Suppress("DEPRECATION") // no pullRefresh modifier in v3 yet
        SwipeRefresh(
            state = rememberSwipeRefreshState(isRefreshing = refreshing.value!!),
            onRefresh = {
                refreshing.postValue(true)
                refresh()
            },
            refreshTriggerDistance = (DataHolder.guiConfig["main"]!!["refreshTriggerDistance"] as Long).toInt().dp
        ) {
            Scaffold(positionIndicator = {
                PositionIndicator(scalingLazyListState = listState)
            }) {
                androidx.compose.material3.Scaffold(
                    floatingActionButton = {
                        val coroutineScope = rememberCoroutineScope()

                        FloatingActionButton(
                            onClick = {
                                coroutineScope.launch {
                                    listState.animateScrollToItem(index = 0)
                                }
                            },
                            containerColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["main"]!!["goUpColor"] as String)),
                            shape = CircleShape,
                            modifier = Modifier
                                .size(Dp((DataHolder.guiConfig["main"]!!["goUpSize"] as Long).toFloat()))
                                .absoluteOffset(y = Dp((DataHolder.guiConfig["main"]!!["goUpYOffsetFactor"] as Double).toFloat() * context.resources.configuration.screenHeightDp))
                        ) {
                            Icon(Icons.Filled.ArrowDropUp, "", tint = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["main"]!!["goUpTintColor"] as String)))
                        }
                    },
                    floatingActionButtonPosition = if (context.resources.configuration.isScreenRound) {
                        FabPosition.Center
                    } else {
                        FabPosition.End
                    },
                    containerColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["main"]!!["containerColor"] as String))
                ) { padding ->
                    val focusRequester = rememberActiveFocusRequester()
                    val coroutineScope = rememberCoroutineScope()
                    ScalingLazyColumn(
                        modifier = if (context.resources.configuration.isScreenRound) {
                            Modifier
                                .padding(
                                    start = ((DataHolder.ROUND_WEAR_PADDING_FACTOR / 5) * context.resources.configuration.screenWidthDp).dp,
                                    end = ((DataHolder.ROUND_WEAR_PADDING_FACTOR / 5) * context.resources.configuration.screenWidthDp).dp,
                                )
                                .fillMaxSize()
                                .onRotaryScrollEvent {
                                    coroutineScope.launch {
                                        listState.scrollBy(it.verticalScrollPixels)
                                        listState.animateScrollBy(it.verticalScrollPixels)
                                    }
                                    true
                                }
                        } else {
                            Modifier
                                .fillMaxSize()
                        }
                        .focusRequester(focusRequester)
                        .focusable(),
                        autoCentering = AutoCenteringParams(itemIndex = 0),
                        state = listState,
                    ) {

                        val contentModifier = Modifier
                            .padding(bottom = (DataHolder.guiConfig["main"]!!["paddingBottom"] as Long).toInt().dp)
                            .height((DataHolder.guiConfig["main"]!!["height"] as Long).toInt().dp)
                            .fillMaxWidth()

                        item(-4) {
                            CartChip(
                                totalPrice = DataHolder.totalPrice.observeAsState().value!!,
                                modifier = contentModifier,
                                useInternationalNames = useInternationalNames
                            )
                            FloatingActionButton(
                                onClick = {
                                    DataHolder.cart = Collections.synchronizedMap(HashMap())
                                    DataHolder.totalPrice.postValue(DataHolder.calculateCartPrice(DataHolder.cart))
                                    DataHolder.foodToOptionsMapping = Collections.synchronizedMap(HashMap(HashMap()))
                                    refresh()
                                },
                                containerColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["main"]!!["emptyCartColor"] as String)),
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(Dp((DataHolder.guiConfig["main"]!!["emptyCartSize"] as Long).toFloat()))
                                    .absoluteOffset(
                                        x = if (context.resources.configuration.isScreenRound) {
                                            (context.resources.configuration.screenWidthDp + (DataHolder.guiConfig["main"]!!["emptyCartXOffsetRound"] as Long).toInt()).dp
                                        } else {
                                            (context.resources.configuration.screenWidthDp + (DataHolder.guiConfig["main"]!!["emptyCartXOffsetSquare"] as Long).toInt() + ((DataHolder.ROUND_WEAR_PADDING_FACTOR / 5) * context.resources.configuration.screenWidthDp)).dp
                                        }
                                    )
                            ) {
                                Icon(Icons.Filled.RemoveShoppingCart, "", tint = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["main"]!!["emptyCartTintColor"] as String)))
                            }
                        }

                        item(-3) {
                            AddressChip(
                                modifier = contentModifier,
                                color = contactCardColor,
                                useInternationalNames = useInternationalNames
                            )
                        }

                        item(-2) {
                            ModeChip(
                                modifier = contentModifier,
                                color = deliveryModeCardColor,
                                useInternationalNames = useInternationalNames
                            )
                        }

                        if (DataHolder.guiConfig["main"]!!["enableFoodChip"] as Boolean) {
                            item(-1) {
                                FoodOverviewChip(modifier = contentModifier, useInternationalNames = useInternationalNames)
                            }
                        }

                        val actualTime = Calendar.getInstance()

                        val actualDay = actualTime.getDisplayName(
                            Calendar.DAY_OF_WEEK,
                            Calendar.LONG,
                            Locale.ENGLISH
                        )!!.lowercase()

                        food.forEachIndexed { key, it ->
                            if (it.isHidden != true) {
                                var backgroundBitmap: BitmapPainter? = null // if image fetched from the cloud into the shared preferences
                                val backgroundResourceIdentifier: Int = if (it.hasImage == true) { // if image exists in the resources folder (standard case)
                                    context.resources.getIdentifier("@drawable/"+it.background, null, context.packageName)
                                } else {
                                    -1
                                }

                                val base64ImageString = DataHolder.sharedPreferences.getString(it.background, null)
                                if (base64ImageString != null) {
                                    val decodedByteArray = android.util.Base64.decode(base64ImageString, android.util.Base64.DEFAULT)
                                    val bitmap = BitmapFactory.decodeByteArray(decodedByteArray, 0, decodedByteArray.size)
                                    backgroundBitmap = BitmapPainter(bitmap.asImageBitmap())
                                }

                                item(key) {
                                    val price: String? = if (it.price != null && it.price != 0.0) {
                                        formatPrice(it.price, context)
                                    } else {
                                        null
                                    }
                                    FoodChip(
                                        it.id,
                                        it.name,
                                        it.nameEn,
                                        it.description,
                                        it.descriptionEn,
                                        if (it.options != null) { (it.options.options as List<HashMap<*, *>>).size } else { 0 },
                                        price,
                                        it.isCategory,
                                        it.availableFrom,
                                        it.availableTo,
                                        backgroundResourceIdentifier,
                                        backgroundBitmap,
                                        it.daysAvailable,
                                        actualDay,
                                        useInternationalNames,
                                        contentModifier
                                    )
                                }
                            }
                        }

                        item(food.size) {
                            TermsOfUseChip(modifier = contentModifier, useInternationalNames)
                        }

                        if (DataHolder.guiConfig["main"]!!["enableImprintChip"] as Boolean) {
                            item(food.size + 1) {
                                ImprintChip(modifier = contentModifier, useInternationalNames)
                            }
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalWearFoundationApi::class)
    @Composable
    fun InternetError(context: Context) {
        val listState = rememberScalingLazyListState()
        Scaffold(positionIndicator = {
            PositionIndicator(scalingLazyListState = listState)
        }) {
            val focusRequester = rememberActiveFocusRequester()
            val coroutineScope = rememberCoroutineScope()
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
                item {
                    Column {
                        Text(
                            getString(R.string.internetErrorHeading),
                            fontSize = 14.sp
                        )
                        Text(
                            getString(R.string.internetErrorDescription),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalWearFoundationApi::class)
    @Composable
    fun LoginError(context: Context) {
        val listState = rememberScalingLazyListState()
        Scaffold(positionIndicator = {
            PositionIndicator(scalingLazyListState = listState)
        }) {
            val focusRequester = rememberActiveFocusRequester()
            val coroutineScope = rememberCoroutineScope()
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
                item {
                    Column {
                        Text(
                            getString(R.string.loginErrorHeading),
                            fontSize = 14.sp
                        )
                        Text(
                            getString(R.string.loginErrorDescription),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalWearFoundationApi::class)
    @Composable
    fun RestaurantClosedError(context: Context) {
        val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
        Scaffold(positionIndicator = {
            PositionIndicator(scalingLazyListState = listState)
        }) {
            val focusRequester = rememberActiveFocusRequester()
            val coroutineScope = rememberCoroutineScope()
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
                        getString(R.string.restaurantClosedHeading),
                        fontSize = 17.sp
                    )
                }
                item(1) {
                    Column {
                        val daysOfWeek = getString(R.string.restaurantClosedDaysOfWeek).split(",")

                        val configuration = Configuration(context.resources.configuration)
                        configuration.setLocale(Locale("en"))
                        val daysOfWeekInternational = context.createConfigurationContext(configuration).resources.getString(R.string.restaurantClosedDaysOfWeek).split(",")

                        val closed = getString(R.string.restaurantClosedTextForClosed)

                        Text(text = getString(R.string.restaurantClosedOpeningTimes), fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))

                        var openingTimesText = ""
                        daysOfWeek.forEachIndexed { index, day ->
                            openingTimesText += "$day: "
                            val dayTimes = DataHolder.restaurantConfig["openingTimes"]!![daysOfWeekInternational[index].lowercase()]!! as List<HashMap<String, String>>
                            if (dayTimes.isEmpty()) {
                                openingTimesText += closed+"\n"
                            } else {
                                dayTimes.forEach {
                                    openingTimesText += it["from"] + " - " + it["to"]
                                    if (it != (DataHolder.restaurantConfig["openingTimes"]!![daysOfWeekInternational[index].lowercase()]!! as List<HashMap<String, String>>).last()) {
                                        openingTimesText += ", "
                                    } else {
                                        openingTimesText += "\n"
                                    }
                                }
                            }
                        }

                        openingTimesText.split("\n").forEach {
                            Text(it, fontSize = 10.sp, modifier = Modifier.padding(bottom = 4.dp))
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalWearFoundationApi::class)
    @Composable
    fun UpdateError(context: Context) {
        val listState = rememberScalingLazyListState()
        Scaffold(positionIndicator = {
            PositionIndicator(scalingLazyListState = listState)
        }) {
            val focusRequester = rememberActiveFocusRequester()
            val coroutineScope = rememberCoroutineScope()
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
                item {
                    Column {
                        Text(
                            getString(R.string.updateErrorHeading),
                            fontSize = 14.sp
                        )
                        Text(
                            getString(R.string.updateErrorDescription),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalWearFoundationApi::class)
    @Composable
    fun InactiveScreen(context: Context) {
        val listState = rememberScalingLazyListState()
        Scaffold(positionIndicator = {
            PositionIndicator(scalingLazyListState = listState)
        }) {
            val focusRequester = rememberActiveFocusRequester()
            val coroutineScope = rememberCoroutineScope()
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
                item {
                    Column {
                        Text(
                            getString(R.string.inactiveScreenHeading),
                            fontSize = 14.sp
                        )
                        Text(
                            getString(R.string.inactiveScreenDescription),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}