package com.mytv0

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.mytv0.databinding.PlayerBinding
import com.mytv0.models.TVModel

class PlayerFragment : Fragment() {
    private var _binding: PlayerBinding? = null
    private val binding get() = _binding!!

    private var tvModel: TVModel? = null
    private val handler = Handler(Looper.getMainLooper())
    private val delayHideVolume = 2 * 1000L
    private var playbackSession = 0
    private var injectedCount = 0
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = PlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        DebugLog.i(TAG, "onViewCreated webPlayer=true")
        configureWebView(binding.webPlayer)
        (activity as MainActivity).ready(TAG)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(webView: WebView) {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.keepScreenOn = true
        webView.setBackgroundColor(Color.BLACK)
        webView.isFocusable = false
        webView.isFocusableInTouchMode = false
        webView.addJavascriptInterface(Bridge(), BRIDGE_NAME)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                DebugLog.i(TAG, "webPageStarted url=$url")
                injectedCount = 0
                tvModel?.setErrInfo("")
                super.onPageStarted(view, url, favicon)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                DebugLog.i(TAG, "webPageFinished url=$url")
                injectPlayerModeRepeatedly()
                super.onPageFinished(view, url)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    val message = "网页加载失败 ${error.errorCode}"
                    DebugLog.e(TAG, "$message url=${request.url} desc=${error.description}")
                    tvModel?.setErrInfo(message)
                }
                super.onReceivedError(view, request, error)
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse
            ) {
                if (request.isForMainFrame) {
                    DebugLog.e(
                        TAG,
                        "webHttpError status=${errorResponse.statusCode} reason=${errorResponse.reasonPhrase} url=${request.url}"
                    )
                }
                super.onReceivedHttpError(view, request, errorResponse)
            }

            override fun onReceivedSslError(
                view: WebView,
                handler: SslErrorHandler,
                error: SslError
            ) {
                DebugLog.e(TAG, "webSslError primary=${error.primaryError} url=${error.url}")
                handler.cancel()
                tvModel?.setErrInfo("网页证书错误")
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                val allowed = CCTV_HOSTS.any { request.url.host?.endsWith(it) == true }
                if (!allowed) {
                    DebugLog.w(TAG, "webNavigationBlocked url=$url")
                    return true
                }
                return false
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                DebugLog.i(TAG, "webFullscreen show")
                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                binding.fullscreenContainer.addView(
                    view,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                binding.fullscreenContainer.visibility = View.VISIBLE
                binding.webPlayer.visibility = View.GONE
                (activity as? MainActivity)?.onVideoRenderingStart(tvModel?.tv?.title.orEmpty())
            }

            override fun onHideCustomView() {
                DebugLog.i(TAG, "webFullscreen hide")
                customView?.let { binding.fullscreenContainer.removeView(it) }
                customView = null
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
                binding.fullscreenContainer.visibility = View.GONE
                binding.webPlayer.visibility = View.VISIBLE
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                DebugLog.i(
                    TAG,
                    "webConsole ${consoleMessage.messageLevel()} ${consoleMessage.sourceId()}:${consoleMessage.lineNumber()} ${consoleMessage.message()}"
                )
                return true
            }
        }
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = USER_AGENT
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                safeBrowsingEnabled = false
            }
        }
    }

    fun play(model: TVModel) {
        DebugLog.i(TAG, "play request ${model.tv.title}")
        tvModel = model
        if (_binding == null || view == null || !isAdded) {
            DebugLog.w(TAG, "play skipped viewNotReady ${model.tv.title}")
            return
        }
        playbackSession++
        hideCustomView()
        val channelId = model.tv.uris.firstOrNull().orEmpty()
        val baseUrl = buildCctvBaseUrl(channelId)
        val html = buildPlayerHtml(channelId)
        DebugLog.i(TAG, "webLoad ${model.tv.title} channelId=$channelId baseUrl=$baseUrl")
        binding.webPlayer.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
    }

    fun switchQuality(step: Int) {
        if (step == 0) {
            return
        }
        "央视网页自动选择画质".showToast(Toast.LENGTH_SHORT)
        DebugLog.i(TAG, "switchQuality ignored webPlayerAuto step=$step")
        injectPlayerModeRepeatedly()
    }

    private fun buildCctvBaseUrl(channelId: String): String {
        val safeId = channelId.ifBlank { "cctv1" }
        return "https://tv.cctv.com/live/$safeId/m/"
    }

    private fun buildPlayerHtml(channelId: String): String {
        val safeId = channelId.ifBlank { "cctv1" }
        return """
            <!doctype html>
            <html>
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
              <title>MyTV $safeId</title>
              <style>
                html,body,#player{width:100%;height:100%;margin:0;padding:0;background:#000;overflow:hidden;}
                #player{position:fixed;left:0;top:0;z-index:1;}
                video,canvas,object,embed,iframe{
                  position:fixed!important;
                  left:0!important;
                  top:0!important;
                  width:100vw!important;
                  height:100vh!important;
                  max-width:none!important;
                  max-height:none!important;
                  object-fit:contain!important;
                  background:#000!important;
                }
                .control_bar,.controlbar,.title,.logo,.advertising,[id*="ad"],[class*="ad"],[class*="share"]{display:none!important;}
              </style>
              <script src="https://r.img.cctvpic.com/photoAlbum/templet/js/jquery-1.7.2.min.js"></script>
              <script src="https://js.player.cntv.cn/creator/swfobject.js"></script>
              <script src="https://js.player.cntv.cn/creator/liveplayer.js"></script>
            </head>
            <body>
              <div id="player"></div>
              <script>
                (function() {
                  var channel = '$safeId';
                  function size() {
                    return {
                      w: Math.max(document.documentElement.clientWidth || 0, window.innerWidth || 0, 1280),
                      h: Math.max(document.documentElement.clientHeight || 0, window.innerHeight || 0, 720)
                    };
                  }
                  function report() {
                    try {
                      var video = document.getElementsByTagName('video')[0];
                      if (window.MyTVBridge && video) {
                        window.MyTVBridge.onVideoState(JSON.stringify({
                          currentTime: video.currentTime || 0,
                          readyState: video.readyState || 0,
                          paused: !!video.paused,
                          videoWidth: video.videoWidth || 0,
                          videoHeight: video.videoHeight || 0,
                          src: video.currentSrc || video.src || ''
                        }));
                      }
                    } catch (e) {
                      console.log('mytv report error ' + e);
                    }
                  }
                  function fitAndPlay() {
                    var videos = Array.prototype.slice.call(document.getElementsByTagName('video'));
                    videos.forEach(function(video) {
                      video.autoplay = true;
                      video.muted = false;
                      video.controls = false;
                      video.style.objectFit = 'contain';
                      video.play && video.play().catch(function(error) {
                        console.log('mytv play rejected ' + error);
                      });
                    });
                    report();
                  }
                  function start() {
                    if (typeof createLivePlayer !== 'function') {
                      console.log('mytv wait createLivePlayer');
                      setTimeout(start, 300);
                      return;
                    }
                    var s = size();
                    var playerParas = {
                      divId: 'player',
                      w: s.w,
                      h: s.h,
                      t: channel,
                      isAutoPlay: 'true',
                      ruleVisible: 'false',
                      br: '',
                      posterImg: '',
                      isLive4k: 'false',
                      isHttps: 'true',
                      wmode: 'opaque',
                      hasBarrage: 'false',
                      playerType: 'hw',
                      webFullScreenOn: 'false',
                      isLeftBottom: 'false',
                      jumpToApp: 'false',
                      others: ''
                    };
                    console.log('mytv createLivePlayer ' + channel + ' ' + s.w + 'x' + s.h);
                    createLivePlayer(playerParas);
                    setInterval(fitAndPlay, 1000);
                    setTimeout(fitAndPlay, 300);
                    setTimeout(fitAndPlay, 1200);
                    setTimeout(fitAndPlay, 3000);
                  }
                  window.addEventListener('resize', fitAndPlay);
                  start();
                })();
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun injectPlayerModeRepeatedly() {
        val session = playbackSession
        repeat(INJECT_ATTEMPTS) { index ->
            handler.postDelayed({
                if (session != playbackSession || _binding == null || !isAdded) {
                    return@postDelayed
                }
                injectedCount = index + 1
                binding.webPlayer.evaluateJavascript(PLAYER_MODE_SCRIPT) { result ->
                    DebugLog.i(TAG, "webInject attempt=${index + 1} result=$result")
                }
            }, index * INJECT_INTERVAL_MS)
        }
    }

    private fun hideCustomView() {
        if (customView == null) {
            return
        }
        binding.fullscreenContainer.removeAllViews()
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        binding.fullscreenContainer.visibility = View.GONE
        binding.webPlayer.visibility = View.VISIBLE
    }

    fun showVolume(visibility: Int) {
        binding.icon.visibility = visibility
        binding.volume.visibility = visibility
        hideVolume()
    }

    fun setVolumeMax(volume: Int) {
        binding.volume.max = volume
    }

    fun setVolume(progress: Int, volume: Boolean = false) {
        val context = requireContext()
        binding.volume.progress = progress
        binding.icon.setImageDrawable(
            ContextCompat.getDrawable(
                context,
                if (volume) {
                    if (progress > 0) R.drawable.volume_up_24px else R.drawable.volume_off_24px
                } else {
                    R.drawable.light_mode_24px
                }
            )
        )
    }

    fun hideVolume() {
        handler.removeCallbacks(hideVolumeRunnable)
        handler.postDelayed(hideVolumeRunnable, delayHideVolume)
    }

    fun hideVolumeNow() {
        handler.removeCallbacks(hideVolumeRunnable)
        handler.postDelayed(hideVolumeRunnable, 0)
    }

    private val hideVolumeRunnable = Runnable {
        binding.icon.visibility = View.GONE
        binding.volume.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()
        DebugLog.i(TAG, "onResume")
        binding.webPlayer.onResume()
        injectPlayerModeRepeatedly()
    }

    override fun onPause() {
        DebugLog.i(TAG, "onPause")
        binding.webPlayer.onPause()
        super.onPause()
    }

    override fun onDestroyView() {
        DebugLog.i(TAG, "onDestroyView")
        handler.removeCallbacksAndMessages(null)
        playbackSession++
        hideCustomView()
        binding.webPlayer.apply {
            stopLoading()
            loadUrl("about:blank")
            removeJavascriptInterface(BRIDGE_NAME)
            webChromeClient = null
            webViewClient = WebViewClient()
            destroy()
        }
        _binding = null
        super.onDestroyView()
    }

    private inner class Bridge {
        @JavascriptInterface
        fun onVideoState(state: String) {
            handler.post {
                DebugLog.i(TAG, "webVideoState $state")
                if (state.contains("\"readyState\":0") || state.contains("\"paused\":true")) {
                    return@post
                }
                tvModel?.setErrInfo("")
                tvModel?.retryTimes = 0
                (activity as? MainActivity)?.onVideoRenderingStart(tvModel?.tv?.title.orEmpty())
            }
        }
    }

    companion object {
        private const val TAG = "PlayerFragment"
        private const val BRIDGE_NAME = "MyTVBridge"
        private const val INJECT_ATTEMPTS = 12
        private const val INJECT_INTERVAL_MS = 1_500L
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; TV; MyTV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36"
        private val CCTV_HOSTS = setOf(
            "cctv.com",
            "cntv.cn",
            "cctvpic.com",
            "player.cntv.cn",
            "data.cctv.com",
            "alicdn.com",
            "qq.com"
        )

        private val PLAYER_MODE_SCRIPT = """
            (function() {
              try {
                if (!document.documentElement || !document.body) {
                  return 'not-ready';
                }
                document.documentElement.style.background = '#000';
                document.body.style.background = '#000';
                document.body.style.margin = '0';
                document.body.style.padding = '0';
                document.body.style.overflow = 'hidden';

                var style = document.getElementById('mytv-web-player-style');
                if (!style) {
                  style = document.createElement('style');
                  style.id = 'mytv-web-player-style';
                  style.innerHTML = [
                    'html,body{width:100%!important;height:100%!important;margin:0!important;padding:0!important;background:#000!important;overflow:hidden!important;}',
                    '.headernew,.page_bottom,.ELMTg32sIxqsfKtFXmdLumkd231024,.ggcontainer,.swiper-container,.footer,.nav,.column_wrapper,.vspace,[class*="tuijian"],[class*="recommend"],[class*="xuqiu"]{display:none!important;}',
                    '.page_wrap,.page_body,.bg_top_h_tile,.bg_top_owner,.bg_bottom_h_tile,.bg_bottom_owner,.ELMTJ3vol41qoeuF7qDWG00e231024,.ind_video_xq18570,.ind_video_xq18570new,.video_box,#player{position:fixed!important;left:0!important;top:0!important;width:100vw!important;height:100vh!important;margin:0!important;padding:0!important;background:#000!important;z-index:2147483647!important;overflow:hidden!important;}',
                    'video,canvas,object,embed,iframe{position:fixed!important;left:0!important;top:0!important;width:100vw!important;height:100vh!important;max-width:none!important;max-height:none!important;background:#000!important;object-fit:contain!important;z-index:2147483647!important;}'
                  ].join('\n');
                  document.head.appendChild(style);
                }

                var videos = Array.prototype.slice.call(document.getElementsByTagName('video'));
                videos.forEach(function(video) {
                  video.autoplay = true;
                  video.playsInline = false;
                  video.webkitPlaysInline = false;
                  video.muted = false;
                  video.controls = true;
                  video.style.objectFit = 'contain';
                  video.play && video.play().catch(function(error) {
                    console.log('mytv play rejected ' + error);
                  });
                });

                var candidates = Array.prototype.slice.call(document.querySelectorAll('button,a,div,span'));
                candidates.some(function(el) {
                  var text = (el.innerText || el.title || el.className || el.id || '').toString();
                  if (/播放|继续|play|start/i.test(text)) {
                    el.click();
                    return true;
                  }
                  return false;
                });

                var video = videos[0] || null;
                if (window.MyTVBridge && video) {
                  window.MyTVBridge.onVideoState(JSON.stringify({
                    currentTime: video.currentTime || 0,
                    readyState: video.readyState || 0,
                    paused: !!video.paused,
                    videoWidth: video.videoWidth || 0,
                    videoHeight: video.videoHeight || 0,
                    src: video.currentSrc || video.src || ''
                  }));
                } else if (window.MyTVBridge) {
                  window.MyTVBridge.onVideoState(JSON.stringify({
                    currentTime: 0,
                    readyState: 0,
                    paused: true,
                    videoWidth: 0,
                    videoHeight: 0,
                    src: '',
                    videoCount: videos.length
                  }));
                }
                return 'ok videoCount=' + videos.length + ' title=' + document.title;
              } catch (error) {
                console.log('mytv inject error ' + error);
                return 'error ' + error;
              }
            })();
        """.trimIndent()
    }
}
