package org.kvxd.nanocompanion.protocol

class ReadBuffer(private val data: ByteArray) {
    private var position = 0

    fun readByte(): Byte {
        checkBounds(1)
        return data[position++]
    }

    fun readInt(): Int {
        checkBounds(4)
        val value = (data[position].toInt() and 0xFF) or
                ((data[position + 1].toInt() and 0xFF) shl 8) or
                ((data[position + 2].toInt() and 0xFF) shl 16) or
                ((data[position + 3].toInt() and 0xFF) shl 24)
        position += 4
        return value
    }

    fun readFloat(): Float {
        val intValue = readInt()
        return java.lang.Float.intBitsToFloat(intValue)
    }

    fun readString(): String {
        val length = readInt()
        checkBounds(length)
        val str = String(data, position, length, Charsets.UTF_8)
        position += length
        return str
    }

    fun readImage(): ByteArray {
        val length = readInt()
        checkBounds(length)
        val image = data.copyOfRange(position, position + length)
        position += length
        return image
    }

    fun hasMore(): Boolean = position < data.size

    private fun checkBounds(required: Int) {
        if (position + required > data.size) {
            throw IndexOutOfBoundsException("Read beyond buffer bounds")
        }
    }
}