package org.kvxd.nanocompanion

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

object MediaControl {

    private var mediaController: MediaController? = null
    private var packetSender: MediaPacketSender? = null

    private var scope: CoroutineScope? = null
    // android sends multiple notifications on single state changes
    // so a debounced flow is required in order not to overwhelm the esp32
    private val mediaChangeFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private var lastSentMediaInfo: MediaInfo? = null

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

    fun initialize(context: Context, sender: MediaPacketSender): Boolean {
        val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val cn = ComponentName(context, NotificationService::class.java)
        val sessions = manager.getActiveSessions(cn)
        mediaController = sessions.firstOrNull()
        packetSender = sender

        if (scope == null) {
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            observeMediaChanges()
        }

        return mediaController != null
    }

    fun notifyMediaChanged() {
        mediaChangeFlow.tryEmit(Unit)
    }

    @OptIn(FlowPreview::class)
    private fun observeMediaChanges() {
        scope?.launch {
            mediaChangeFlow
                .collect {
                    val info = getMediaInfo() ?: return@collect
                    if (info != lastSentMediaInfo) {
                        packetSender?.sendMediaInfoPacket(info)
                        lastSentMediaInfo = info
                    }
                }
        }
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

    @SuppressLint("NewApi")
    fun togglePlaying() {
        if (mediaController?.playbackState?.isActive == true)
            mediaController?.transportControls?.stop()
        else
            mediaController?.transportControls?.play()
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


class NotificationService : NotificationListenerService() {

    private var mediaSessionManager: MediaSessionManager? = null
    private var controllers: List<MediaController>? = null

    private val mediaCallback = object : MediaController.Callback() {

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            super.onPlaybackStateChanged(state)
            MediaControl.notifyMediaChanged()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            super.onMetadataChanged(metadata)
            MediaControl.notifyMediaChanged()
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()

        mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        val componentName = ComponentName(this, NotificationService::class.java)
        controllers = mediaSessionManager?.getActiveSessions(componentName)

        controllers?.forEach {
            it.registerCallback(mediaCallback)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()

        controllers?.forEach {
            it.unregisterCallback(mediaCallback)
        }

        controllers = null
    }
}
