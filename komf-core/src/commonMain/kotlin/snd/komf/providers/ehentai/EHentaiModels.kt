package snd.komf.providers.ehentai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class EHentaiGalleryId(
    val gid: Long,
    val token: String,
) {
    override fun toString() = "$gid/$token"
}

data class EHentaiSearchResult(
    val id: EHentaiGalleryId,
    val title: String,
    val thumbnailUrl: String?,
    val url: String,
)

@Serializable
data class EHentaiGDataResponse(
    @SerialName("gmetadata")
    val metadata: List<EHentaiGallery> = emptyList(),
)

@Serializable
data class EHentaiGallery(
    val gid: Long,
    val token: String,
    val title: String,
    @SerialName("title_jpn")
    val titleJpn: String? = null,
    val category: String? = null,
    val thumb: String? = null,
    val uploader: String? = null,
    val posted: String? = null,
    val rating: String? = null,
    val tags: List<String> = emptyList(),
)

fun EHentaiGallery.id() = EHentaiGalleryId(gid, token)

fun EHentaiGallery.galleryUrl(baseWebUrl: String = EHentaiParser.PUBLIC_BASE_URL): String =
    "$baseWebUrl/g/$gid/$token/"

fun EHentaiGallery.sourceTag(): String = "source:e-hentai.org/g/$gid/$token"
