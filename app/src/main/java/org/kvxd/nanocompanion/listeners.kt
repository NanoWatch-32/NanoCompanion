package org.kvxd.nanocompanion

import io.github.kvxd.ksignal.filterIsInstance
import org.kvxd.nanocompanion.ble.BLEController
import org.kvxd.nanocompanion.protocol.packet.MediaCommandPacket
import org.kvxd.nanocompanion.protocol.packet.MediaInfoPacket
import org.kvxd.nanocompanion.protocol.packet.TimeSyncPacket
import java.util.TimeZone

private fun syncTime(bleController: BLEController) {

    bleController.connectedSignal.connect {
        val utcMillis = System.currentTimeMillis()
        val tz = TimeZone.getDefault()
        val millis = utcMillis + tz.getOffset(utcMillis)

        bleController.sendPacket(TimeSyncPacket(millis))
    }
}

private fun mediaCommands(bleController: BLEController) {
    bleController.packetReceivedSignal.filterIsInstance<MediaCommandPacket>()
        .connect { packet ->
            when (packet.command) {
                0 -> MediaControl.togglePlaying()
                1 -> MediaControl.next()
                2 -> MediaControl.previous()
            }
        }

    bleController.connectedSignal.connect {
        val info = MediaControl.getMediaInfo()
        if (info == null) return@connect

        val packet = MediaInfoPacket(
            info.title ?: "Unknown",
            info.artist ?: "Unknown",
            info.album ?: "Unknown",
            info.duration ?: -1,
            info.position,
            info.isPlaying
        )
        bleController.sendPacket(packet)
    }
}

fun registerListeners(bleController: BLEController) {
    syncTime(bleController)
    mediaCommands(bleController)
}