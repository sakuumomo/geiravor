package io.r_a_d.geiravor.playback

import androidx.media3.common.Metadata
import androidx.media3.extractor.metadata.icy.IcyInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IcyMetadataTest {
    @Test
    fun readsIcyTitleWithoutTreatingItAsNowPlaying() {
        val icy = IcyInfo("StreamTitle='Other - Song';".toByteArray(), "Other - Song", null)
        assertEquals("Other - Song", IcyMetadata.titleFrom(Metadata(icy)))
    }

    @Test
    fun ignoresMetadataWithoutIcyInfo() {
        assertNull(IcyMetadata.titleFrom(Metadata()))
    }
}
