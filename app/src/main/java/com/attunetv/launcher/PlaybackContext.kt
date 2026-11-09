package com.attunetv.launcher

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.service.notification.NotificationListenerService

/** Optional Android-authorized media access. No notification content is read or forwarded. */
class PlaybackMetadataService : NotificationListenerService()

data class PlaybackContext(val title:String?, val episode:String?, val positionMs:Long?) {
    companion object {
        fun read(context:Context,pkg:String):PlaybackContext {
            val unknown=PlaybackContext(null,null,null)
            if(pkg !in StreamingSupport.candidates)return unknown
            return try {
                val manager=context.getSystemService(MediaSessionManager::class.java)
                val sessions=manager.getActiveSessions(ComponentName(context,PlaybackMetadataService::class.java))
                val matching=sessions.filter {it.packageName==pkg}
                val controller=matching.firstOrNull {it.playbackState?.state==PlaybackState.STATE_PLAYING} ?: matching.firstOrNull() ?: return unknown
                val metadata=controller.metadata
                val title=ChatSecurity.usableTitle(metadata?.getString(MediaMetadata.METADATA_KEY_TITLE))
                    ?: ChatSecurity.usableTitle(metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE))
                val subtitle=metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)?.take(120)
                val state=controller.playbackState
                val position=state?.position?.takeIf {it>=0}?.let {position ->
                    if(state.state==PlaybackState.STATE_PLAYING)position+((SystemClock.elapsedRealtime()-state.lastPositionUpdateTime).coerceAtLeast(0)*state.playbackSpeed).toLong()
                    else position
                }
                PlaybackContext(title,if(title!=null)subtitle else null,position)
            } catch (_:SecurityException) {unknown} catch (_:RuntimeException) {unknown}
        }
    }
}
