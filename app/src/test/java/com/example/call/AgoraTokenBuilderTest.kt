package com.example.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgoraTokenBuilderTest {

    @Test
    fun testBuildTokenDeterministic() {
        val appId = "c7accf9b9c8849bd853556fe5f332c47"
        val appCert = "fa4db0151d904743a928c7df26d868ca"
        val channel = "test_channel"
        val uid = 1001
        val salt = 12345
        val ts = 1700000000
        val privileges = mapOf(1 to 1700003600, 2 to 1700003600)

        val token = AgoraTokenBuilder.buildTokenInternal(
            appId = appId,
            appCertificate = appCert,
            channelName = channel,
            uid = uid,
            salt = salt,
            ts = ts,
            privileges = privileges
        )

        val expected = "006c7accf9b9c8849bd853556fe5f332c47IACrpQHPDXFrwwfb4E+SeTyy8BrV4oInweTEIIs2r88t5Y9auH6BkyDDFgA5MAAAAPFTZQIAAQAQ/1NlAgAQ/1Nl"
        assertEquals(expected, token)
    }

    @Test
    fun testBuildTokenLive() {
        val token = AgoraTokenBuilder.buildToken(
            channelName = "live_channel",
            uid = 1001
        )
        assertTrue(token.startsWith("006" + AgoraTokenBuilder.DEFAULT_APP_ID))
        assertTrue(token.length > 50)
    }
}
