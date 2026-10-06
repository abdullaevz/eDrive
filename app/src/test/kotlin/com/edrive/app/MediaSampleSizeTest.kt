package com.edrive.app

import com.edrive.app.util.Media
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class MediaSampleSizeTest {
    @Test fun smallImageIsNotShrunk() = assertEquals(1, Media.sampleSize(3000, 2000, 4096))

    @Test fun exactlyTargetIsNotShrunk() = assertEquals(1, Media.sampleSize(4096, 3000, 4096))

    /** 50 MP kamera (8160×6120): əvvəl 1 qaytarırdı və 199 MB-lıq bitmap çökməyə səbəb olurdu. */
    @Test fun fiftyMegapixelPhotoIsShrunk() {
        val s = Media.sampleSize(8160, 6120, 4096)
        assertEquals(2, s)
        assertTrue((8160 / s) * (6120 / s) * 4L < 100L * 1024 * 1024)
    }

    @Test fun veryLargeImageGetsLargerFactor() = assertEquals(4, Media.sampleSize(16000, 12000, 4096))
}
