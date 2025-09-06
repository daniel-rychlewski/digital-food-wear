package business.digitalfood.watch.activity

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import business.digitalfood.watch.DataHolder
import business.digitalfood.watch.DataHolder.calculateNumberOfProducts
import business.digitalfood.watch.DataHolder.formatPrice
import business.digitalfood.watch.DataHolder.options
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale

@Composable
fun FoodChip(
    id: Int,
    name: String?,
    nameEn: String?,
    description: String?,
    descriptionEn: String?,
    numberOfOptions: Int,
    price: String?,
    isCategory: Boolean?,
    availableFrom: String?,
    availableTo: String?,
    @DrawableRes backgroundIcon: Int,
    bitmapPainter: BitmapPainter?,
    daysAvailable: List<String>?,
    actualDay: String,
    useInternationalNames: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val satisfiesTimeCriteria = satisfiesTimeCriteria(availableFrom, availableTo, true)
    val isWrongDay = isWrongDay(daysAvailable, actualDay)

    Chip(
        modifier = modifier,
        onClick = {
            if ((isCategory != true || (description != null && descriptionEn != null)) && !isUnavailable(availableFrom, availableTo) && !isWrongDay && satisfiesTimeCriteria) {
                val intent = Intent(context, FoodActivity::class.java)
                intent.putExtra("id", "" + id)
                intent.putExtra("name", name)
                intent.putExtra("nameEn", nameEn)
                intent.putExtra("description", description)
                intent.putExtra("descriptionEn", descriptionEn)
                intent.putExtra("isCategory", "" + isCategory)
                intent.putExtra("numberOfOptions", "" + numberOfOptions)
                intent.putExtra("useInternationalNames", "" + useInternationalNames)
                context.startActivity(intent)
            }
        },
        label = {
            Text(
                text = if (useInternationalNames) { nameEn ?: "" } else { name ?: "" },
                maxLines = (DataHolder.guiConfig["chip"]!!["foodLabelMaxLines"] as Long).toInt(),
                overflow = TextOverflow.Ellipsis
            )
        },
        secondaryLabel = {
            if (!isUnavailable(availableFrom, availableTo) && !isWrongDay) {
                if (price != null) {
                    Text(
                        text = price + getAvailabilityRestrictions(availableFrom, availableTo, useInternationalNames, "food"),
                        maxLines = (DataHolder.guiConfig["chip"]!!["foodSecondaryLabelMaxLines"] as Long).toInt(),
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else if (isWrongDay) {
                Text(
                    text = getDaySpecificText(daysAvailable, actualDay, useInternationalNames)
                )
            } else {
                Text(
                    text = DataHolder.guiConfig["main"]!![DataHolder.internationalizeLabel("notAvailableText", useInternationalNames)] as String
                )
            }
        },
        colors = if (backgroundIcon != -1) {
            if (satisfiesTimeCriteria && !isUnavailable(availableFrom, availableTo) && !isWrongDay) {
                ChipDefaults.imageBackgroundChipColors(
                    backgroundImagePainter = bitmapPainter ?: painterResource(id = backgroundIcon),
                )
            } else {
                ChipDefaults.imageBackgroundChipColors(
                    backgroundImagePainter = bitmapPainter ?: painterResource(id = backgroundIcon),
                    backgroundImageScrimBrush =  Brush.linearGradient(
                        colors = listOf(
                            Color(android.graphics.Color.parseColor(DataHolder.guiConfig["chip"]!!["foodChipGrayedOutOverlayColor"] as String)).copy(alpha = (DataHolder.guiConfig["chip"]!!["foodChipGrayedOutTransparency"] as Double).toFloat()),
                            Color(android.graphics.Color.parseColor(DataHolder.guiConfig["chip"]!!["foodChipGrayedOutOverlayColor"] as String)).copy(alpha = (DataHolder.guiConfig["chip"]!!["foodChipGrayedOutTransparency"] as Double).toFloat())
                        )
                    )
                )
            }
        } else {
            ChipDefaults.chipColors(
                backgroundColor = if (satisfiesTimeCriteria && !isWrongDay) {
                    Color(android.graphics.Color.parseColor(DataHolder.guiConfig["chip"]!!["foodChipBackgroundColor"] as String))
                } else {
                    Color(android.graphics.Color.parseColor(DataHolder.guiConfig["chip"]!!["foodChipGrayedOutBackgroundColor"] as String))
                },
                contentColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["chip"]!!["foodChipContentColor"] as String))
            )
        }
    )
}

fun getAvailabilityRestrictions(availableFrom: String?, availableTo: String?, useInternationalNames: Boolean, type: String): String {
    if (availableFrom != null && availableFrom != "") {
        if (availableTo != null && availableTo != "") {
            return (DataHolder.guiConfig["chip"]!![DataHolder.internationalizeLabel(type + "SecondaryLabelWithAvailabilityFromAndTo", useInternationalNames)] as String).format(availableFrom, availableTo)
        } else {
            return (DataHolder.guiConfig["chip"]!![DataHolder.internationalizeLabel(type + "SecondaryLabelWithAvailabilityFrom", useInternationalNames)] as String).format(availableFrom)
        }
    } else {
        if (availableTo != null && availableTo != "") {
            return (DataHolder.guiConfig["chip"]!![DataHolder.internationalizeLabel(type + "SecondaryLabelWithAvailabilityTo", useInternationalNames)] as String).format(availableTo)
        } else {
            return ""
        }
    }
}

fun isUnavailable(availableFrom: String?, availableTo: String?): Boolean {
    val notAvailable = DataHolder.guiConfig["main"]!!["notAvailable"] as String
    if (availableFrom == notAvailable || availableTo == notAvailable) {
        return true
    } else {
        return false
    }
}

fun isWrongDay(daysAvailable: List<String>?, actualDay: String?): Boolean {
    if (daysAvailable != null && !daysAvailable.contains(actualDay)) {
        return true
    } else {
        return false
    }
}

fun getDaySpecificText(
    daysAvailable: List<String>?,
    actualDay: String?,
    useInternationalNames: Boolean
): String {
    if (daysAvailable != null && !daysAvailable.contains(actualDay)) {
        val label = DataHolder.guiConfig["chip"]!![DataHolder.internationalizeLabel("dayAvailability", useInternationalNames)] as String

        return if (daysAvailable.size == 1) {
            label.format(DataHolder.toShortDayNames(daysAvailable, useInternationalNames, useShort = false).first())
        } else if (daysAvailable.size > 1) {
            val formattedDays = DataHolder.toShortDayNames(daysAvailable, useInternationalNames, useShort = true).joinToString(", ")
            label.format(formattedDays)
        } else {
            DataHolder.guiConfig["main"]!![DataHolder.internationalizeLabel("notAvailableText", useInternationalNames)] as String // fallback if array empty
        }
    }
    return "" // no day-specific restriction
}

fun satisfiesTimeCriteria(availableFrom: String?, availableTo: String?, fallbackForInvalidTime: Boolean): Boolean {
    if (availableFrom == null && availableTo == null) return fallbackForInvalidTime

    var isFromTimeAvailable = true
    var isToTimeAvailable = true

    val fromSplit: List<String>
    var fromHours = 0
    var fromMinutes = 0

    val toSplit: List<String>
    var toHours = 0
    var toMinutes = 0

    if (availableFrom == null) {
        isFromTimeAvailable = false
    } else {
        fromSplit = availableFrom.split(":")
        if (fromSplit.size < 2) return fallbackForInvalidTime // ignore time criteria if any of the two times is invalid
        try {
            fromHours = fromSplit[0].toInt()
            fromMinutes = fromSplit[1].toInt()
            if (fromHours < 0 || fromHours > 23 || fromMinutes < 0 || fromMinutes > 59) return fallbackForInvalidTime;
        } catch (e: NumberFormatException) {
            return fallbackForInvalidTime
        }
    }

    if (availableTo == null) {
        isToTimeAvailable = false
    } else {
        toSplit = availableTo.split(":")
        if (toSplit.size < 2) return fallbackForInvalidTime // ignore time criteria if any of the two times is invalid
        try {
            toHours = toSplit[0].toInt()
            toMinutes = toSplit[1].toInt()
            if (toHours < 0 || toHours > 23 || toMinutes < 0 || toMinutes > 59) return fallbackForInvalidTime;
        } catch (e: NumberFormatException) {
            return fallbackForInvalidTime
        }
    }

    val actualTime = Calendar.getInstance()

    if (isFromTimeAvailable) {

        val fromTime = Calendar.getInstance()
        fromTime.set(Calendar.HOUR_OF_DAY, fromHours)
        fromTime.set(Calendar.MINUTE, fromMinutes)
        fromTime.set(Calendar.SECOND, 0)
        fromTime.set(Calendar.MILLISECOND, 0)

        if (isToTimeAvailable) {

            val toTime = Calendar.getInstance()
            toTime.set(Calendar.HOUR_OF_DAY, toHours)
            toTime.set(Calendar.MINUTE, toMinutes)
            toTime.set(Calendar.SECOND, 0)
            toTime.set(Calendar.MILLISECOND, 0)

            return actualTime.before(toTime) && (actualTime.after(fromTime) || actualTime.equals(fromTime))
        } else {
            return (actualTime.after(fromTime) || actualTime.equals(fromTime)) && fallbackForInvalidTime
        }

    } else {

        if (isToTimeAvailable) {

            val toTime = Calendar.getInstance()
            toTime.set(Calendar.HOUR_OF_DAY, toHours)
            toTime.set(Calendar.MINUTE, toMinutes)

            return actualTime.before(toTime) && fallbackForInvalidTime
        } else {
            return fallbackForInvalidTime
        }
    }
}

fun isRestaurantCurrentlyOpen(): Boolean {
    val calendar: Calendar = Calendar.getInstance()
    val dayOfWeek: Int = calendar.get(Calendar.DAY_OF_WEEK)
    val dayOfWeekString = DateFormatSymbols(Locale.US).weekdays[dayOfWeek].lowercase()

    val openingTime = DataHolder.restaurantConfig["openingTimes"]!![dayOfWeekString]!! as List<HashMap<String, String>>

    var isOpeningTimeSatisfied = false
    openingTime.forEach {
        isOpeningTimeSatisfied = isOpeningTimeSatisfied || satisfiesTimeCriteria(it["from"], it["to"], false)
    }

    return isOpeningTimeSatisfied
}

@Composable
fun AddressChip(
    modifier: Modifier = Modifier,
    color: Color,
    useInternationalNames: Boolean
) {
    val context = LocalContext.current

    Chip(
        modifier = modifier,
        onClick = {
            val intent = Intent(context, ContactActivity::class.java)
            intent.putExtra("useInternationalNames", "" + useInternationalNames)
            context.startActivity(intent)
        },
        label = {
            Text(
                text = DataHolder.guiConfig["chip"]!![DataHolder.internationalizeLabel("contactData", useInternationalNames)] as String,
                maxLines = (DataHolder.guiConfig["chip"]!!["addressLabelMaxLines"] as Long).toInt(),
                overflow = TextOverflow.Ellipsis
            )
        },
        secondaryLabel = if (DataHolder.guiConfig["chip"]!!["isContactDataSecondaryLabelEnabled"] as Boolean) {
            {
                Text(
                    text = DataHolder.guiConfig["chip"]!![DataHolder.internationalizeLabel("enterContactData", useInternationalNames)] as String,
                    maxLines = (DataHolder.guiConfig["chip"]!!["addressSecondaryLabelMaxLines"] as Long).toInt(),
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else null,
        colors = ChipDefaults.chipColors(
            backgroundColor = color,
            contentColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["chip"]!!["addressContentColor"] as String))
        )
    )
}

@Composable
fun ModeChip(
    modifier: Modifier = Modifier,
    color: Color,
    useInternationalNames: Boolean
) {
    val context = LocalContext.current

    Chip(
        modifier = modifier,
        onClick = {
            val intent = Intent(context, ModeActivity::class.java)
            intent.putExtra("useInternationalNames", "" + useInternationalNames)
            context.startActivity(intent)
        },
        label = {
            Text(
                text = DataHolder.guiConfig["chip"]!![DataHolder.internationalizeLabel("deliveryModeData", useInternationalNames)] as String,
                maxLines = (DataHolder.guiConfig["chip"]!!["deliveryModeLabelMaxLines"] as Long).toInt(),
                overflow = TextOverflow.Ellipsis
            )
        },
        secondaryLabel = if (DataHolder.guiConfig["chip"]!!["isDeliveryModeSecondaryLabelEnabled"] as Boolean) {
            {
                Text(
                    text = DataHolder.guiConfig["chip"]!![DataHolder.internationalizeLabel("chooseDeliveryMode", useInternationalNames)] as String,
                    maxLines = (DataHolder.guiConfig["chip"]!!["deliveryModeSecondaryLabelMaxLines"] as Long).toInt(),
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else null,
        colors = ChipDefaults.chipColors(
            backgroundColor = color,
            contentColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["chip"]!!["deliveryModeContentColor"] as String))
        )
    )
}

@Composable
fun CartChip(
    totalPrice: Double,
    modifier: Modifier = Modifier,
    useInternationalNames: Boolean
) {
    var error by remember { mutableStateOf(false) }
    val color = if (error) {
        Color(android.graphics.Color.parseColor(DataHolder.guiConfig["chip"]!!["cartMinimumOrderValueErrorColor"] as String))
    } else {
        Color(android.graphics.Color.parseColor(DataHolder.guiConfig["chip"]!!["cartMinimumOrderValueColor"] as String))
    }

    val context = LocalContext.current
    val numberOfElements = calculateNumberOfProducts(DataHolder.cart, options)
    val totalPriceFormatted = formatPrice(totalPrice, context)
    val minimumOrderPrice = getMinimumOrderValuePrice()
    if (!error) {
        DataHolder.minimumOrderValueText.postValue(getMinimumOrderValueText(context, useInternationalNames))
    }

    Chip(
        modifier = modifier,
        colors = ChipDefaults.chipColors(
            backgroundColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["chip"]!!["cartChipBackgroundColor"] as String)),
            contentColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["chip"]!!["cartChipContentColor"] as String))
        ),
        onClick = {
            if (totalPrice >= minimumOrderPrice && (DataHolder.deliveryMode == "collection" || DataHolder.deliveryMode == "dine-in" || DataHolder.isZipCodeValid()) && DataHolder.cart.isNotEmpty()) {
                if (DataHolder.isModeSelected()) {
                    DataHolder.deliveryModeError.postValue(false)
                    if (DataHolder.isContactDataValid()) {
                        DataHolder.contactDataError.postValue(false)
                        val intent = Intent(context, CartActivity::class.java)
                        intent.putExtra("useInternationalNames", "" + useInternationalNames)
                        context.startActivity(intent)
                    } else {
                        DataHolder.contactDataError.postValue(true)
                    }
                } else {
                    DataHolder.deliveryModeError.postValue(true)
                }
            } else {
                error = true
            }
        },
        label = {
            Text(
                text = DataHolder.guiConfig["chip"]!![DataHolder.internationalizeLabel("cart", useInternationalNames)] as String,
                maxLines = (DataHolder.guiConfig["chip"]!!["cartLabelMaxLines"] as Long).toInt(),
                overflow = TextOverflow.Ellipsis
            )
        },
        secondaryLabel = {
            if (numberOfElements != 1) {
                DataHolder.priceLine.postValue("$numberOfElements " + (DataHolder.guiConfig["chip"]!![DataHolder.internationalizeLabel("elements", useInternationalNames)]) + " - $totalPriceFormatted")
            } else {
                DataHolder.priceLine.postValue("$numberOfElements " + (DataHolder.guiConfig["chip"]!![DataHolder.internationalizeLabel("element", useInternationalNames)]) + " - $totalPriceFormatted")
            }

            Column {
                Text(
                    text = DataHolder.priceLine.observeAsState().value.toString(),
                    maxLines = (DataHolder.guiConfig["chip"]!!["cartSecondaryLabelFirstMaxLines"] as Long).toInt(),
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = getMinimumOrderValueText(context, useInternationalNames),
                    maxLines = (DataHolder.guiConfig["chip"]!!["cartSecondaryLabelSecondMaxLines"] as Long).toInt(),
                    overflow = TextOverflow.Ellipsis,
                    color = if (totalPrice >= minimumOrderPrice && DataHolder.cart.isNotEmpty()) {
                        Color(android.graphics.Color.parseColor(DataHolder.guiConfig["chip"]!!["cartMinimumOrderValueColor"] as String))
                    } else {
                        color
                    }
                )
            }
        }
    )
}

