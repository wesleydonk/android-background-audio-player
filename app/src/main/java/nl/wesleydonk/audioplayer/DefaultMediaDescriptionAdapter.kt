package nl.wesleydonk.audioplayer

import android.app.PendingIntent
import android.graphics.Bitmap
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerNotificationManager

class DefaultMediaDescriptionAdapter(
    private val item: PlayListItem
) : PlayerNotificationManager.MediaDescriptionAdapter {

    override fun getCurrentContentTitle(player: Player): CharSequence {
        return item.title
    }

    override fun createCurrentContentIntent(player: Player): PendingIntent? {
        // TODO define wether you want to return to the app with a pending intent
        return null
    }

    override fun getCurrentContentText(player: Player): CharSequence {
        return item.subtitle
    }

    override fun getCurrentLargeIcon(
        player: Player,
        callback: PlayerNotificationManager.BitmapCallback
    ): Bitmap? {
        return null
    }

}
