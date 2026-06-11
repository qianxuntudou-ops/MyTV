package com.mytv0

import com.mytv0.data.TV

object CctvChannels {
    private const val GROUP = "央视频道"

    private data class ChannelDef(
        val number: Int,
        val suffix: String,
        val title: String,
    )

    private val channels = listOf(
        ChannelDef(1, "cctv1", "CCTV-1 综合"),
        ChannelDef(2, "cctv2", "CCTV-2 财经"),
        ChannelDef(3, "cctv3", "CCTV-3 综艺"),
        ChannelDef(4, "cctv4", "CCTV-4 中文国际"),
        ChannelDef(5, "cctv5", "CCTV-5 体育"),
        ChannelDef(6, "cctv6", "CCTV-6 电影"),
        ChannelDef(7, "cctv7", "CCTV-7 国防军事"),
        ChannelDef(8, "cctv8", "CCTV-8 电视剧"),
        ChannelDef(9, "cctvjilu", "CCTV-9 纪录"),
        ChannelDef(10, "cctv10", "CCTV-10 科教"),
        ChannelDef(11, "cctv11", "CCTV-11 戏曲"),
        ChannelDef(12, "cctv12", "CCTV-12 社会与法"),
        ChannelDef(13, "cctv13", "CCTV-13 新闻"),
        ChannelDef(14, "cctvchild", "CCTV-14 少儿"),
        ChannelDef(15, "cctv15", "CCTV-15 音乐"),
        ChannelDef(16, "cctv16", "CCTV-16 奥林匹克"),
        ChannelDef(17, "cctv17", "CCTV-17 农业农村"),
    )

    fun list(): List<TV> {
        return channels.map { channel ->
            TV(
                name = channel.suffix,
                title = channel.title,
                description = "央视官方直播",
                uris = listOf(channel.suffix),
                headers = emptyMap(),
                group = GROUP,
                number = channel.number,
            )
        }
    }
}

