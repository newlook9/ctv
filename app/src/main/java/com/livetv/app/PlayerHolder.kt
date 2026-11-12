package com.livetv.app

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter

// One shared player for the whole app, so radio keeps playing when the screen is closed.
object PlayerHolder {
    @Volatile
    private var player: ExoPlayer? = null

    fun get(ctx: Context): ExoPlayer {
        val c = ctx.applicationContext
        return player ?: synchronized(this) {
            player ?: build(c).also { player = it }
        }
    }

    private fun build(c: Context): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(25000, 60000, 3000, 6000)
            .build()
        // Low starting estimate: channels open quickly on slow internet, then quality rises.
        val meter = DefaultBandwidthMeter.Builder(c)
            .setInitialBitrateEstimate(1_500_000L)
            .build()
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent(Config.UA)
        val attrs = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        return ExoPlayer.Builder(c)
            .setLoadControl(loadControl)
            .setBandwidthMeter(meter)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(c, http)))
            .setAudioAttributes(attrs, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }
}
