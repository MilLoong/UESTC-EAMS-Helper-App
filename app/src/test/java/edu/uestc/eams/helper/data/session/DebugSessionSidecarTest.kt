package edu.uestc.eams.helper.data.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DebugSessionSidecarTest {
    @Test
    fun encode_decode_round_trip() {
        val cookies = """[{"name":"JSESSIONID","value":"abc"}]"""
        val raw = DebugSessionSidecar.encode(cookies)
        assertEquals(cookies, DebugSessionSidecar.decode(raw))
    }

    @Test
    fun decode_rejects_blank() {
        assertNull(DebugSessionSidecar.decode(""))
        assertNull(DebugSessionSidecar.decode("{}"))
        assertNull(DebugSessionSidecar.decode("{not json"))
    }
}
