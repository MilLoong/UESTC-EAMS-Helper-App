package edu.uestc.eams.helper.data.web

import edu.uestc.eams.helper.data.session.StoredCookie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WebSessionImportTest {

  private val jwt =
      "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.abc"

    @Test
    fun extract_jwt_from_page_url_query() {
        val url =
            "https://eamsapp.uestc.edu.cn/?jsessionid=$jwt&userId=1&roles=student#/"
        assertEquals(jwt, WebSessionImport.extractJwtFromPageUrl(url))
        val normalized =
            WebSessionImport.buildNormalizedSession(
                pageUrl = url,
                storageToken = null,
                cookies = emptyList(),
            )
        assertEquals(
            jwt,
            normalized.single { it.name == "JSESSIONID" }.value,
        )
    }

    @Test
    fun validate_passes_when_jwt_cookie_present_even_if_page_not_eamsapp() {
    val cookies =
        listOf(
            StoredCookie(
                name = "JSESSIONID",
                value = jwt,
                domain = "eamsapp.uestc.edu.cn",
                path = "/",
                expiresAtMillis = null,
                secure = true,
                httpOnly = true,
            ),
        )
    assertNull(WebSessionImport.validate("https://idas.uestc.edu.cn/authserver/login", cookies))
  }

  @Test
  fun extract_jwt_from_non_jsessionid_cookie_name() {
    val cookies =
        listOf(
            StoredCookie(
                name = "saber-access-token",
                value = jwt,
                domain = "eamsapp.uestc.edu.cn",
                path = "/",
                expiresAtMillis = null,
                secure = true,
                httpOnly = true,
            ),
        )
    assertEquals(jwt, WebSessionImport.extractEamsappJwt(cookies))
    val normalized = WebSessionImport.normalizeForOkHttp(cookies)
    assertNotNull(
        normalized.singleOrNull {
          it.name == "JSESSIONID" && it.domain.contains("eamsapp")
        },
    )
  }
}
