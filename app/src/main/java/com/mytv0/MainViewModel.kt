package com.mytv0

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.mytv0.data.TV
import com.mytv0.models.TVGroupModel
import com.mytv0.models.TVListModel
import com.mytv0.models.TVModel

class MainViewModel : ViewModel() {
    private var timeFormat = if (SP.displaySeconds) "HH:mm:ss" else "HH:mm"

    var listModel: List<TVModel> = emptyList()
    val groupModel = TVGroupModel()

    private val _channelsOk = MutableLiveData<Boolean>()
    val channelsOk: LiveData<Boolean>
        get() = _channelsOk

    fun init(context: Context) {
        if (groupModel.tvGroupValue.isNotEmpty()) {
            _channelsOk.value = true
            return
        }

        groupModel.addTVListModel(TVListModel(context.getString(R.string.my_favorites), 0))
        groupModel.addTVListModel(TVListModel(context.getString(R.string.all_channels), 1))

        loadBuiltinCctvChannels()
        _channelsOk.value = true
    }

    fun setDisplaySeconds(displaySeconds: Boolean) {
        timeFormat = if (displaySeconds) "HH:mm:ss" else "HH:mm"
        SP.displaySeconds = displaySeconds
    }

    fun getTime(): String {
        return Utils.getDateFormat(timeFormat)
    }

    fun reset() {
        loadBuiltinCctvChannels()
        groupModel.initPosition()
        groupModel.setChange()
        _channelsOk.value = true
    }

    private fun loadBuiltinCctvChannels() {
        val list = CctvChannels.list()
        Log.i(TAG, "load built-in cctv channels ${list.size}")
        tvList2Channels(list)
    }

    private fun tvList2Channels(list: List<TV>) {
        groupModel.initTVGroup()

        val map: MutableMap<String, MutableList<TVModel>> = mutableMapOf()
        for (tv in list) {
            map.getOrPut(tv.group) { mutableListOf() }.add(TVModel(tv))
        }

        val listModelNew = mutableListOf<TVModel>()
        var groupIndex = 2
        var id = 0
        for ((name, groupList) in map) {
            val tvListModel = TVListModel(name.ifEmpty { "未知" }, groupIndex)
            for ((listIndex, tvModel) in groupList.withIndex()) {
                tvModel.tv.id = id
                tvModel.setLike(SP.getLike(id))
                tvModel.setGroupIndex(groupIndex)
                tvModel.listIndex = listIndex
                tvListModel.addTVModel(tvModel)
                listModelNew.add(tvModel)
                id++
            }
            groupModel.addTVListModel(tvListModel)
            groupIndex++
        }

        listModel = listModelNew
        groupModel.getAllList()?.setTVListModel(listModel)
        groupModel.initPosition()
        groupModel.setChange()
    }

    companion object {
        private const val TAG = "MainViewModel"
    }
}
