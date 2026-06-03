package edu.uestc.eams.helper.data.eamsapp

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BladeJsonAuthTest {

    @Test
    fun responseOk_is_false_when_success_false_even_if_code_200() {
        val body = """{"code":200,"success":false,"msg":"请求未授权"}"""
        val rsp = response(200, body)
        assertFalse(BladeJson.responseOk(rsp, body))
    }

    @Test
    fun responseAuthFailed_detects_unauthorized_msg_with_code_200() {
        val body = """{"code":200,"success":false,"msg":"请求未授权"}"""
        val rsp = response(200, body)
        assertTrue(BladeJson.responseAuthFailed(rsp, body))
    }

    private fun response(code: Int, body: String): Response =
        Response.Builder()
            .request(Request.Builder().url("https://eamsapp.uestc.edu.cn/").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("OK")
            .build()
}
