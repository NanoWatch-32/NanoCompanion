package org.kvxd.nanocompanion


import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import android.service.notification.NotificationListenerService

object MediaControl {

    private var mediaController: MediaController? = null

    fun hasPermissions(context: Context): Boolean {
        val cn = ComponentName(context, NotificationService::class.java)
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )

        return enabledListeners?.contains(cn.flattenToString()) ?: false
    }

    fun requestPermission(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(intent)
    }

    fun initialize(context: Context): Boolean {
        val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val cn = ComponentName(context, NotificationService::class.java)
        val sessions = manager.getActiveSessions(cn)
        mediaController = sessions.firstOrNull()
        return mediaController != null
    }

    fun getMediaInfo(): MediaInfo? {
        val controller = mediaController ?: return null
        val metadata = controller.metadata ?: return null
        val playbackState = controller.playbackState

        return MediaInfo(
            title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE),
            artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST),
            album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM),
            duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION),
            position = playbackState?.position ?: 0L,
            isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING
        )
    }

    fun play() {
        mediaController?.transportControls?.play()
    }

    fun pause() {
        mediaController?.transportControls?.pause()
    }

    fun next() {
        mediaController?.transportControls?.skipToNext()
    }

    fun previous() {
        mediaController?.transportControls?.skipToPrevious()
    }

    data class MediaInfo(
        val title: String?,
        val artist: String?,
        val album: String?,
        val duration: Long?,
        val position: Long,
        val isPlaying: Boolean
    )

}

class NotificationService : NotificationListenerService()