package business.digitalfood.watch.model

class OptionContent (
    val id: Long,
    val name: String,
    val nameEn: String?,
    val price: Double,
    val hasImage: Boolean?,
    val background: String?,
    val availableFrom: String?,
    val availableTo: String?
)