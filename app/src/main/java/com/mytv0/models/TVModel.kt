package com.mytv0.models

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.mytv0.SP
import com.mytv0.data.TV

class TVModel(var tv: TV) : ViewModel() {
    var retryTimes = 0
    var retryMaxTimes = 10

    private var groupIndexValue = 0
    val groupIndex: Int
        get() = if (SP.showAllChannels || groupIndexValue == 0) groupIndexValue else groupIndexValue - 1

    fun setGroupIndex(index: Int) {
        groupIndexValue = index
    }

    fun getGroupIndexInAll(): Int {
        return groupIndexValue
    }

    var listIndex = 0

    private val _errInfo = MutableLiveData<String>()
    val errInfo: LiveData<String>
        get() = _errInfo

    fun setErrInfo(info: String) {
        _errInfo.value = info
    }

    fun getVideoUrl(): String? {
        return tv.uris.firstOrNull()
    }

    private val _like = MutableLiveData<Boolean>()
    val like: LiveData<Boolean>
        get() = _like

    fun setLike(liked: Boolean) {
        _like.value = liked
    }

    private val _ready = MutableLiveData<Boolean>()
    val ready: LiveData<Boolean>
        get() = _ready

    fun setReady(retry: Boolean = false) {
        if (!retry) {
            setErrInfo("")
            retryTimes = 0
        }
        _ready.value = true
    }

    fun update(t: TV) {
        tv = t
    }

    init {
        _like.value = SP.getLike(tv.id)
    }
}
