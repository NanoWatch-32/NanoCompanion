package org.kvxd.nanocompanion.protocol.packet

import org.kvxd.nanocompanion.protocol.Packet
import org.kvxd.nanocompanion.protocol.PacketType
import org.kvxd.nanocompanion.protocol.ReadBuffer
import org.kvxd.nanocompanion.protocol.WriteBuffer

class MediaCommandPacket(
    val command: Int
) : Packet {

    override val packetType = PacketType.MEDIA_COMMAND

    override fun encode(buffer: WriteBuffer) {
        buffer.writeInt(command)
    }

    override fun decode(buffer: ReadBuffer): MediaCommandPacket {
        return MediaCommandPacket(
            buffer.readInt()
        )
    }

}