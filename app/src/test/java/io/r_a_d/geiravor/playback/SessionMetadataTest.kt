package io.r_a_d.geiravor.playback

import androidx.media3.common.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun emptyArtistStaysEmpty() {
        val card = requireNotNull(
            SessionMetadata.card(
                sampleStatus().copy(np = "just a title", artist = "", title = "just a title"),
            ),
        )
        assertEquals("just a title", card.title)
        assertEquals("", card.artist)
    }

    @Test
    fun missingStatusHasNoMetadata() {
        assertNull(SessionMetadata.card(null))
    }

    @Test
    fun cardSubtitleFollowsAutoPrevNext() {
        val afk = requireNotNull(
            SessionMetadata.card(
                sampleStatus(
                    lastPlayed = listOf(ListEntry("Hirasawa Susumu - Gats", "Hirasawa Susumu", "Gats", 1, false)),
                    queue = listOf(ListEntry("Aimer - ninelie", "Aimer", "ninelie", 2, false)),
                ),
            ),
        )
        assertEquals("Prev: Hirasawa Susumu - Gats · Next: Aimer - ninelie", afk.subtitle)
        val liveDj = requireNotNull(
            SessionMetadata.card(
                sampleStatus(
                    isAfkStream = false,
                    lastPlayed = listOf(ListEntry("Previous - Song", "", "Previous - Song", 1, false)),
                    queue = listOf(ListEntry("Hidden - Track", "", "Hidden - Track", 2, false)),
                ),
            ),
        )
        assertEquals("Prev: Previous - Song · Next: ???", liveDj.subtitle)
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
