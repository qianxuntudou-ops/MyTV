package com.mytv0

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.marginEnd
import androidx.core.view.marginTop
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.mytv0.databinding.ChannelBinding
import com.mytv0.models.TVModel

class ChannelFragment : Fragment() {
    private var _binding: ChannelBinding? = null
    private val binding get() = _binding!!

    private val handler = Handler(Looper.getMainLooper())
    private val delay: Long = 5000
    private var channel = 0
    private var channelCount = 0

    private lateinit var viewModel: MainViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ChannelBinding.inflate(inflater, container, false)
        binding.root.visibility = View.GONE

        val application = requireActivity().applicationContext as MyTVApplication
        binding.channel.layoutParams.width = application.px2Px(binding.channel.layoutParams.width)
        binding.channel.layoutParams.height = application.px2Px(binding.channel.layoutParams.height)

        val layoutParams = binding.channel.layoutParams as ViewGroup.MarginLayoutParams
        layoutParams.topMargin = application.px2Px(binding.channel.marginTop)
        layoutParams.marginEnd = application.px2Px(binding.channel.marginEnd)
        binding.channel.layoutParams = layoutParams

        binding.content.textSize = application.px2PxFont(binding.content.textSize)
        binding.time.textSize = application.px2PxFont(binding.time.textSize)
        binding.main.layoutParams.width = application.shouldWidthPx()
        binding.main.layoutParams.height = application.shouldHeightPx()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
    }

    fun show(tvModel: TVModel) {
        val tv = tvModel.tv
        Log.i(TAG, "show $tv")
        handler.removeCallbacks(hideRunnable)
        handler.removeCallbacks(playRunnable)
        binding.content.text =
            if (tv.number == -1) (tv.id + 1).toString() else tv.number.toString()
        view?.visibility = View.VISIBLE
        channel = 0
        channelCount = 0
        handler.postDelayed(hideRunnable, delay)
    }

    fun show(channel: Int) {
        Log.i(TAG, "input $channel ${this.channel}")
        val current = viewModel.groupModel.getCurrent()?.tv ?: return
        if (current.id > 10 && current.id == this.channel - 1) {
            this.channel = 0
            channelCount = 0
        }
        if (channelCount > 2) {
            return
        }

        channelCount++
        this.channel = "${this.channel}$channel".toInt()
        handler.removeCallbacks(hideRunnable)
        handler.removeCallbacks(playRunnable)
        binding.content.text = this.channel.toString()
        view?.visibility = View.VISIBLE
        if (channelCount < 3) {
            handler.postDelayed(playRunnable, delay)
        } else {
            playNow()
        }
    }

    fun playNow() {
        handler.postDelayed(playRunnable, 0)
    }

    override fun onResume() {
        super.onResume()
        if (view?.visibility == View.VISIBLE) {
            handler.postDelayed(hideRunnable, delay)
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(hideRunnable)
        handler.removeCallbacks(playRunnable)
    }

    private val hideRunnable = Runnable {
        binding.content.text = BLANK
        view?.visibility = View.GONE
    }

    fun hideSelf() {
        channel = 0
        channelCount = 0
        handler.postDelayed(hideRunnable, 0)
    }

    private val playRunnable = Runnable {
        var target = channel - 1
        viewModel.listModel.find { it.tv.number == channel }?.let {
            target = it.tv.id
        }
        if ((activity as MainActivity).play(target)) {
            channel = 0
            channelCount = 0
            handler.postDelayed(hideRunnable, delay)
        } else {
            hideSelf()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "ChannelFragment"
        private const val BLANK = ""
    }
}
