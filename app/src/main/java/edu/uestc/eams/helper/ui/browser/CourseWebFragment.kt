package edu.uestc.eams.helper.ui.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.fragment.app.Fragment
import edu.uestc.eams.helper.CourseWebActivity
import edu.uestc.eams.helper.R
import edu.uestc.eams.helper.data.network.ApiConstants
import edu.uestc.eams.helper.data.web.WebViewCookieReader
import edu.uestc.eams.helper.databinding.FragmentCourseWebBinding
import java.util.Locale

/**
 * 备选内置浏览器：`CourseWebActivity` 内嵌 WebView，用于一网通／统一认证与[读取 Cookie]。
 *
 * Idas/CAS 走移动端 UA；一网通仍为桌面 UA，与 OkHttp/脚本对齐。
 */
class CourseWebFragment : Fragment() {

    private var _binding: FragmentCourseWebBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCourseWebBinding.inflate(inflater, container, false)
        configureWeb(binding.webView)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnWebNavigate.setOnClickListener { navigateFromUrlBar() }
        binding.btnWebBack.setOnClickListener { goBackIfPossible() }
        binding.btnWebReload.setOnClickListener { binding.webView.reload() }
        binding.btnWebExportCookies.setOnClickListener {
            val host = activity
            when (host) {
                is CourseWebActivity -> host.triggerWebViewCookieExport()
                else ->
                    Toast
                        .makeText(requireContext(), R.string.toast_wv_browser_not_ready, Toast.LENGTH_SHORT)
                        .show()
            }
        }
        binding.edtWebUrl.setOnEditorActionListener { _, actionId, _ ->
            when (actionId) {
                EditorInfo.IME_ACTION_GO,
                EditorInfo.IME_ACTION_DONE,
                EditorInfo.IME_ACTION_NEXT,
                -> {
                    navigateFromUrlBar()
                    true
                }
                else -> false
            }
        }
        val seed = arguments?.getString(ARG_INITIAL_URL)?.trim()
        if (!seed.isNullOrEmpty()) {
            loadFallback(seed)
        } else {
            val url = ApiConstants.casLoginUrlWithService()
            binding.edtWebUrl.setText(url)
            loadFallback(url)
        }
    }

    /** 校验并加载地址栏网址（支持省略协议，默认补 `https://`）。*/
    fun navigateFromUrlBar() {
        val raw = binding.edtWebUrl.text?.toString().orEmpty()
        val url = normalizeUserUrl(raw)
        when {
            raw.isBlank() ->
                Toast
                    .makeText(requireContext(), R.string.toast_web_need_url, Toast.LENGTH_SHORT)
                    .show()
            url == null ->
                Toast
                    .makeText(requireContext(), R.string.toast_web_url_invalid, Toast.LENGTH_SHORT)
                    .show()
            else -> {
                applyWebRenderProfileForUrl(binding.webView, url)
                binding.webView.loadUrl(url)
            }
        }
    }

    /** 页面内[网页返回]按钮：在 WebView 历史内后退。 */
    fun goBackIfPossible(): Boolean {
        val w = _binding?.webView ?: return false
        return if (w.canGoBack()) {
            w.goBack()
            true
        } else {
            false
        }
    }

    /**
     * Idas/CAS：Chrome Mobile UA + `overview`，让统一认证返回移动布局（避免 Logo/语言层叠错位）。
     * 一网通 online：**桌面 UA** + 仅 `wide viewport`，与 OkHttp/脚本对齐。
     */
    private fun applyWebRenderProfileForUrl(webView: WebView, navigationUrlHint: String?) {
        val raw = navigationUrlHint?.trim().orEmpty()
        val lookupUrl =
            when {
                raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true) -> raw
                else -> ApiConstants.ONLINE_PAGE_URL
            }
        val host = runCatching { Uri.parse(lookupUrl).host?.lowercase(Locale.ROOT) }.getOrNull()
        val casMobile = ApiConstants.webViewShouldUseCasMobileLayout(host)

        val s = webView.settings
        val ua =
            if (casMobile) {
                ApiConstants.WEBVIEW_USER_AGENT_IDAS_MOBILE
            } else {
                ApiConstants.WEBVIEW_USER_AGENT
            }
        if (s.userAgentString != ua) s.userAgentString = ua

        /*
         * 统一认证页：缩放整页进屏宽；一网通仍为固定缩放，可调双指。
         */
        if (casMobile) {
            s.useWideViewPort = true
            s.loadWithOverviewMode = true
        } else {
            s.useWideViewPort = true
            s.loadWithOverviewMode = false
        }



    }



    @SuppressLint("SetJavaScriptEnabled")


    private fun configureWeb(v: WebView) {
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(v, true)

        val s = v.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.loadsImagesAutomatically = true

        /*
         * 老教务常为 https 主页里嵌 http iframe/脚本；默认 MIXED_CONTENT_NEVER_ALLOW 会导致中间区域空白。
         */
        s.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.setSupportZoom(true)
        s.builtInZoomControls = true
        s.displayZoomControls = false

        /*
         * 具体 UA / overview 策略见 [applyWebRenderProfileForUrl]（首次导航 Url 取自 arguments 种子）。
         */
        applyWebRenderProfileForUrl(
            v,
            arguments?.getString(ARG_INITIAL_URL)?.trim()?.takeUnless { it.isEmpty() },
        )
        /*
         * 部分流程用 window.open / target=_blank；允许 JS 拉起窗口链路（仍为同一 WebView 栈内）。
         */
        s.javaScriptCanOpenWindowsAutomatically = true
        s.textZoom = 100

        v.webChromeClient = WebChromeClient()

        v.webViewClient =
            object : WebViewClient() {

                override fun onPageStarted(
                    webView: WebView?,
                    url: String?,
                    favicon: Bitmap?,
                ) {
                    webView?.let { applyWebRenderProfileForUrl(it, url) }
                    binding.progress.visibility = View.VISIBLE
                }

                override fun onPageFinished(
                    webView: WebView?,
                    url: String?,
                ) {
                    binding.progress.visibility = View.GONE
                    syncUrlBarIfIdle(webView?.url ?: url)
                    webView?.evaluateJavascript("(typeof table0 !== 'undefined' && table0 != null)") { probe ->
                        if (probe == "true") {
                            Log.d(TAG, "table0 可用: $url")
                        }
                    }
                }

                override fun doUpdateVisitedHistory(
                    view: WebView?,
                    url: String?,
                    isReload: Boolean,
                ) {
                    super.doUpdateVisitedHistory(view, url, isReload)
                    syncUrlBarIfIdle(url)
                }

                /** 必须与 Chrome 一致跟完服务端 302/HTML 跳转，否则会停在[半截]壳页面。 */
                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
                    false

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean = false

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?,
                ) {
                    if (request?.isForMainFrame == true && error != null) {
                        Log.w(
                            TAG,
                            "main frame ${error.description} (${error.errorCode}) ${request.url}",
                        )
                    }
                }
            }
    }

    private fun syncUrlBarIfIdle(displayUrl: String?) {
        val eb = _binding ?: return
        val url = displayUrl?.trim()?.takeUnless { it.isEmpty() } ?: return
        if (eb.edtWebUrl.hasFocus()) return
        eb.edtWebUrl.setText(url)
    }

    fun loadHomeAfterCookies() {
        val home = ApiConstants.ONLINE_PAGE_URL
        binding.edtWebUrl.setText(home)
        applyWebRenderProfileForUrl(binding.webView, home)
        binding.webView.loadUrl(home)
    }

    fun loadFallback(url: String) {
        val trimmed = url.trim()
        val toLoad = normalizeUserUrl(trimmed) ?: trimmed
        normalizeUserUrl(trimmed)?.let { binding.edtWebUrl.setText(it) }
            ?: binding.edtWebUrl.setText(trimmed)
        applyWebRenderProfileForUrl(binding.webView, toLoad)
        binding.webView.loadUrl(toLoad)
    }

    fun currentTopUrl(): String? = _binding?.webView?.url?.trim()?.takeUnless { it.isEmpty() }

    /** 刷新 CookieManager，便于随后读取 Cookie。 */
    fun postFlushCookiesSync() {
        val w = _binding?.webView
        val cm =
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                flush()
            }
        if (w != null) {
            w.post { cm.flush() }
        }
    }

    /** 读取当前页 document.cookie。 */
    fun readDocumentCookie(onResult: (String) -> Unit) {
        val w = _binding?.webView
        if (w == null) {
            onResult("")
            return
        }
        w.evaluateJavascript("(function(){try{return document.cookie||'';}catch(e){return '';}})()") {
                raw ->
            onResult(WebViewCookieReader.unwrapEvaluateJavascriptString(raw))
        }
    }

    /** 探测 Cookie 时用：优先当前加载 URL + BackForwardList（比固定两三个 seed 更接近真实会话范围）。*/
    fun cookieSnapshotUrlHints(): List<String> {
        val b = _binding ?: return emptyList()
        val w = b.webView
        val urls = LinkedHashSet<String>()
        w.url?.takeIf { it.startsWith("http") }?.let(urls::add)
        try {
            val history = w.copyBackForwardList()
            for (i in 0 until history.size) {
                history.getItemAtIndex(i)?.url?.takeIf { u -> u.startsWith("http") }?.let(urls::add)
            }
        } catch (_: Throwable) {}
        return urls.take(48).toList()
    }

    private fun normalizeUserUrl(raw: String): String? {
        val t = raw.trim().ifBlank { return null }
        val withScheme =
            when {
                t.startsWith("http://", ignoreCase = true) -> t
                t.startsWith("https://", ignoreCase = true) -> t
                t.startsWith("//") -> "https:$t"
                else -> "https://$t"
            }
        val uri =
            Uri.parse(withScheme).takeUnless { it.host.isNullOrBlank() } ?: return null
        val scheme = uri.scheme?.lowercase()
        return withScheme.takeIf { scheme == "http" || scheme == "https" }
    }

    override fun onDestroyView() {
        _binding?.webView?.apply {
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            stopLoading()
            destroy()
        }
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_INITIAL_URL = "initial_url"
        private val TAG = CourseWebFragment::class.java.simpleName

        /** @param initialUrl `null`/空白则不自动跳转，仅在地址栏显示默认一网通门户。*/
        fun newInstance(initialUrl: String?): CourseWebFragment {
            val f = CourseWebFragment()
            val t = initialUrl?.trim().orEmpty()
            if (t.isNotEmpty()) {
                f.arguments = Bundle().apply { putString(ARG_INITIAL_URL, t) }
            }
            return f
        }
    }
}
