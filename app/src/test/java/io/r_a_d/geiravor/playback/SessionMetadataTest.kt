package io.r_a_d.geiravor.playback

import androidx.media3.common.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.geiravor_core.Dj
import uniffi.geiravor_core.ListEntry
import uniffi.geiravor_core.Status

class SessionMetadataTest {
    @Test
    fun usesSplitNpAndDjNameNotIcy() {
        val card = requireNotNull(SessionMetadata.card(sampleStatus()))
        assertEquals("Gats", card.title)
        assertEquals("Hirasawa Susumu", card.artist)
        assertEquals("Hanyuu-sama", card.albumArtist)
        assertEquals(
            "https://r-a-d.io/api/dj-image/18-e0177611a37081b5.png",
            card.artworkUrl,
        )
    }

    @Test
    fun blankArtistIsDjOnlyOnShadeAndAuto() {
        for (artist in listOf("", " ")) {
            val card = requireNotNull(
                SessionMetadata.card(
                    sampleStatus().copy(np = "just a title", artist = artist, title = "just a title"),
                ),
            )
            assertEquals("just a title", card.title)
            assertEquals("", card.artist)
            assertEquals("Hanyuu-sama", SessionMetadata.shadeArtist(card))
            val meta = SessionMetadata.sessionMetadata(card)
            assertEquals("Hanyuu-sama", meta.artist.toString())
            assertEquals("Hanyuu-sama", meta.subtitle.toString())
            assertNull(meta.description)
        }
    }

    @Test
    fun missingStatusHasNoMetadata() {
        assertNull(SessionMetadata.card(null))
    }

    @Test
    fun sessionMetadataIsTitleArtistAndDj() {
        val card = requireNotNull(SessionMetadata.card(sampleStatus()))
        val meta = SessionMetadata.sessionMetadata(card)
        assertEquals("Gats", meta.title.toString())
        assertEquals("Gats", meta.displayTitle.toString())
        assertEquals("Hirasawa Susumu | Hanyuu-sama", meta.artist.toString())
        assertEquals("Hirasawa Susumu", meta.subtitle.toString())
        assertEquals("Hanyuu-sama", meta.description.toString())
        assertEquals("Hanyuu-sama", meta.albumArtist.toString())
        assertFalse(meta.subtitle.toString().contains(" | "))
        assertFalse(meta.description.toString().startsWith("DJ:"))
        assertFalse(meta.subtitle.toString().contains("Next:"))
    }

    @Test
    fun shadeArtistKeepsPipeAndDjWhenEllipsizing() {
        val card = requireNotNull(SessionMetadata.card(sampleStatus()))
        assertEquals("Hirasawa Susumu | Hanyuu-sama", SessionMetadata.shadeArtist(card))
        val tight = SessionMetadata.shadeArtist(card, maxChars = 22)
        assertTrue(tight.endsWith(" | Hanyuu-sama"))
        assertTrue(tight.contains("…"))
        assertFalse(tight.contains("Hirasawa Susumu |"))
        val noRoom = SessionMetadata.ellipsizeKeepingSuffix("Hirasawa Susumu", " | Hanyuu-sama", 8)
        assertEquals("… | Hanyuu-sama", noRoom)
    }

    @Test
    fun shadeMaxCharsFollowsDisplayWidth() {
        val wide = SessionMetadata.shadeMaxChars(widthPx = 1080, density = 3f, fontScale = 1f)
        val narrow = SessionMetadata.shadeMaxChars(widthPx = 480, density = 1.5f, fontScale = 1f)
        assertTrue(wide > narrow)
        assertTrue(narrow >= 8)
    }

    @Test
    fun djArtworkDecodeDoesNotDensityScale() {
        assertFalse(DjArtwork.decodeOptions().inScaled)
        assertEquals(android.graphics.Bitmap.Config.ARGB_8888, DjArtwork.decodeOptions().inPreferredConfig)
    }

    @Test
    fun publishedMetadataPrefersApiCardOverExoCombined() {
        val api = MediaMetadata.Builder()
            .setTitle("Gats")
            .setArtist("Hirasawa Susumu")
            .setAlbumArtist("Hanyuu-sama")
            .build()
        val exo = MediaMetadata.Builder().setTitle("r/a/dio").setArtist("r/a/dio").build()
        val published = SessionMetadata.published(api, exo)
        assertEquals("Gats", published.title.toString())
        assertEquals("Hirasawa Susumu", published.artist.toString())
        assertEquals("Hanyuu-sama", published.albumArtist.toString())
        assertEquals("r/a/dio", SessionMetadata.published(null, exo).title.toString())
    }

    @Test
    fun songWindowIsApiSecondsTimesOneThousand() {
        val progress = uniffi.geiravor_core.SongProgress(elapsedSecs = 65, durationSecs = 180)
        assertEquals(65_000L, SessionMetadata.positionMs(progress))
        assertEquals(180_000L, SessionMetadata.durationMs(progress))
        assertEquals(0L, SessionMetadata.positionMs(null))
        assertEquals(
            androidx.media3.common.C.TIME_UNSET,
            SessionMetadata.durationMs(uniffi.geiravor_core.SongProgress(elapsedSecs = 10, durationSecs = null)),
        )
    }
}

internal fun sampleStatus(
    isAfkStream: Boolean = true,
    lastPlayed: List<ListEntry> = emptyList(),
    queue: List<ListEntry> = emptyList(),
) = Status(
    np = "Hirasawa Susumu - Gats",
    artist = "Hirasawa Susumu",
    title = "Gats",
    listeners = 123,
    isAfkStream = isAfkStream,
    requesting = true,
    current = 1_787_672_409,
    startTime = 1_787_672_000,
    endTime = 1_787_672_500,
    trackId = 42,
    thread = null,
    dj = Dj(id = 18, name = "Hanyuu-sama", image = "18-e0177611a37081b5.png"),
    queue = queue,
    lastPlayed = lastPlayed,
    tags = listOf("berserk", "guts"),
)
