package edu.uestc.eams.helper.server

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerModePolicyTest {
    @Test fun confirmedModesRemainSelected() {
        assertEquals("local", ServerModePolicy.confirmed("local", "server"))
        assertEquals("server", ServerModePolicy.confirmed("server", "local"))
    }

    @Test fun pendingSwitchUsesPreviousMode() {
        assertEquals("local", ServerModePolicy.confirmed("switching", "local"))
        assertEquals("server", ServerModePolicy.confirmed("switching", "server"))
    }

    @Test fun legacyPendingStateHasASelection() {
        assertEquals("server", ServerModePolicy.confirmed("switching", ""))
        assertEquals("server", ServerModePolicy.confirmed("blocked", ""))
    }
}
