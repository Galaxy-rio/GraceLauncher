package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.notifications.isTitleRedundant
import com.galaxyrio.gracelauncher.data.notifications.stripSenderFromTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationTitleTest {
    @Test fun titleRepeatingTheMessageSenderIsRedundant() {
        assertTrue(isTitleRedundant("Dada", "Dada: Good morning"))
        assertTrue(isTitleRedundant("Dada", "Dada: hi\nDada: again"))
        assertTrue(isTitleRedundant("Dada", "Dada"))
    }

    @Test fun channelIsKeptAndSenderDroppedFromTitle() {
        assertEquals("nms-cops-extended", stripSenderFromTitle("nms-cops-extended: Josef Krentz", "Josef Krentz: hi"))
        assertEquals("chan: Josef", stripSenderFromTitle("chan: Josef", "Anna: hi"))
        assertEquals("Dada", stripSenderFromTitle("Dada", "Dada: hi"))
    }

    @Test fun groupAndPlainTitlesAreKept() {
        assertFalse(isTitleRedundant("Family", "Dada: hi"))
        assertFalse(isTitleRedundant("Meeting moved", "Room 4 at 3pm"))
        assertFalse(isTitleRedundant("Dada", "Good morning"))
        assertFalse(isTitleRedundant("Dad", "Dada: Good morning"))
        assertFalse(isTitleRedundant("", "Dada: Good morning"))
    }
}
