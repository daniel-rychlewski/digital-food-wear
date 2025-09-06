package business.digitalfood.watch.model

class Food(
    val id: Int,
    val background: String?,
    val name: String?,
    val nameEn: String?,
    val description: String?,
    val descriptionEn: String?,
    val hasImage: Boolean?,
    val isCategory: Boolean?,
    val price: Double?,
    val options: Options?,
    val availableFrom: String?,
    val availableTo: String?,
    val daysAvailable: List<String>?,
    val isHidden: Boolean?
)