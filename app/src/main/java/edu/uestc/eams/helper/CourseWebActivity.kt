package edu.uestc.eams.helper

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.webkit.CookieManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import edu.uestc.eams.helper.data.network.replaceJarWithStored
import edu.uestc.eams.helper.data.session.SessionCookieStorage
import edu.uestc.eams.helper.data.web.WebSessionImport
import edu.uestc.eams.helper.data.web.WebViewCookieReader
import edu.uestc.eams.helper.ui.browser.CourseWebFragment
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 内置浏览器与 Web 导入会话。 */
class CourseWebActivity : AppCompatActivity() {

    private val appGraph get() = application as EamsHelperApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_course_web)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { navigateUpOrFinish() }
        toolbar.setOnMenuItemClickListener(::onToolbarMenuSelected)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(
                    R.id.browser_fragment_container,
                    CourseWebFragment.newInstance(intent.getStringExtra(EXTRA_INITIAL_URL)),
                ).commit()
            showWebImportGuideDialog()
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    navigateUpOrFinish()
                }
            },
        )
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        onToolbarMenuSelected(item) || super.onOptionsItemSelected(item)

    private fun onToolbarMenuSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.menu_import_session -> {
                triggerWebViewCookieExport()
                true
            }
            else -> false
        }

    internal fun triggerWebViewCookieExport() {
        lifecycleScope.launch {
            val frag = browserFragment()
            frag?.postFlushCookiesSync()
            delay(520)
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                flush()
            }

            val hints = frag?.cookieSnapshotUrlHints().orEmpty()
            val pageUrl = frag?.currentTopUrl()
            val docHeader =
                suspendCoroutine { cont ->
                    if (frag == null || frag.view == null) {
                        cont.resume("")
                    } else {
                        frag.readDocumentCookie { cont.resume(it) }
                    }
                }

            val fromCm = WebViewCookieReader.collectUeStcSnapshot(prioritizedUrls = hints)
            val fromDoc =
                WebViewCookieReader.snapshotFromDocumentCookie(header = docHeader, pageUrl = pageUrl)
            val list = WebViewCookieReader.mergeCookieLists(fromCm, fromDoc)

            val reject = WebSessionImport.validate(pageUrl, list)
            if (reject != null) {
                Toast.makeText(this@CourseWebActivity, reject, Toast.LENGTH_LONG).show()
                return@launch
            }
            if (list.isEmpty()) {
                Toast
                    .makeText(this@CourseWebActivity, R.string.toast_wv_cookies_none, Toast.LENGTH_LONG)
                    .show()
            } else {
                appGraph.cookieJar.replaceJarWithStored(list)
                SessionCookieStorage(this@CourseWebActivity).persistFromJar(appGraph.cookieJar)
                Toast
                    .makeText(this@CourseWebActivity, R.string.toast_wv_cookies_overwritten, Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun showWebImportGuideDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.web_import_guide_title)
            .setMessage(R.string.web_import_guide_message)
            .setPositiveButton(R.string.web_import_guide_ok, null)
            .setCancelable(true)
            .show()
    }

    private fun navigateUpOrFinish() {
        finish()
    }

    private fun browserFragment(): CourseWebFragment? =
        supportFragmentManager.findFragmentById(R.id.browser_fragment_container) as? CourseWebFragment

    companion object {
        internal const val EXTRA_INITIAL_URL =
            "edu.uestc.eams.helper.EXTRA_INITIAL_URL"

        fun start(
            context: android.content.Context,
            initialUrl: String?,
        ) {
            context.startActivity(
                Intent(context, CourseWebActivity::class.java).apply {
                    initialUrl?.let { putExtra(EXTRA_INITIAL_URL, it) }
                },
            )
        }
    }
}
