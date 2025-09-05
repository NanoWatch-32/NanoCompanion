package org.kvxd.nanocompanion.protocol.packet

import org.kvxd.nanocompanion.protocol.Packet
import org.kvxd.nanocompanion.protocol.PacketType
import org.kvxd.nanocompanion.protocol.ReadBuffer
import org.kvxd.nanocompanion.protocol.WriteBuffer

class TimeSyncPacket(
    val timestamp: Long
) : Packet {

    override val packetType: PacketType = PacketType.TIME_SYNC

    override fun encode(buffer: WriteBuffer) {
        buffer.writeLong(timestamp)
    }

    override fun decode(buffer: ReadBuffer): TimeSyncPacket {
        return TimeSyncPacket(
            buffer.readLong()
        )
    }

}