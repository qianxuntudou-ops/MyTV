package com.mytv0

import android.content.Context
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import java.util.Locale
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private var ok = 0
    private val playerFragment = PlayerFragment()
    private val errorFragment = ErrorFragment()
    private val loadingFragment = LoadingFragment()
    private val infoFragment = InfoFragment()
    private val channelFragment = ChannelFragment()
    private val timeFragment = TimeFragment()
    private val menuFragment = MenuFragment()
    private val settingFragment = SettingFragment()

    private val handler = Handler(Looper.myLooper()!!)
    private val delayHideMenu = 10 * 1000L
    private val delayHideSetting = 3 * 60 * 1000L

    private var doubleBackToExitPressedOnce = false
    private lateinit var gestureDetector: GestureDetector
    private lateinit var viewModel: MainViewModel
    private var isSafeToPerformFragmentTransactions = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_main)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        viewModel.init(this)
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    back()
                }
            }
        )

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.main_browse_fragment, playerFragment)
                .add(R.id.main_browse_fragment, infoFragment)
                .add(R.id.main_browse_fragment, channelFragment)
                .commitNowAllowingStateLoss()
        }
    }

    fun updateMenuSize() {
        menuFragment.updateSize()
    }

    fun ready(tag: String) {
        Log.i(TAG, "ready $tag")
        ok++
        if (ok != 2) {
            return
        }

        gestureDetector = GestureDetector(this, GestureListener(this))

        viewModel.groupModel.change.observe(this) {
            if (viewModel.groupModel.tvGroup.value != null) {
                watch()
                menuFragment.update()
            }
        }

        viewModel.channelsOk.observe(this) { ok ->
            if (!ok) {
                return@observe
            }

            val prevGroup = viewModel.groupModel.positionValue
            val tvModel = if (SP.channel > 0) {
                val position = if (SP.channel < viewModel.listModel.size) SP.channel - 1 else 0
                if (SP.channel >= viewModel.listModel.size) {
                    SP.channel = 0
                }
                viewModel.groupModel.getPosition(position)
            } else {
                viewModel.groupModel.getCurrent()
            }

            viewModel.groupModel.setPositionPlaying()
            viewModel.groupModel.getCurrentList()?.setPositionPlaying()
            tvModel?.setReady()

            val currentGroup = viewModel.groupModel.positionValue
            if (currentGroup != prevGroup) {
                menuFragment.updateList(currentGroup)
            }

            viewModel.groupModel.isInLikeMode =
                SP.defaultLike && viewModel.groupModel.positionValue == 0
        }
    }

    private fun watch() {
        viewModel.listModel.forEach { tvModel ->
            tvModel.errInfo.observe(this) {
                if (tvModel.errInfo.value == null) {
                    return@observe
                }

                hideFragment(loadingFragment)
                if (tvModel.errInfo.value.isNullOrEmpty()) {
                    hideFragment(errorFragment)
                    showFragment(playerFragment)
                } else {
                    hideFragment(playerFragment)
                    errorFragment.setMsg(tvModel.errInfo.value.toString())
                    showFragment(errorFragment)
                }
            }

            tvModel.ready.observe(this) {
                if (tvModel.ready.value == null) {
                    return@observe
                }

                hideFragment(errorFragment)
                showFragment(loadingFragment)
                showFragment(playerFragment)
                playerFragment.play(tvModel)
                infoFragment.show(tvModel)
                if (SP.channelNum) {
                    channelFragment.show(tvModel)
                }
            }

            tvModel.like.observe(this) {
                if (tvModel.like.value == null || tvModel.tv.id == -1) {
                    return@observe
                }

                val liked = tvModel.like.value as Boolean
                if (liked) {
                    viewModel.groupModel.getFavoritesList()?.replaceTVModel(tvModel)
                } else {
                    viewModel.groupModel.getFavoritesList()?.removeTVModel(tvModel.tv.id)
                }
                SP.setLike(tvModel.tv.id, liked)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event != null) {
            gestureDetector.onTouchEvent(event)
        }
        return super.onTouchEvent(event)
    }

    private inner class GestureListener(context: Context) :
        GestureDetector.SimpleOnGestureListener() {

        private val displayMetrics = resources.displayMetrics
        private var screenWidth = displayMetrics.widthPixels
        private var screenHeight = displayMetrics.heightPixels
        private val audioManager = context.getSystemService(AUDIO_SERVICE) as AudioManager
        private var maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        private var lastScrollTime: Long = 0
        private var decayFactor: Float = 1.0f

        override fun onDown(e: MotionEvent): Boolean {
            playerFragment.hideVolumeNow()
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            showFragment(menuFragment)
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            showSetting()
            return true
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            val oldX = e1?.rawX ?: 0f
            val oldY = e1?.rawY ?: 0f
            val newX = e2.rawX
            val newY = e2.rawY
            if (oldX > screenWidth / 3 && oldX < screenWidth * 2 / 3 && abs(newX - oldX) < abs(newY - oldY)) {
                if (velocityY > 0) {
                    if ((!menuFragment.isAdded || menuFragment.isHidden) &&
                        (!settingFragment.isAdded || settingFragment.isHidden)
                    ) {
                        prev()
                    }
                }
                if (velocityY < 0) {
                    if ((!menuFragment.isAdded || menuFragment.isHidden) &&
                        (!settingFragment.isAdded || settingFragment.isHidden)
                    ) {
                        next()
                    }
                }
            }
            return super.onFling(e1, e2, velocityX, velocityY)
        }

        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            val oldX = e1?.rawX ?: 0f
            val oldY = e1?.rawY ?: 0f
            val newY = e2.rawY

            if (oldX < screenWidth / 3) {
                val currentTime = System.currentTimeMillis()
                val deltaTime = currentTime - lastScrollTime
                lastScrollTime = currentTime
                decayFactor = 0.01f.coerceAtLeast(decayFactor - 0.03f * deltaTime)
                val delta = ((oldY - newY) * decayFactor * 0.2 / screenHeight).toFloat()
                adjustBrightness(delta)
                decayFactor = 1.0f
                return super.onScroll(e1, e2, distanceX, distanceY)
            }

            if (oldX > screenWidth * 2 / 3 && abs(distanceY) > abs(distanceX)) {
                val currentTime = System.currentTimeMillis()
                val deltaTime = currentTime - lastScrollTime
                lastScrollTime = currentTime
                decayFactor = 0.01f.coerceAtLeast(decayFactor - 0.03f * deltaTime)
                val delta = ((oldY - newY) * maxVolume * decayFactor * 0.2 / screenHeight).toInt()
                adjustVolume(delta)
                decayFactor = 1.0f
                return super.onScroll(e1, e2, distanceX, distanceY)
            }

            return super.onScroll(e1, e2, distanceX, distanceY)
        }

        private fun adjustVolume(deltaVolume: Int) {
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            var newVolume = currentVolume + deltaVolume
            if (newVolume < 0) newVolume = 0
            if (newVolume > maxVolume) newVolume = maxVolume

            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
            playerFragment.setVolumeMax(maxVolume * 100)
            playerFragment.setVolume(newVolume * 100, true)
            playerFragment.showVolume(View.VISIBLE)
        }

        private fun adjustBrightness(deltaBrightness: Float) {
            var brightness = window.attributes.screenBrightness
            brightness += deltaBrightness
            brightness = 0.1f.coerceAtLeast(0.9f.coerceAtMost(brightness))
            window.attributes = window.attributes.apply {
                screenBrightness = brightness
            }
            playerFragment.setVolumeMax(100)
            playerFragment.setVolume((brightness * 100).toInt())
            playerFragment.showVolume(View.VISIBLE)
        }
    }

    fun onPlayEnd() {
        val tvModel = viewModel.groupModel.getCurrent() ?: return
        if (SP.repeatInfo) {
            infoFragment.show(tvModel)
            if (SP.channelNum) {
                channelFragment.show(tvModel)
            }
        }
    }

    fun play(position: Int): Boolean {
        return if (position > -1 && position < viewModel.groupModel.getAllList()!!.size()) {
            val prevGroup = viewModel.groupModel.positionValue
            val tvModel = viewModel.groupModel.getPosition(position)

            tvModel?.setReady()
            viewModel.groupModel.setPositionPlaying()
            viewModel.groupModel.getCurrentList()?.setPositionPlaying()

            val currentGroup = viewModel.groupModel.positionValue
            if (currentGroup != prevGroup) {
                menuFragment.updateList(currentGroup)
            }
            true
        } else {
            R.string.channel_not_exist.showToast()
            false
        }
    }

    fun prev() {
        val prevGroup = viewModel.groupModel.positionValue
        val tvModel = if (SP.defaultLike && viewModel.groupModel.isInLikeMode &&
            viewModel.groupModel.getFavoritesList() != null
        ) {
            viewModel.groupModel.getPrev(true)
        } else {
            viewModel.groupModel.getPrev()
        }

        tvModel?.setReady()
        viewModel.groupModel.setPositionPlaying()
        viewModel.groupModel.getCurrentList()?.setPositionPlaying()

        val currentGroup = viewModel.groupModel.positionValue
        if (currentGroup != prevGroup) {
            menuFragment.updateList(currentGroup)
        }
    }

    fun next() {
        val prevGroup = viewModel.groupModel.positionValue
        val tvModel = if (SP.defaultLike && viewModel.groupModel.isInLikeMode &&
            viewModel.groupModel.getFavoritesList() != null
        ) {
            viewModel.groupModel.getNext(true)
        } else {
            viewModel.groupModel.getNext()
        }

        tvModel?.setReady()
        viewModel.groupModel.setPositionPlaying()
        viewModel.groupModel.getCurrentList()?.setPositionPlaying()

        val currentGroup = viewModel.groupModel.positionValue
        if (currentGroup != prevGroup) {
            menuFragment.updateList(currentGroup)
        }
    }

    private fun showFragment(fragment: Fragment) {
        if (!isSafeToPerformFragmentTransactions) {
            return
        }

        if (!fragment.isAdded) {
            supportFragmentManager.beginTransaction()
                .add(R.id.main_browse_fragment, fragment)
                .commitAllowingStateLoss()
            return
        }

        if (!fragment.isHidden) {
            return
        }

        supportFragmentManager.beginTransaction()
            .show(fragment)
            .commitAllowingStateLoss()
    }

    private fun hideFragment(fragment: Fragment) {
        if (!isSafeToPerformFragmentTransactions || !fragment.isAdded || fragment.isHidden) {
            return
        }

        supportFragmentManager.beginTransaction()
            .hide(fragment)
            .commitAllowingStateLoss()
    }

    fun menuActive() {
        handler.removeCallbacks(hideMenu)
        handler.postDelayed(hideMenu, delayHideMenu)
    }

    private val hideMenu = Runnable {
        if (!isFinishing && !supportFragmentManager.isStateSaved && !menuFragment.isHidden) {
            supportFragmentManager.beginTransaction()
                .hide(menuFragment)
                .commitAllowingStateLoss()
        }
    }

    fun settingActive() {
        handler.removeCallbacks(hideSetting)
        handler.postDelayed(hideSetting, delayHideSetting)
    }

    private val hideSetting = Runnable {
        hideFragment(settingFragment)
        showTimeFragment()
    }

    fun showTimeFragment() {
        if (SP.time) {
            showFragment(timeFragment)
        } else {
            hideFragment(timeFragment)
        }
    }

    private fun showChannel(channel: Int) {
        if (!menuFragment.isHidden || settingFragment.isVisible) {
            return
        }
        channelFragment.show(channel)
    }

    private fun channelUp() {
        if ((!menuFragment.isAdded || menuFragment.isHidden) &&
            (!settingFragment.isAdded || settingFragment.isHidden)
        ) {
            if (SP.channelReversal) next() else prev()
        }
    }

    private fun channelDown() {
        if ((!menuFragment.isAdded || menuFragment.isHidden) &&
            (!settingFragment.isAdded || settingFragment.isHidden)
        ) {
            if (SP.channelReversal) prev() else next()
        }
    }

    private fun back() {
        when {
            menuFragment.isAdded && !menuFragment.isHidden -> hideFragment(menuFragment)
            settingFragment.isAdded && !settingFragment.isHidden -> {
                hideFragment(settingFragment)
                showTimeFragment()
            }
            channelFragment.isAdded && channelFragment.isVisible -> channelFragment.hideSelf()
            doubleBackToExitPressedOnce -> finish()
            else -> {
                doubleBackToExitPressedOnce = true
                R.string.press_again_to_exit.showToast()
                Handler(Looper.getMainLooper()).postDelayed({
                    doubleBackToExitPressedOnce = false
                }, 2000)
            }
        }
    }

    private fun showSetting() {
        if (menuFragment.isAdded && !menuFragment.isHidden) {
            return
        }
        showFragment(settingFragment)
        settingActive()
    }

    fun onKey(keyCode: Int): Boolean {
        Log.d(TAG, "keyCode $keyCode")
        when (keyCode) {
            KeyEvent.KEYCODE_0,
            KeyEvent.KEYCODE_1,
            KeyEvent.KEYCODE_2,
            KeyEvent.KEYCODE_3,
            KeyEvent.KEYCODE_4,
            KeyEvent.KEYCODE_5,
            KeyEvent.KEYCODE_6,
            KeyEvent.KEYCODE_7,
            KeyEvent.KEYCODE_8,
            KeyEvent.KEYCODE_9 -> {
                showChannel(keyCode - 7)
                return true
            }

            KeyEvent.KEYCODE_ESCAPE,
            KeyEvent.KEYCODE_BACK -> {
                back()
                return true
            }

            KeyEvent.KEYCODE_BOOKMARK,
            KeyEvent.KEYCODE_UNKNOWN,
            KeyEvent.KEYCODE_HELP,
            KeyEvent.KEYCODE_SETTINGS,
            KeyEvent.KEYCODE_MENU -> {
                showSetting()
                return true
            }

            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_DPAD_CENTER -> {
                if (channelFragment.isAdded && channelFragment.isVisible) {
                    channelFragment.playNow()
                    return true
                }
                showFragment(menuFragment)
            }

            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_CHANNEL_UP -> channelUp()

            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_CHANNEL_DOWN -> channelDown()

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if ((!menuFragment.isAdded || menuFragment.isHidden) &&
                    (!settingFragment.isAdded || settingFragment.isHidden)
                ) {
                    playerFragment.switchQuality(-1)
                    return true
                }
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if ((!menuFragment.isAdded || menuFragment.isHidden) &&
                    (!settingFragment.isAdded || settingFragment.isHidden)
                ) {
                    playerFragment.switchQuality(1)
                    return true
                }
                showSetting()
            }
        }
        return false
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (onKey(keyCode)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        isSafeToPerformFragmentTransactions = true
        showTimeFragment()
    }

    override fun onPause() {
        super.onPause()
        isSafeToPerformFragmentTransactions = false
    }

    override fun attachBaseContext(base: Context) {
        try {
            val locale = Locale.SIMPLIFIED_CHINESE
            val config = Configuration(base.resources.configuration)
            config.setLocale(locale)
            super.attachBaseContext(base.createConfigurationContext(config))
        } catch (_: Exception) {
            super.attachBaseContext(base)
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
