package com.example.call

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import java.util.TreeMap
import java.util.zip.CRC32
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Generates Agora RTC Access Token (v006) directly on the client device.
 * Used for peer-to-peer voice calls between couple members without requiring
 * external cloud functions.
 */
object AgoraTokenBuilder {

    const val DEFAULT_APP_ID = "c7accf9b9c8849bd853556fe5f332c47"
    const val DEFAULT_APP_CERTIFICATE = "fa4db0151d904743a928c7df26d868ca"

    private const val VERSION = "006"
    private const val PRIVILEGE_JOIN_CHANNEL = 1
    private const val PRIVILEGE_PUBLISH_AUDIO_STREAM = 2

    fun buildToken(
        appId: String = DEFAULT_APP_ID,
        appCertificate: String = DEFAULT_APP_CERTIFICATE,
        channelName: String,
        uid: Int,
        ttlSeconds: Int = 86400
    ): String {
        val now = (System.currentTimeMillis() / 1000L).toInt()
        val expireTimestamp = now + ttlSeconds
        val salt = SecureRandom().nextInt()
        return buildTokenInternal(
            appId = appId.trim(),
            appCertificate = appCertificate.trim(),
            channelName = channelName.trim(),
            uid = uid,
            salt = salt,
            ts = now + 24 * 3600,
            privileges = mapOf(
                PRIVILEGE_JOIN_CHANNEL to expireTimestamp,
                PRIVILEGE_PUBLISH_AUDIO_STREAM to expireTimestamp
            )
        )
    }

    internal fun buildTokenInternal(
        appId: String,
        appCertificate: String,
        channelName: String,
        uid: Int,
        salt: Int,
        ts: Int,
        privileges: Map<Int, Int>
    ): String {
        val uidStr = if (uid == 0) "" else uid.toString()

        // 1. Pack Message
        val messageBytes = packMessage(salt, ts, privileges)

        // 2. Compute toSign = appId + channelName + uidStr + messageBytes
        val toSignStream = ByteArrayOutputStream()
        toSignStream.write(appId.toByteArray(Charsets.UTF_8))
        toSignStream.write(channelName.toByteArray(Charsets.UTF_8))
        toSignStream.write(uidStr.toByteArray(Charsets.UTF_8))
        toSignStream.write(messageBytes)
        val toSign = toSignStream.toByteArray()

        // 3. HMAC-SHA256 signature
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(appCertificate.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val signature = mac.doFinal(toSign)

        // 4. Compute CRC32
        val crcChannel = CRC32().apply { update(channelName.toByteArray(Charsets.UTF_8)) }.value.toInt()
        val crcUid = CRC32().apply { update(uidStr.toByteArray(Charsets.UTF_8)) }.value.toInt()

        // 5. Pack Content
        val contentBytes = packContent(signature, crcChannel, crcUid, messageBytes)

        // 6. Base64 encode (supporting both Android runtime and JVM unit test environment)
        val base64Content = try {
            android.util.Base64.encodeToString(contentBytes, android.util.Base64.NO_WRAP)
        } catch (_: Throwable) {
            java.util.Base64.getEncoder().encodeToString(contentBytes)
        }
        return VERSION + appId + base64Content
    }

    private fun packMessage(salt: Int, ts: Int, privileges: Map<Int, Int>): ByteArray {
        val stream = ByteArrayOutputStream()
        writeUInt32LE(stream, salt)
        writeUInt32LE(stream, ts)

        val sorted = TreeMap(privileges)
        writeUInt16LE(stream, sorted.size)
        for ((privilege, expireTs) in sorted) {
            writeUInt16LE(stream, privilege)
            writeUInt32LE(stream, expireTs)
        }
        return stream.toByteArray()
    }

    private fun packContent(
        signature: ByteArray,
        crcChannel: Int,
        crcUid: Int,
        messageBytes: ByteArray
    ): ByteArray {
        val stream = ByteArrayOutputStream()
        writeBytes(stream, signature)
        writeUInt32LE(stream, crcChannel)
        writeUInt32LE(stream, crcUid)
        writeBytes(stream, messageBytes)
        return stream.toByteArray()
    }

    private fun writeUInt16LE(stream: ByteArrayOutputStream, value: Int) {
        val buf = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(value.toShort())
        stream.write(buf.array())
    }

    private fun writeUInt32LE(stream: ByteArrayOutputStream, value: Int) {
        val buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(value)
        stream.write(buf.array())
    }

    private fun writeBytes(stream: ByteArrayOutputStream, bytes: ByteArray) {
        writeUInt16LE(stream, bytes.size)
        stream.write(bytes)
    }
}
