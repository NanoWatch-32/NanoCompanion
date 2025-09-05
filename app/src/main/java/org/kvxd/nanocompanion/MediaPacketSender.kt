package org.kvxd.nanocompanion

interface MediaPacketSender {
    fun sendMediaInfoPacket(info: MediaControl.MediaInfo)
}
