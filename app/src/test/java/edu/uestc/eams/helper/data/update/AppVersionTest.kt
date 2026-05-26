package edu.uestc.eams.helper.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    @Test
    fun isRemoteNewer_compares_semver_parts() {
        assertTrue(AppVersion.isRemoteNewer("2.3", "2.2"))
        assertTrue(AppVersion.isRemoteNewer("v1.0.1", "1.0.0"))
        assertFalse(AppVersion.isRemoteNewer("1.0.0", "2.2"))
        assertFalse(AppVersion.isRemoteNewer("2.2", "2.2"))
    }
}