/**
 * Example: minimumOrderMap = {8048 -> 12, 8049 -> null}
 * means that PLZ 8048 has 12 EUR minimum order value, PLZ 8049 is not allowed to order and any other PLZ is not allowed to order
 * thus - equivalent: minimumOrderMap = {8048 -> 12}
 */
fun getMinimumOrderValuePrice(): Double {
    val minimumOrderValueMap = DataHolder.restaurantConfig["minimumOrderMap"] as Map<*, *>
    if (minimumOrderValueMap.isNotEmpty()) {
        if (DataHolder.deliveryMode == "collection") {
            val minimumOrderValueLookupForCollection = minimumOrderValueMap.getOrDefault("collection", -1);
            if (minimumOrderValueLookupForCollection != -1 && minimumOrderValueLookupForCollection != null) {
                return (minimumOrderValueLookupForCollection as Number).toDouble()
            } else {
                return Double.MAX_VALUE
            }
        } else if (DataHolder.deliveryMode == "dine-in") {
            val minimumOrderValueLookupForDineIn = minimumOrderValueMap.getOrDefault("dine-in", -1);
            if (minimumOrderValueLookupForDineIn != -1 && minimumOrderValueLookupForDineIn != null) {
                return (minimumOrderValueLookupForDineIn as Number).toDouble()
            } else {
                return Double.MAX_VALUE
            }
        } else if (DataHolder.deliveryMode == "delivery" && DataHolder.zip.value != null) {
            val minimumOrderValueLookupForZipCode = minimumOrderValueMap.getOrDefault(DataHolder.zip.value, -1)
            if (minimumOrderValueLookupForZipCode != -1 && minimumOrderValueLookupForZipCode != null) {
                return (minimumOrderValueLookupForZipCode as Number).toDouble()
            } else {
                return Double.MAX_VALUE
            }
        } else {
            return Double.MAX_VALUE
        }
    } else {
        return Double.MAX_VALUE
    }
}

