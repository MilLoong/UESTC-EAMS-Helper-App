package edu.uestc.eams.helper

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.webkit.CookieManager
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import edu.uestc.eams.helper.data.network.replaceJarWithStored
import edu.uestc.eams.helper.data.session.SessionCookieStorage
import edu.uestc.eams.helper.data.web.WebCookieImportDiagnostics
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
            val webViewUrl = frag?.currentTopUrl()
            val pageUrl = frag?.currentImportPageUrl()
            val urlBarText = frag?.urlBarText()
            val docHeader =
                suspendCoroutine { cont ->
                    if (frag == null || frag.view == null) {
                        cont.resume("")
                    } else {
                        frag.readDocumentCookie { cont.resume(it) }
                    }
                }
            var storageToken = readBladeStorageTokenSync(frag)
            if (
                storageToken.isBlank() &&
                WebSessionImport.extractJwtFromPageUrl(pageUrl) == null
            ) {
                delay(400)
                storageToken = readBladeStorageTokenSync(frag)
            }

            val fromCm = WebViewCookieReader.collectUeStcSnapshot(prioritizedUrls = hints)
            val fromDoc =
                WebViewCookieReader.snapshotFromDocumentCookie(header = docHeader, pageUrl = pageUrl)
            val fromUrl =
                WebSessionImport.jwtFromPageUrl(pageUrl)?.let { listOf(it) }.orEmpty()
            val fromStorage =
                WebSessionImport.jwtFromWebStorageToken(storageToken)?.let { listOf(it) }.orEmpty()
            val merged =
                WebViewCookieReader.mergeCookieLists(fromCm, fromDoc, fromStorage, fromUrl)
            val list = WebSessionImport.buildNormalizedSession(pageUrl, storageToken, merged)

            val reject = WebSessionImport.validate(pageUrl, storageToken, list)
            when {
                list.isEmpty() -> {
                    Toast
                        .makeText(this@CourseWebActivity, R.string.toast_wv_cookies_none, Toast.LENGTH_LONG)
                        .show()
                    showImportFailureReport(
                        buildImportFailureReport(
                            webViewUrl = webViewUrl,
                            pageUrl = pageUrl,
                            urlBarText = urlBarText,
                            hints = hints,
                            docHeader = docHeader,
                            storageToken = storageToken,
                            fromCm = fromCm,
                            fromDoc = fromDoc,
                            fromStorage = fromStorage,
                            fromUrl = fromUrl,
                            merged = merged,
                            normalized = list,
                        ),
                    )
                }
                reject != null -> {
                    Toast.makeText(this@CourseWebActivity, reject, Toast.LENGTH_LONG).show()
                    showImportFailureReport(
                        buildImportFailureReport(
                            webViewUrl = webViewUrl,
                            pageUrl = pageUrl,
                            urlBarText = urlBarText,
                            hints = hints,
                            docHeader = docHeader,
                            storageToken = storageToken,
                            fromCm = fromCm,
                            fromDoc = fromDoc,
                            fromStorage = fromStorage,
                            fromUrl = fromUrl,
                            merged = merged,
                            normalized = list,
                        ),
                    )
                }
                else -> {
                    appGraph.cookieJar.replaceJarWithStored(list)
                    SessionCookieStorage(this@CourseWebActivity).persistFromJar(appGraph.cookieJar)
                    Toast
                        .makeText(
                            this@CourseWebActivity,
                            R.string.toast_wv_cookies_overwritten,
                            Toast.LENGTH_SHORT,
                        )
                        .show()
                }
            }
        }
    }

    private fun buildImportFailureReport(
        webViewUrl: String?,
        pageUrl: String?,
        urlBarText: String?,
        hints: List<String>,
        docHeader: String,
        storageToken: String,
        fromCm: List<edu.uestc.eams.helper.data.session.StoredCookie>,
        fromDoc: List<edu.uestc.eams.helper.data.session.StoredCookie>,
        fromStorage: List<edu.uestc.eams.helper.data.session.StoredCookie>,
        fromUrl: List<edu.uestc.eams.helper.data.session.StoredCookie>,
        merged: List<edu.uestc.eams.helper.data.session.StoredCookie>,
        normalized: List<edu.uestc.eams.helper.data.session.StoredCookie>,
    ): String {
        val probeByUrl = WebCookieImportDiagnostics.collectUrlProbes(hints)
        return WebCookieImportDiagnostics.buildReport(
            WebCookieImportDiagnostics.Context(
                webViewUrl = webViewUrl,
                importPageUrl = pageUrl,
                urlBarText = urlBarText,
                hintUrls = hints,
                documentCookieHeader = docHeader,
                storageToken = storageToken,
                fromCookieManager = fromCm,
                fromDocument = fromDoc,
                fromStorage = fromStorage,
                fromUrl = fromUrl,
                merged = merged,
                normalized = normalized,
                probeByUrl = probeByUrl,
            ),
        )
    }

    private fun showImportFailureReport(report: String) {
        val scroll = ScrollView(this)
        val pad = (16 * resources.displayMetrics.density).toInt()
        scroll.setPadding(pad, pad, pad, pad)
        val tv =
            TextView(this).apply {
                text = report
                textSize = 11f
                setTextIsSelectable(true)
            }
        scroll.addView(tv)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.web_import_failure_report_title)
            .setView(scroll)
            .setPositiveButton(R.string.web_import_guide_ok, null)
            .show()
    }

    private suspend fun readBladeStorageTokenSync(frag: CourseWebFragment?): String =
        suspendCoroutine { cont ->
            if (frag == null || frag.view == null) {
                cont.resume("")
            } else {
                frag.readBladeStorageToken { cont.resume(it) }
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
