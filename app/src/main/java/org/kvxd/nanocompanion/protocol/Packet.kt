package org.kvxd.nanocompanion.protocol

interface Packet {

    val packetType: PacketType

    fun encode(buffer: WriteBuffer)
    fun decode(buffer: ReadBuffer): Packet

}