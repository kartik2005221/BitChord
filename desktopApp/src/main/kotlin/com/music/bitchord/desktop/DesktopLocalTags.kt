package com.music.bitchord.desktop

import java.nio.file.Path
import java.util.concurrent.TimeUnit
import org.bytedeco.ffmpeg.avformat.AVIOInterruptCB
import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.global.avformat.avformat_alloc_context
import org.bytedeco.ffmpeg.global.avformat.avformat_close_input
import org.bytedeco.ffmpeg.global.avformat.avformat_open_input
import org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_AUDIO
import org.bytedeco.ffmpeg.global.avutil.av_dict_free
import org.bytedeco.ffmpeg.global.avutil.av_dict_get
import org.bytedeco.ffmpeg.global.avutil.av_dict_set
import org.bytedeco.javacpp.Pointer

internal data class DesktopLocalTags(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val track: Int? = null,
    val disc: Int? = null,
) {
    companion object {
        /**
         * Read local container headers using the demuxer already used for playback.
         * No audio is decoded and no other protocol or playlist demuxer is allowed.
         */
        fun read(path: Path): DesktopLocalTags = runCatching {
            val format = avformat_alloc_context()
            if (format.isNull) return@runCatching DesktopLocalTags()
            val options = AVDictionary(null)
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(500)
            val interrupt = object : AVIOInterruptCB.Callback_Pointer() {
                override fun call(opaque: Pointer?): Int =
                    if (System.nanoTime() - deadline >= 0 || Thread.currentThread().isInterrupted) 1 else 0
            }
            format.interrupt_callback().callback(interrupt)
            try {
                av_dict_set(options, "protocol_whitelist", "file", 0)
                av_dict_set(options, "format_whitelist", "aac,flac,mp3,mov,ogg,wav,matroska,webm", 0)
                av_dict_set(options, "formatprobesize", "262144", 0)
                val status = avformat_open_input(format, "file:${path.toAbsolutePath()}", null, options)
                if (status < 0) return@runCatching DesktopLocalTags()
                val container = format.metadata()
                val audio = (0 until format.nb_streams()).asSequence()
                    .map(format::streams)
                    .firstOrNull { it.codecpar().codec_type() == AVMEDIA_TYPE_AUDIO }
                    ?.metadata()
                fun tag(name: String): String? = container.tag(name) ?: audio.tag(name)
                DesktopLocalTags(
                    title = tag("title"),
                    artist = tag("artist"),
                    album = tag("album"),
                    albumArtist = tag("album_artist") ?: tag("albumartist"),
                    track = (tag("track") ?: tag("tracknumber")).tagNumber(),
                    disc = (tag("disc") ?: tag("discnumber")).tagNumber(),
                )
            } finally {
                av_dict_free(options)
                if (!format.isNull) avformat_close_input(format)
                interrupt.close()
            }
        }.getOrElse { DesktopLocalTags() }

        private fun AVDictionary?.tag(name: String): String? =
            this?.takeUnless { it.isNull }?.let { av_dict_get(it, name, null, 0) }
                ?.takeUnless { it.isNull }?.value()?.getString()?.trim()?.takeIf(String::isNotEmpty)

        private fun String?.tagNumber(): Int? =
            this?.substringBefore('/')?.trim()?.toIntOrNull()?.takeIf { it > 0 }
    }
}
