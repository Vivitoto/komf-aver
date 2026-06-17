package snd.komf.app.api.mappers

import snd.komf.api.PatchValue
import snd.komf.api.config.EHentaiConfigUpdateRequest
import snd.komf.api.config.KomfConfigUpdateRequest
import snd.komf.api.config.MetadataProvidersConfigUpdateRequest
import snd.komf.api.config.ProvidersConfigUpdateRequest
import snd.komf.app.config.AppConfig
import snd.komf.providers.EHentaiConfig
import snd.komf.providers.MetadataProvidersConfig
import snd.komf.providers.ProvidersConfig
import snd.komf.providers.mangabaka.db.MangaBakaDbMetadata
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class AppConfigUpdateMapperTest {

    @Test
    fun preservesExistingEHentaiCookieWhenPatchValueIsMaskedPlaceholder() {
        val config = AppConfig(
            metadataProviders = MetadataProvidersConfig(
                defaultProviders = ProvidersConfig(
                    ehentai = EHentaiConfig(
                        cookies = mapOf(
                            "ipb_member_id" to "existing-member",
                            "ipb_pass_hash" to "existing-hash",
                        )
                    )
                )
            )
        )
        val patch = KomfConfigUpdateRequest(
            metadataProviders = PatchValue.Some(
                MetadataProvidersConfigUpdateRequest(
                    defaultProviders = PatchValue.Some(
                        ProvidersConfigUpdateRequest(
                            ehentai = PatchValue.Some(
                                EHentaiConfigUpdateRequest(
                                    cookies = PatchValue.Some(
                                        mapOf(
                                            "ipb_member_id" to "********",
                                            "ipb_pass_hash" to "new-hash",
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        val updated = AppConfigUpdateMapper().patch(config, patch)
        val cookies = updated.metadataProviders.defaultProviders.ehentai.cookies

        assertEquals("existing-member", cookies["ipb_member_id"])
        assertEquals("new-hash", cookies["ipb_pass_hash"])
    }

    @Test
    fun masksEHentaiCookieHeaderInConfigDto() {
        val dto = AppConfigMapper().toDto(
            config = AppConfig(
                metadataProviders = MetadataProvidersConfig(
                    defaultProviders = ProvidersConfig(
                        ehentai = EHentaiConfig(
                            cookieHeader = "ipb_member_id=member; ipb_pass_hash=hash; igneous=igneous",
                            cookies = mapOf("ipb_member_id" to "member"),
                        )
                    )
                )
            ),
            mangaBakaDbMetadata = mangaBakaDbMetadata(),
        )

        val ehentai = dto.metadataProviders.defaultProviders.ehentai

        assertEquals("********", ehentai.cookieHeader)
        assertEquals("********", ehentai.cookies["ipb_member_id"])
    }

    @Test
    fun preservesExistingEHentaiCookieHeaderWhenPatchValueIsMaskedPlaceholder() {
        val existingCookieHeader = "ipb_member_id=existing; ipb_pass_hash=existing-hash; igneous=existing-igneous"
        val config = AppConfig(
            metadataProviders = MetadataProvidersConfig(
                defaultProviders = ProvidersConfig(
                    ehentai = EHentaiConfig(cookieHeader = existingCookieHeader)
                )
            )
        )
        val patch = KomfConfigUpdateRequest(
            metadataProviders = PatchValue.Some(
                MetadataProvidersConfigUpdateRequest(
                    defaultProviders = PatchValue.Some(
                        ProvidersConfigUpdateRequest(
                            ehentai = PatchValue.Some(
                                EHentaiConfigUpdateRequest(
                                    cookieHeader = PatchValue.Some("********")
                                )
                            )
                        )
                    )
                )
            )
        )

        val updated = AppConfigUpdateMapper().patch(config, patch)

        assertEquals(existingCookieHeader, updated.metadataProviders.defaultProviders.ehentai.cookieHeader)
    }

    private fun mangaBakaDbMetadata(): MangaBakaDbMetadata {
        val directory = Files.createTempDirectory("komf-mangabaka-db-test")
        val timestampFile = directory.resolve("timestamp")
        val checksumFile = directory.resolve("checksum")
        Files.writeString(timestampFile, "2024-01-01T00:00:00Z")
        Files.writeString(checksumFile, "checksum")

        return MangaBakaDbMetadata(
            timestampFile = timestampFile,
            checksumFile = checksumFile,
        )
    }
}
