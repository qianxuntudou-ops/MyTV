package com.mytv0

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.marginBottom
import androidx.core.view.marginEnd
import androidx.core.view.marginTop
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.mytv0.databinding.SettingBinding

class SettingFragment : Fragment() {

    private var _binding: SettingBinding? = null
    private val binding get() = _binding!!
    private val installPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val context = context ?: return@registerForActivityResult
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                context.packageManager.canRequestPackageInstalls()
            ) {
                updateManager.checkAndUpdate()
            } else {
                R.string.authorization_failed.showToast()
            }
        }

    private lateinit var updateManager: UpdateManager
    private lateinit var viewModel: MainViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val application = requireActivity().applicationContext as MyTVApplication
        val context = requireContext()
        val mainActivity = activity as MainActivity

        _binding = SettingBinding.inflate(inflater, container, false)

        binding.versionName.text = "v${context.appVersionName}"
        binding.version.text = "CCTV"

        binding.switchChannelReversal.isChecked = SP.channelReversal
        binding.switchChannelReversal.setOnCheckedChangeListener { _, isChecked ->
            SP.channelReversal = isChecked
            mainActivity.settingActive()
        }

        binding.switchChannelNum.isChecked = SP.channelNum
        binding.switchChannelNum.setOnCheckedChangeListener { _, isChecked ->
            SP.channelNum = isChecked
            mainActivity.settingActive()
        }

        binding.switchTime.isChecked = SP.time
        binding.switchTime.setOnCheckedChangeListener { _, isChecked ->
            SP.time = isChecked
            mainActivity.settingActive()
        }

        binding.switchBootStartup.isChecked = SP.bootStartup
        binding.switchBootStartup.setOnCheckedChangeListener { _, isChecked ->
            SP.bootStartup = isChecked
            mainActivity.settingActive()
        }

        binding.switchDefaultLike.isChecked = SP.defaultLike
        binding.switchDefaultLike.setOnCheckedChangeListener { _, isChecked ->
            SP.defaultLike = isChecked
            mainActivity.settingActive()
        }

        binding.switchShowAllChannels.isChecked = SP.showAllChannels
        binding.switchCompactMenu.isChecked = SP.compactMenu
        binding.switchCompactMenu.setOnCheckedChangeListener { _, isChecked ->
            SP.compactMenu = isChecked
            mainActivity.updateMenuSize()
            mainActivity.settingActive()
        }

        binding.switchDisplaySeconds.isChecked = SP.displaySeconds

        binding.checkVersion.setOnClickListener {
            requestInstallPermissions()
            mainActivity.settingActive()
        }

        binding.clear.setOnClickListener {
            resetDefaults()
            mainActivity.settingActive()
        }

        binding.setting.setOnClickListener {
            hideSelf()
        }

        binding.exit.setOnClickListener {
            requireActivity().finishAffinity()
        }

        val txtTextSize = application.px2PxFont(binding.versionName.textSize)

        binding.content.layoutParams.width = application.px2Px(binding.content.layoutParams.width)
        binding.content.setPadding(
            application.px2Px(binding.content.paddingLeft),
            application.px2Px(binding.content.paddingTop),
            application.px2Px(binding.content.paddingRight),
            application.px2Px(binding.content.paddingBottom)
        )

        binding.name.textSize = application.px2PxFont(binding.name.textSize)
        binding.version.textSize = txtTextSize
        val layoutParamsVersion = binding.version.layoutParams as ViewGroup.MarginLayoutParams
        layoutParamsVersion.topMargin = application.px2Px(binding.version.marginTop)
        layoutParamsVersion.bottomMargin = application.px2Px(binding.version.marginBottom)
        binding.version.layoutParams = layoutParamsVersion
        binding.versionName.textSize = txtTextSize

        val btnWidth = application.px2Px(binding.clear.layoutParams.width)
        val btnLayoutParams = binding.clear.layoutParams as ViewGroup.MarginLayoutParams
        btnLayoutParams.marginEnd = application.px2Px(binding.clear.marginEnd)

        for (button in listOf(binding.clear, binding.checkVersion, binding.exit)) {
            button.layoutParams.width = btnWidth
            button.textSize = txtTextSize
            button.layoutParams = btnLayoutParams
            button.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    button.background = ColorDrawable(ContextCompat.getColor(context, R.color.focus))
                    button.setTextColor(ContextCompat.getColor(context, R.color.white))
                } else {
                    button.background = ColorDrawable(ContextCompat.getColor(context, R.color.description_blur))
                    button.setTextColor(ContextCompat.getColor(context, R.color.blur))
                }
            }
        }

        val textSizeSwitch = application.px2PxFont(binding.switchChannelReversal.textSize)
        val layoutParamsSwitch = binding.switchChannelReversal.layoutParams as ViewGroup.MarginLayoutParams
        layoutParamsSwitch.topMargin = application.px2Px(binding.switchChannelReversal.marginTop)

        for (switchView in listOf(
            binding.switchChannelReversal,
            binding.switchChannelNum,
            binding.switchTime,
            binding.switchDisplaySeconds,
            binding.switchBootStartup,
            binding.switchDefaultLike,
            binding.switchShowAllChannels,
            binding.switchCompactMenu,
        )) {
            switchView.textSize = textSizeSwitch
            switchView.layoutParams = layoutParamsSwitch
            switchView.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    switchView.setTextColor(ContextCompat.getColor(context, R.color.focus))
                } else {
                    switchView.setTextColor(ContextCompat.getColor(context, R.color.title_blur))
                }
            }
        }

        updateManager = UpdateManager(context, context.appVersionCode)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = requireActivity()
        val mainActivity = activity as MainActivity
        viewModel = ViewModelProvider(context)[MainViewModel::class.java]

        binding.switchDisplaySeconds.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setDisplaySeconds(isChecked)
            mainActivity.settingActive()
        }

        binding.switchShowAllChannels.setOnCheckedChangeListener { _, isChecked ->
            SP.showAllChannels = isChecked
            viewModel.groupModel.setChange()
            mainActivity.settingActive()
        }

        binding.clear.requestFocus()
    }

    private fun resetDefaults() {
        SP.channelNum = SP.DEFAULT_CHANNEL_NUM
        SP.channelReversal = SP.DEFAULT_CHANNEL_REVERSAL
        SP.time = SP.DEFAULT_TIME
        SP.bootStartup = SP.DEFAULT_BOOT_STARTUP
        SP.defaultLike = false
        SP.showAllChannels = SP.DEFAULT_SHOW_ALL_CHANNELS
        SP.compactMenu = SP.DEFAULT_COMPACT_MENU
        SP.displaySeconds = SP.DEFAULT_DISPLAY_SECONDS
        SP.channel = SP.DEFAULT_CHANNEL
        SP.position = SP.DEFAULT_POSITION
        SP.positionGroup = SP.DEFAULT_POSITION_GROUP
        SP.repeatInfo = SP.DEFAULT_REPEAT_INFO
        SP.cctvQuality = SP.DEFAULT_CCTV_QUALITY
        SP.deleteLike()

        viewModel.setDisplaySeconds(SP.DEFAULT_DISPLAY_SECONDS)
        viewModel.reset()

        R.string.config_restored.showToast()
    }

    private fun hideSelf() {
        requireActivity().supportFragmentManager.beginTransaction()
            .hide(this)
            .commitAllowingStateLoss()
        (activity as MainActivity).showTimeFragment()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (_binding != null && !hidden) {
            binding.clear.requestFocus()
        }
    }

    private fun requestInstallPermissions() {
        val context = requireContext()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            )
            installPermissionLauncher.launch(intent)
        } else {
            updateManager.checkAndUpdate()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
