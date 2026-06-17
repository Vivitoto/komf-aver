package snd.komf.providers.nhentai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NHentaiSearchResponse(
    @SerialName("result")
    val results: List<NHentaiGallery> = emptyList(),
)

@Serializable
data class NHentaiGallery(
    val id: Long,
    @SerialName("media_id")
    val mediaId: String? = null,
    val title: NHentaiTitle = NHentaiTitle(),
    val images: NHentaiImages = NHentaiImages(),
    @SerialName("upload_date")
    val uploadDate: Long? = null,
    val tags: List<NHentaiTag> = emptyList(),
    @SerialName("num_pages")
    val numPages: Int? = null,
)

@Serializable
data class NHentaiTitle(
    val english: String? = null,
    val japanese: String? = null,
    val pretty: String? = null,
)

@Serializable
data class NHentaiImages(
    val cover: NHentaiImage? = null,
    val thumbnail: NHentaiImage? = null,
)

@Serializable
data class NHentaiImage(
    @SerialName("t")
    val type: String? = null,
    @SerialName("w")
    val width: Int? = null,
    @SerialName("h")
    val height: Int? = null,
)

@Serializable
data class NHentaiTag(
    val id: Long? = null,
    val type: String,
    val name: String,
    val url: String? = null,
    val count: Int? = null,
)

fun NHentaiGallery.galleryUrl(): String = "https://nhentai.net/g/$id/"

fun NHentaiGallery.coverUrl(): String? {
    val mediaId = mediaId ?: return null
    val extension = images.cover?.extension() ?: images.thumbnail?.extension() ?: return null
    return "https://t.nhentai.net/galleries/$mediaId/cover.$extension"
}

private fun NHentaiImage.extension(): String? {
    return when (type) {
        "j" -> "jpg"
        "p" -> "png"
        "g" -> "gif"
        "w" -> "webp"
        null -> null
        else -> type
    }
}
