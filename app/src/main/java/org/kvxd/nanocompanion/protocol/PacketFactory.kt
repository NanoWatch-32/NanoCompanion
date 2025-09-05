package org.kvxd.nanocompanion.protocol

import org.kvxd.nanocompanion.protocol.packet.MediaCommandPacket
import org.kvxd.nanocompanion.protocol.packet.MediaInfoPacket
import org.kvxd.nanocompanion.protocol.packet.TimeSyncPacket

object PacketFactory {

    fun createPacketFromType(type: PacketType): Packet {
        return when(type) {
            PacketType.MEDIA_COMMAND -> MediaCommandPacket(-1)
            PacketType.MEDIA_INFO -> MediaInfoPacket("", "", "", -1, -1, false)
            PacketType.TIME_SYNC -> TimeSyncPacket(0)
        }
    }

}