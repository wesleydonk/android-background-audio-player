package nl.wesleydonk.audioplayer

import android.app.Notification
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Parcelable
import android.widget.Button
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.ui.PlayerControlView
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import com.google.android.exoplayer2.util.Log
import com.google.android.exoplayer2.util.Util
import kotlinx.parcelize.Parcelize

private const val DEFAULT_PLAYER_NOTIFICATION_ID = 1

private const val EXTRA_PLAYER_COMMAND = "EXTRA_PLAYER_COMMAND"

private const val SOURCE_URL =
    "https://example-link-to.mp3"

// TODO Requires FOREGROUND_SERVICE permission
// TODO Requires Service in Manifest
// TODO Requires ExoPlayer

// TODO Requires VIew
// TODO Requires Service
// TODO Requires NotificationManager of ExoPlayer
// TODO (Optional) Requires custom NotifiationManager

@Parcelize
// TODO specify extra properties that are required to show more meta data
data class PlayListItem(
    val title: String,
    val subtitle: String,
    val sourceUri: String,
) : Parcelable

sealed class PlayerCommand : Parcelable {
    @Parcelize
    object Pause : PlayerCommand()

    @Parcelize
    data class Play(
        val playListItem: PlayListItem
    ) : PlayerCommand()

    @Parcelize
    object Stop : PlayerCommand()

    @Parcelize
    object ToggleMute : PlayerCommand()

    @Parcelize
    data class SeekBack(val seconds: Long) : PlayerCommand()
}

enum class PlayerState {
    Playing,
    Paused,
    Buffering
}

interface AudioPlayer {

    fun getPlayer(): ExoPlayer
    fun start(sourceUri: String)
    fun stop()
    fun pause()
    fun toggleMute()
    fun seekBack(seconds: Long)
}

class ExoAudioPlayer(context: Context) : AudioPlayer {

    private val exoPlayer = SimpleExoPlayer.Builder(context)
        .build()

    override fun getPlayer(): ExoPlayer {
        return exoPlayer
    }

    override fun start(sourceUri: String) {
        exoPlayer.setMediaItem(MediaItem.fromUri(sourceUri))
        exoPlayer.prepare()
        exoPlayer.play()
    }

    override fun stop() {
        exoPlayer.stop()
        exoPlayer.release()
    }

    override fun pause() {
        if (!exoPlayer.isPlaying) {
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
    }

    override fun toggleMute() {
        exoPlayer.isDeviceMuted = !exoPlayer.isDeviceMuted
    }

    override fun seekBack(seconds: Long) {
        exoPlayer.seekTo(exoPlayer.currentPosition - (seconds * 1000))
    }
}

class PlayerService : Service() {

    private val binder = PlayerServiceBinder()

    private lateinit var playerNotificationManager: PlayerNotificationManager
    private lateinit var audioPlayer: AudioPlayer
    private lateinit var playListItem: PlayListItem

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    // TODO #4 Whenever created, the audioPlayer is set up
    override fun onCreate() {
        super.onCreate()
        audioPlayer = ExoAudioPlayer(baseContext)
    }

    // TODO #5 We can receive commands to do something with audio
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (val command = intent?.getParcelableExtra<PlayerCommand>(EXTRA_PLAYER_COMMAND)) {
            is PlayerCommand.Play -> {
                this.playListItem = command.playListItem
                audioPlayer.start(playListItem.sourceUri)

                // TODO #6 For Android Q and later, we need to show a notification
                createPlayerNotification(playListItem)
            }
            is PlayerCommand.SeekBack -> audioPlayer.seekBack(command.seconds)
            PlayerCommand.ToggleMute -> audioPlayer.toggleMute()
            PlayerCommand.Pause -> audioPlayer.pause()
            PlayerCommand.Stop -> audioPlayer.stop()
        }
        return START_STICKY
    }