fun getMinimumOrderValueText(context: android.content.Context, useInternationalNames: Boolean): String {
    val minimumOrderValueMap = DataHolder.restaurantConfig["minimumOrderMap"] as Map<*, *>
    if (minimumOrderValueMap.isNotEmpty()) {
        if (DataHolder.deliveryMode == "collection") {
            val minimumOrderValueLookupForCollection = minimumOrderValueMap.getOrDefault("collection", -1)
            if (minimumOrderValueLookupForCollection != -1 && minimumOrderValueLookupForCollection != null) {
                if (DataHolder.totalPrice.value != 0.0) {
                    return String.format(
                        DataHolder.guiConfig["chip"]!![DataHolder.internationalizeLabel("minimumOrderValue", useInternationalNames)] as String,
                        formatPrice((minimumOrderValueLookupForCollection as Number).toDouble(), context)
                    )
                } else {
                    return DataHolder.guiConfig["chip"]!![DataHolder.internationalizeLabel("addProducts", useInternationalNames)] as String
                }
            } else {
                return DataHolder.guiConfig["chip"]!![DataHolder.internationalizeLabel("noCollectionPossible", useInternationalNames)] as String
            }
        } else if (DataHolder.deliveryMode == "dine-in") {
            val minimumOrderValueLookupForDineIn = minimumOrderValueMap.getOrDefault("dine-in", -1);
            if (minimumOrderValueLookupForDineIn != -1 && minimumOrderValueLookupForDineIn != null) {
                if (DataHolder.totalPrice.value != 0.0) {
                    return String.format(
                        DataHolder.guiConfig["chip"]!![DataHolder.internationalizeLabel("minimumOrderValue", useInternationalNames)] as String,
                        formatPrice((minimumOrderValueLookupForDineIn as Number).toDouble(), context)
                    )
                } else {
                    return DataHolder.guiConfig["chip"]!![DataHolder.internationalizeLabel("addProducts", useInternationalNames)] as String
                }
            } else {
                return DataHolder.guiConfig["chip"]!![DataHolder.internationalizeLabel("noDineInPossible", useInternationalNames)] as String
            }
        } else { // DataHolder.deliveryMode == "delivery"
            if (DataHolder.zip.value != null) {
                val minimumOrderValueLookupForZipCode = minimumOrderValueMap.getOrDefault(DataHolder.zip.value, -1)
                if (minimumOrderValueLookupForZipCode != -1 && minimumOrderValueLookupForZipCode != null) {
                    return String.format(
                        DataHolder.guiConfig["chip"]!![DataHolder.internationalizeLabel("minimumOrderValue", useInternationalNames)] as String,
                        formatPrice((minimumOrderValueLookupForZipCode as Number).toDouble(), context)
                    )
                } else {
                    return DataHolder.guiConfig["chip"]!![DataHolder.internationalizeLabel("noDeliveryPossible", useInternationalNames)] as String
                }
            } else {
                return DataHolder.guiConfig["chip"]!![DataHolder.internationalizeLabel("enterContactData", useInternationalNames)] as String
            }
        }
    } else {
        if (DataHolder.deliveryMode == "collection") {
            return DataHolder.guiConfig["chip"]!![DataHolder.internationalizeLabel("noCollectionPossible", useInternationalNames)] as String
        } else if (DataHolder.deliveryMode == "dine-in") {
            return DataHolder.guiConfig["chip"]!![DataHolder.internationalizeLabel("noDineInPossible", useInternationalNames)] as String
        } else { // DataHolder.deliveryMode == "delivery"
            return DataHolder.guiConfig["chip"]!![DataHolder.internationalizeLabel("noDeliveryPossible", useInternationalNames)] as String
        }
    }
}

