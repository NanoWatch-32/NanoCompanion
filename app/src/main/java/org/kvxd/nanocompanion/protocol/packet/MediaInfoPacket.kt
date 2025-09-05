package org.kvxd.nanocompanion.protocol.packet

import org.kvxd.nanocompanion.protocol.Packet
import org.kvxd.nanocompanion.protocol.PacketType
import org.kvxd.nanocompanion.protocol.ReadBuffer
import org.kvxd.nanocompanion.protocol.WriteBuffer

class MediaInfoPacket(
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val position: Long,
    val isPlaying: Boolean
) : Packet {

    override val packetType: PacketType = PacketType.MEDIA_INFO

    override fun encode(buffer: WriteBuffer) {
        buffer.writeString(title)
        buffer.writeString(artist)
        buffer.writeString(album)
        buffer.writeLong(duration)
        buffer.writeLong(position)
        buffer.writeBoolean(isPlaying)
    }

    override fun decode(buffer: ReadBuffer): MediaInfoPacket {
        return MediaInfoPacket(
            buffer.readString(),
            buffer.readString(),
            buffer.readString(),
            buffer.readLong(),
            buffer.readLong(),
            buffer.readBoolean()
        )
    }

}