    // TODO #7 Whenever a forground service is created, we need to call startForeground(notificationId, notification) within 5 seconds, otherwise you will see errors.
    //  ExoPlayer lib contains a PlayerNotificationManager which has callbacks to make our lives easier.
    private fun createPlayerNotification(item: PlayListItem) {
        playerNotificationManager = PlayerNotificationManager.createWithNotificationChannel(
            applicationContext,
            "channel_id",
            R.string.channel_name,
            R.string.channel_description,
            DEFAULT_PLAYER_NOTIFICATION_ID,
            DefaultMediaDescriptionAdapter(item),
            object : PlayerNotificationManager.NotificationListener {
                override fun onNotificationStarted(
                    notificationId: Int,
                    notification: Notification
                ) {
                    startForeground(notificationId, notification)
                }

                override fun onNotificationPosted(
                    notificationId: Int,
                    notification: Notification,
                    ongoing: Boolean
                ) {
                    if (ongoing) {
                        startForeground(notificationId, notification);
                    } else {
                        // TODO #8 A foreground service cannot be dismissed by the user
                        stopForeground(/* removeNotification= */ false);
                    }
                }

                override fun onNotificationCancelled(notificationId: Int) {
                    stopSelf()
                }
            }
        ).apply {
            setUseNextActionInCompactView(false)
            setUseNextAction(false)
            setUsePreviousActionInCompactView(false)
            setUsePreviousAction(false)
            setUseNavigationActions(false)
            setUseStopAction(false)

            // TODO #9 Subclass playerNotificationManager.getActions to provide a custom list of actions, I reckon we only want to show the play/pause button for now
        }
        playerNotificationManager.setPlayer(audioPlayer.getPlayer())
    }

    inner class PlayerServiceBinder : Binder() {

        fun getPlayer(): ExoPlayer = audioPlayer.getPlayer()
    }

    companion object {

        fun newIntent(context: Context, command: PlayerCommand?): Intent {
            return Intent(context, PlayerService::class.java).apply {
                putExtra(EXTRA_PLAYER_COMMAND, command)
            }
        }
    }
}

class PlayerActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerControlView

    private val connection = object : ServiceConnection {

        // TODO #8 If the service is started and connected, we can request the exo player to fetch the player, but also the play list item if we want to.
        override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
            val playerBinder = binder as PlayerService.PlayerServiceBinder
            playerView.player = playerBinder.getPlayer().also { player ->
                player.setOnPlayStateChanged(stateChangedListener)
            }
            // TODO #10 Register extr acallbacks for the player
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            // TODO #9 We should alter the UI to ensure no player is visible
        }
    }

    private val stateChangedListener: (PlayerState) -> Unit = { state ->
        Log.d("PLAYER_STATE", "Transered to state: $state")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        playerView = findViewById<PlayerControlView>(R.id.player_view)

        val playButton = findViewById<Button>(R.id.play)
        playButton.setOnClickListener {

            // TODO #1 first we need to start the service
            val intent = PlayerService.newIntent(this, null)
            bindService(intent, connection, Service.BIND_AUTO_CREATE)

            // TODO #2 Additionally we need to start playing it
            val item = PlayListItem("Title", "subtitle", SOURCE_URL)
            sendCommand(PlayerCommand.Play(item))
        }

        val pauseButton = findViewById<Button>(R.id.pause)
        pauseButton.setOnClickListener {
            sendCommand(PlayerCommand.Pause)
        }

        val muteButton = findViewById<Button>(R.id.mute)
        muteButton.setOnClickListener {
            sendCommand(PlayerCommand.ToggleMute)
        }

        val seekBack = findViewById<Button>(R.id.seekbar)
        seekBack.setOnClickListener {
            sendCommand(PlayerCommand.SeekBack(10))
        }
    }

    override fun onDestroy() {
        sendCommand(PlayerCommand.Stop)
        super.onDestroy()
    }

    private fun sendCommand(command: PlayerCommand) {
        // TODO #3 We are required to start it with a foreground service
        Util.startForegroundService(this, PlayerService.newIntent(this, command))
    }
}

@MainThread
fun ExoPlayer.setOnPlayStateChanged(stateChangedListener: (PlayerState) -> Unit) {
    addListener(object : Player.EventListener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (playbackState != Player.STATE_READY) return
            val state = when {
                isPlaying -> PlayerState.Playing
                else -> PlayerState.Paused
            }
            stateChangedListener(state)
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (playbackState == Player.STATE_BUFFERING) {
                stateChangedListener(PlayerState.Buffering)
            }
        }
    })
}