@Composable
fun OptionChip(
    parentId: Int,
    id: Long?,
    name: String,
    nameEn: String,
    price: String?,
    @DrawableRes backgroundIcon: Int,
    bitmapPainter: BitmapPainter?,
    useInternationalNames: Boolean,
    modifier: Modifier,
    isFirstOption: Boolean,
    availableFrom: String?,
    availableTo: String?
) {
    val context = LocalContext.current
    val satisfiesTimeCriteria = satisfiesTimeCriteria(availableFrom, availableTo, true)

    Chip(
        modifier = modifier,
        colors = if (backgroundIcon != -1) {
            if (satisfiesTimeCriteria && !isUnavailable(availableFrom, availableTo)) {
                ChipDefaults.imageBackgroundChipColors(
                    backgroundImagePainter = bitmapPainter ?: painterResource(id = backgroundIcon),
                )
            } else {
                ChipDefaults.imageBackgroundChipColors(
                    backgroundImagePainter = bitmapPainter ?: painterResource(id = backgroundIcon),
                    backgroundImageScrimBrush =  Brush.linearGradient(
                        colors = listOf(
                            Color(android.graphics.Color.parseColor(DataHolder.guiConfig["chip"]!!["optionChipGrayedOutOverlayColor"] as String)).copy(alpha = (DataHolder.guiConfig["chip"]!!["optionChipGrayedOutTransparency"] as Double).toFloat()),
                            Color(android.graphics.Color.parseColor(DataHolder.guiConfig["chip"]!!["optionChipGrayedOutOverlayColor"] as String)).copy(alpha = (DataHolder.guiConfig["chip"]!!["optionChipGrayedOutTransparency"] as Double).toFloat())
                        )
                    )
                )
            }
        } else {
            ChipDefaults.chipColors(
                backgroundColor = if (satisfiesTimeCriteria && !isUnavailable(availableFrom, availableTo)) {
                    Color(android.graphics.Color.parseColor(DataHolder.guiConfig["chip"]!!["optionChipBackgroundColor"] as String))
                } else {
                    Color(android.graphics.Color.parseColor(DataHolder.guiConfig["chip"]!!["optionChipGrayedOutBackgroundColor"] as String))
                },
                contentColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["chip"]!!["optionChipContentColor"] as String))
            )
        },
        onClick = {
            if (id != null && satisfiesTimeCriteria && !isUnavailable(availableFrom, availableTo)) {
                DataHolder.cart[id.toInt()] = (DataHolder.cart[id.toInt()] ?: 0) + 1
                DataHolder.totalPrice.postValue(DataHolder.calculateCartPrice(DataHolder.cart))

                val mapping = DataHolder.foodToOptionsMapping[parentId]
                if (mapping == null || mapping.isEmpty()) {
                    val newMapping = ArrayList<Int>()
                    newMapping.add(id.toInt())
                    DataHolder.foodToOptionsMapping[parentId]!!.add(newMapping)
                } else {
                    if (isFirstOption) {
                        val newMapping = ArrayList<Int>()
                        newMapping.add(id.toInt())
                        mapping.add(newMapping)
                    } else {
                        mapping[mapping.size - 1].add(id.toInt())
                    }
                    DataHolder.foodToOptionsMapping[parentId] = mapping
                }

                FoodActivity.optionSelectionSuccessful = true
                (context as ComponentActivity).finish()
            }
        },
        label = {
            Text(
                text = if (useInternationalNames) { nameEn } else { name },
                maxLines = (DataHolder.guiConfig["chip"]!!["optionLabelMaxLines"] as Long).toInt(),
                overflow = TextOverflow.Ellipsis
            )
        },
        secondaryLabel = {
            if (!isUnavailable(availableFrom, availableTo)) {
                if (price != null) {
                    Text(
                        text = price + getAvailabilityRestrictions(availableFrom, availableTo, useInternationalNames, "option"),
                        maxLines = (DataHolder.guiConfig["chip"]!!["optionSecondaryLabelMaxLines"] as Long).toInt(),
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Text(
                    text = DataHolder.guiConfig["main"]!![DataHolder.internationalizeLabel("notAvailableText", useInternationalNames)] as String
                )
            }
        }
    )
}

