package org.kvxd.nanocompanion.protocol

import org.kvxd.nanocompanion.protocol.packet.MediaCommandPacket

object PacketFactory {

    fun createPacketFromType(type: PacketType): Packet {
        return when(type) {
            PacketType.MEDIA_COMMAND -> MediaCommandPacket(-1);
        }
    }

}