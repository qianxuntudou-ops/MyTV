package com.mytv0

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import com.mytv0.databinding.PlayerBinding
import com.mytv0.models.TVModel
import com.mytv0.requests.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerFragment : Fragment() {
    private var _binding: PlayerBinding? = null
    private val binding get() = _binding!!

    private var tvModel: TVModel? = null
    private val handler = Handler(Looper.getMainLooper())
    private val delayHideVolume = 2 * 1000L
    private var currentQuality = CctvLiveResolver.Quality.fromStoredValue(SP.cctvQuality)
    private var player: ExoPlayer? = null
    private var playJob: Job? = null
    private var currentResult: CctvLiveResolver.Result? = null
    private var currentUrlIndex = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = PlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupPlayer()
        (activity as MainActivity).ready(TAG)
    }

    private fun setupPlayer() {
        val context = requireContext()
        val mediaSourceFactory = DefaultMediaSourceFactory(
            OkHttpDataSource.Factory(HttpClient.okHttpClient)
        )
        player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .also { exoPlayer ->
                exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
                exoPlayer.playWhenReady = true
                exoPlayer.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            tvModel?.setErrInfo("")
                            tvModel?.retryTimes = 0
                            Log.i(TAG, "ready ${tvModel?.tv?.title}")
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(TAG, "player error ${tvModel?.tv?.title}", error)
                        if (!playBackup()) {
                            tvModel?.setErrInfo(R.string.play_error.getString())
                        }
                    }
                })
            }

        binding.player.player = player
        binding.player.useController = false
        binding.player.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        binding.player.setShutterBackgroundColor(
            ContextCompat.getColor(requireContext(), R.color.black)
        )
        binding.player.keepScreenOn = true
    }

    private fun releasePlayer() {
        playJob?.cancel()
        playJob = null
        currentResult = null
        currentUrlIndex = 0
        binding.player.player = null
        player?.release()
        player = null
    }

    private fun playResolved(model: TVModel) {
        playJob?.cancel()
        playJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    CctvLiveResolver.resolve(
                        model.tv.uris.firstOrNull().orEmpty(),
                        currentQuality
                    )
                }
                if (!isAdded) {
                    return@launch
                }
                startPlayback(result)
            } catch (e: Exception) {
                Log.e(TAG, "resolve error ${model.tv.title}", e)
                model.setErrInfo(R.string.play_error.getString())
            }
        }
    }

    fun play(model: TVModel) {
        tvModel = model
        playResolved(model)
    }

    fun switchQuality(step: Int) {
        if (step == 0) {
            return
        }
        val qualities = CctvLiveResolver.Quality.entries
        val currentIndex = qualities.indexOf(currentQuality).takeIf { it >= 0 } ?: 0
        val nextIndex = (currentIndex + step).mod(qualities.size)
        currentQuality = qualities[nextIndex]
        SP.cctvQuality = currentQuality.key
        "画质 ${currentQuality.label}".showToast(Toast.LENGTH_SHORT)
        tvModel?.let { playResolved(it) }
    }

    private fun startPlayback(result: CctvLiveResolver.Result) {
        currentResult = result
        currentUrlIndex = 0
        startPlayback(result, currentUrlIndex)
    }

    private fun startPlayback(result: CctvLiveResolver.Result, index: Int) {
        val current = tvModel ?: return
        val urls = listOf(result.url) + result.backups
        val url = urls.getOrNull(index) ?: return
        val mediaItem = MediaItem.Builder().setUri(url.toUri()).build()
        val factory = OkHttpDataSource.Factory(HttpClient.okHttpClient)
            .setDefaultRequestProperties(result.headers)
        val mediaSource = HlsMediaSource.Factory(factory).createMediaSource(mediaItem)

        player?.apply {
            stop()
            clearMediaItems()
            setMediaSource(mediaSource)
            prepare()
            playWhenReady = true
        }
        Log.i(TAG, "play ${current.tv.title} $url")
    }

    private fun playBackup(): Boolean {
        val result = currentResult ?: return false
        val nextIndex = currentUrlIndex + 1
        val urls = listOf(result.url) + result.backups
        if (nextIndex >= urls.size) {
            return false
        }
        currentUrlIndex = nextIndex
        startPlayback(result, currentUrlIndex)
        return true
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
        player?.playWhenReady = true
    }

    override fun onPause() {
        player?.playWhenReady = false
        super.onPause()
    }

    override fun onDestroyView() {
        handler.removeCallbacksAndMessages(null)
        releasePlayer()
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val TAG = "PlayerFragment"
    }
}