@Composable
fun OptionMultiChip(
    parentId: Int,
    id: Long?,
    name: String,
    nameEn: String,
    price: String?,
    useInternationalNames: Boolean,
    modifier: Modifier,
    availableFrom: String?,
    availableTo: String?
) {
    val checkedState = remember { mutableStateOf(false) }
    val satisfiesTimeCriteria = satisfiesTimeCriteria(availableFrom, availableTo, true)
    val isUnavailable = isUnavailable(availableFrom, availableTo)

    ToggleChip(
        modifier = modifier,
        checked = false,
        contentPadding = PaddingValues(),
        onCheckedChange = {},
        label = {
            Text(
                text = if (useInternationalNames) { nameEn } else { name },
                maxLines = (DataHolder.guiConfig["chip"]!!["optionMultiLabelMaxLines"] as Long).toInt(),
                overflow = TextOverflow.Ellipsis
            )
        },
        secondaryLabel = {
            if (!isUnavailable) {
                if (price != null) {
                    Text(
                        text = price + getAvailabilityRestrictions(availableFrom, availableTo, useInternationalNames, "option"),
                        maxLines = (DataHolder.guiConfig["chip"]!!["optionMultiSecondaryLabelMaxLines"] as Long).toInt(),
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Text(
                    text = DataHolder.guiConfig["main"]!![DataHolder.internationalizeLabel("notAvailableText", useInternationalNames)] as String
                )
            }
        },
        colors = ToggleChipDefaults.toggleChipColors(
            checkedStartBackgroundColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["chip"]!!["optionMultiToggleCheckedStartBackgroundColor"] as String)),
            checkedEndBackgroundColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["chip"]!!["optionMultiToggleCheckedEndBackgroundColor"] as String)),
            checkedContentColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["chip"]!!["optionMultiToggleCheckedContentColor"] as String)),
            checkedSecondaryContentColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["chip"]!!["optionMultiToggleCheckedSecondaryContentColor"] as String)),
            checkedToggleControlColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["chip"]!!["optionMultiToggleCheckedToggleControlColor"] as String)),
            uncheckedStartBackgroundColor = if (isUnavailable || !satisfiesTimeCriteria) { Color(android.graphics.Color.parseColor(DataHolder.guiConfig["chip"]!!["optionChipGrayedOutBackgroundColor"] as String)) } else { Color(android.graphics.Color.parseColor(DataHolder.guiConfig["chip"]!!["optionMultiToggleUncheckedStartBackgroundColor"] as String)) },
            uncheckedEndBackgroundColor = if (isUnavailable || !satisfiesTimeCriteria) { Color(android.graphics.Color.parseColor(DataHolder.guiConfig["chip"]!!["optionChipGrayedOutBackgroundColor"] as String)) } else { Color(android.graphics.Color.parseColor(DataHolder.guiConfig["chip"]!!["optionMultiToggleUncheckedEndBackgroundColor"] as String)) },
            uncheckedContentColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["chip"]!!["optionMultiToggleUncheckedContentColor"] as String)),
            uncheckedSecondaryContentColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["chip"]!!["optionMultiToggleUncheckedSecondaryContentColor"] as String)),
            uncheckedToggleControlColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["chip"]!!["optionMultiToggleUncheckedToggleControlColor"] as String)),
        ),
        toggleControl = { ToggleChipDefaults.checkboxIcon(checkedState.value) },
        appIcon = {
            Checkbox(
                checked = checkedState.value,

                colors = CheckboxDefaults.colors(
                    checkedColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["main"]!!["goUpColor"] as String)),
                    checkmarkColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["main"]!!["goUpTintColor"] as String))
                ),

                onCheckedChange = {
                    if (satisfiesTimeCriteria && !isUnavailable(availableFrom, availableTo)) {
                        checkedState.value = it

                        if (it) {
                            if (id != null) {
                                DataHolder.cart[id.toInt()] = (DataHolder.cart[id.toInt()] ?: 0) + 1
                                DataHolder.totalPrice.postValue(DataHolder.calculateCartPrice(DataHolder.cart))

                                val mapping = DataHolder.foodToOptionsMapping[parentId]
                                if (mapping == null) {
                                    DataHolder.foodToOptionsMapping[parentId] = ArrayList()
                                    val newMapping = ArrayList<Int>()
                                    newMapping.add(id.toInt())
                                    DataHolder.foodToOptionsMapping[parentId]!!.add(newMapping)
                                } else {
                                    mapping[mapping.size - 1].add(id.toInt())
                                    DataHolder.foodToOptionsMapping[parentId] = mapping
                                }
                            }
                        } else {
                            if (id != null) {
                                DataHolder.cart[id.toInt()] = if (DataHolder.cart[id.toInt()] != null) { DataHolder.cart[id.toInt()]!! - 1 } else { 0 }
                                DataHolder.totalPrice.postValue(DataHolder.calculateCartPrice(DataHolder.cart))

                                val mapping = DataHolder.foodToOptionsMapping[parentId]
                                if (mapping != null) {
                                    val mostRecentMapping = mapping[mapping.size - 1]
                                    mostRecentMapping.remove(id.toInt())
                                    DataHolder.foodToOptionsMapping[parentId]!![mapping.size - 1] = mostRecentMapping
                                }
                            }
                        }
                    }
                }
            )
        }
    )
}

