package org.lblocker

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Plays the bundled recitations one after another (shuffled) until [release] is called. */
class QuranPlayer(private val ctx: Context) {
    /** The verse currently being recited, so the UI can show its text. */
    var current by mutableStateOf<Verse?>(null)
        private set

    private val handler = Handler(Looper.getMainLooper())
    private val audioManager = ctx.getSystemService(AudioManager::class.java)
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(attrs)
        .build()

    private var player: MediaPlayer? = null
    private var queue: List<Verse> = emptyList()
    private var index = 0
    private var released = false

    fun start() {
        queue = Content.verses(ctx).shuffled()
        if (queue.isEmpty()) return
        audioManager.requestAudioFocus(focus)
        playNext()
    }

    private fun playNext() {
        if (released || queue.isEmpty()) return
        val verse = queue[index % queue.size]
        index++
        current = verse

        val id = ctx.resources.getIdentifier(verse.raw, "raw", ctx.packageName)
        if (id == 0) {
            Log.w(TAG, "Missing audio for ${verse.ref}")
            handler.postDelayed({ playNext() }, 500)
            return
        }
        try {
            val afd = ctx.resources.openRawResourceFd(id)
            val p = MediaPlayer()
            p.setAudioAttributes(attrs)
            p.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            p.setOnCompletionListener {
                it.release()
                if (player === it) player = null
                handler.postDelayed({ playNext() }, 1500)
            }
            p.prepare()
            p.start()
            player = p
        } catch (e: Exception) {
            Log.w(TAG, "Could not play ${verse.ref}", e)
        }
    }

    fun release() {
        released = true
        handler.removeCallbacksAndMessages(null)
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        audioManager.abandonAudioFocusRequest(focus)
    }

    private companion object {
        const val TAG = "QuranPlayer"
    }
}
