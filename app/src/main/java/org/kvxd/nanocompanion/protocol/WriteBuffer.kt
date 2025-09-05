package org.kvxd.nanocompanion.protocol

import java.io.ByteArrayOutputStream

class WriteBuffer {

    private val data = ByteArrayOutputStream()

    fun writeByte(value: Byte) = data.write(value.toInt())

    fun writeInt(value: Int) {
        data.write(value and 0xFF)
        data.write((value shr 8) and 0xFF)
        data.write((value shr 16) and 0xFF)
        data.write((value shr 24) and 0xFF)
    }

    fun writeFloat(value: Float) {
        val intValue = java.lang.Float.floatToIntBits(value)
        writeInt(intValue)
    }

    fun writeLong(value: Long) {
        data.write((value and 0xFF).toInt())
        data.write(((value shr 8) and 0xFF).toInt())
        data.write(((value shr 16) and 0xFF).toInt())
        data.write(((value shr 24) and 0xFF).toInt())
        data.write(((value shr 32) and 0xFF).toInt())
        data.write(((value shr 40) and 0xFF).toInt())
        data.write(((value shr 48) and 0xFF).toInt())
        data.write(((value shr 56) and 0xFF).toInt())
    }

    fun writeBoolean(value: Boolean) {
        data.write(if (value) 1 else 0)
    }

    fun writeString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        data.write(bytes)
    }

    fun writeImage(value: ByteArray) {
        writeInt(value.size)
        data.write(value)
    }

    fun toByteArray(): ByteArray = data.toByteArray()

    fun clear() {
        data.reset()
    }

}