package org.kvxd.nanocompanion.protocol

enum class PacketType(val value: UByte) {

    MEDIA_COMMAND(0u),
    MEDIA_INFO(1u),
    TIME_SYNC(2u),

}