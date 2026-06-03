package edu.uestc.eams.helper.data.repository

import edu.uestc.eams.helper.data.network.EamsFetchException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class UserDataFetchErrorsTest {

    @Test
    fun unauthorized_api_msg_is_session_invalid() {
        val mapped =
            runBlocking {
                UserDataFetchErrors.map(IllegalStateException("请求未授权"))
            }
        assertTrue(
            mapped is EamsFetchException.SessionInvalid ||
                mapped is EamsFetchException.OffCampus,
        )
    }

    @Test
    fun auth_failure_message_is_classified() {
        val mapped =
            runBlocking {
                UserDataFetchErrors.map(
                    IllegalStateException("课表 接口会话失效，请重新登录。"),
                )
            }
        assertTrue(
            mapped is EamsFetchException.SessionInvalid ||
                mapped is EamsFetchException.OffCampus,
        )
    }
}