@Composable
fun TermsOfUseChip(
    modifier: Modifier,
    useInternationalNames: Boolean
) {
    val context = LocalContext.current

    Chip(
        modifier = modifier,
        onClick = {
            val intent = Intent(context, TermsOfUseActivity::class.java)
            intent.putExtra("useInternationalNames", "" + useInternationalNames)
            context.startActivity(intent)
        },
        label = {
            Text(text = DataHolder.guiConfig["chip"]!![DataHolder.internationalizeLabel("termsOfUseLabelText", useInternationalNames)] as String)
        },
        colors = ChipDefaults.chipColors(
            backgroundColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["chip"]!!["termsOfUseChipBackgroundColor"] as String)),
            contentColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["chip"]!!["termsOfUseChipContentColor"] as String))
        )
    )
}

@Composable
fun ImprintChip(
    modifier: Modifier,
    useInternationalNames: Boolean
) {
    val context = LocalContext.current

    Chip(
        modifier = modifier,
        onClick = {
            val intent = Intent(context, ImprintActivity::class.java)
            intent.putExtra("useInternationalNames", "" + useInternationalNames)
            context.startActivity(intent)
        },
        label = {
            Text(text = DataHolder.guiConfig["chip"]!![DataHolder.internationalizeLabel("imprintLabelText", useInternationalNames)] as String)
        },
        colors = ChipDefaults.chipColors(
            backgroundColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["chip"]!!["imprintChipBackgroundColor"] as String)),
            contentColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["chip"]!!["imprintChipContentColor"] as String))
        )
    )
}

@Composable
fun FoodOverviewChip(
    modifier: Modifier,
    useInternationalNames: Boolean
) {
    Chip(
        modifier = modifier,
        onClick = {},
        label = {
            Text(
                DataHolder.guiConfig["chip"]!![DataHolder.internationalizeLabel("foodOverview", useInternationalNames)] as String
            )
        },
        colors = ChipDefaults.chipColors(
            backgroundColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["chip"]!!["foodOverviewChipBackgroundColor"] as String)),
            contentColor = Color(android.graphics.Color.parseColor(DataHolder.guiConfig["chip"]!!["foodOverviewChipContentColor"] as String))
        )
    )
}