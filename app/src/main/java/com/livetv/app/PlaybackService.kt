package com.livetv.app

import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

// Keeps audio playing outside the app and shows the notification with play/pause controls.
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        session = MediaSession.Builder(this, PlayerHolder.get(this)).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = PlayerHolder.get(this)
        if (!p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }
}
