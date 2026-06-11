package com.mytv0

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.util.Locale

class MyTVApplication : Application() {

    companion object {
        private const val TAG = "MyTVApplication"
        private lateinit var instance: MyTVApplication

        @JvmStatic
        fun getInstance(): MyTVApplication {
            return instance
        }
    }

    private var width = 0
    private var height = 0
    private var shouldWidth = 0
    private var shouldHeight = 0
    private var ratio = 1.0
    private var density = 2.0f
    private var scale = 1.0f

    lateinit var imageHelper:ImageHelper

    override fun onCreate() {
        super.onCreate()
        instance = this

        val displayMetrics = resources.displayMetrics

        if (displayMetrics.heightPixels > displayMetrics.widthPixels) {
            width = displayMetrics.heightPixels
            height = displayMetrics.widthPixels
        } else {
            width = displayMetrics.widthPixels
            height = displayMetrics.heightPixels
        }

        density = displayMetrics.density
        scale = resources.configuration.fontScale

        if ((width.toDouble() / height) < (16.0 / 9.0)) {
            ratio = width * 2 / 1920.0 / density
            shouldWidth = width
            shouldHeight = (width * 9.0 / 16.0).toInt()
        } else {
            ratio = height * 2 / 1080.0 / density
            shouldHeight = height
            shouldWidth = (height * 16.0 / 9.0).toInt()
        }

        Thread.setDefaultUncaughtExceptionHandler(MyTVExceptionHandler(this))

        imageHelper = ImageHelper(this)
    }

    fun toast(message: CharSequence = "", duration: Int = Toast.LENGTH_SHORT) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, message, duration).show()
        }
    }

    fun shouldWidthPx(): Int {
        return shouldWidth
    }

    fun shouldHeightPx(): Int {
        return shouldHeight
    }

    fun dp2Px(dp: Int): Int {
        return (dp * ratio * density + 0.5f).toInt()
    }

    fun px2Px(px: Int): Int {
        return (px * ratio + 0.5f).toInt()
    }

    fun px2PxFont(px: Float): Float {
        return (px * ratio / scale).toFloat()
    }

    fun sp2Px(sp: Float): Float {
        return (sp * ratio * scale).toFloat()
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
}
