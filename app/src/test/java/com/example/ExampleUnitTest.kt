package com.example

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

class ExampleUnitTest {
    @Test
    fun testSelfSignedCertificateGeneration() {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val keyPair = kpg.generateKeyPair()

        val cert = generateTestCert(keyPair)
        assertNotNull(cert)
        assertEquals("X.509", cert.type)
        // Verify signature with its own public key
        cert.verify(keyPair.public)
        println("Generated certificate verified successfully: ${cert.subjectX500Principal}")
    }

    @Test
    fun testApkSignerLibrary() {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val keyPair = kpg.generateKeyPair()
        val cert = generateTestCert(keyPair)

        val manifestFile = if (java.io.File("src/main/assets/base_manifest.bin").exists()) {
            java.io.File("src/main/assets/base_manifest.bin")
        } else {
            java.io.File("app/src/main/assets/base_manifest.bin")
        }
        val manifestBytes = manifestFile.readBytes()
        val inputZip = java.io.File.createTempFile("unsigned", ".zip")
        java.util.zip.ZipOutputStream(java.io.FileOutputStream(inputZip)).use { zos ->
            zos.putNextEntry(java.util.zip.ZipEntry("AndroidManifest.xml"))
            zos.write(manifestBytes)
            zos.closeEntry()
            zos.putNextEntry(java.util.zip.ZipEntry("hello.txt"))
            zos.write("Hello World".toByteArray())
            zos.closeEntry()
        }

        val outputApk = java.io.File.createTempFile("signed", ".apk")
        val signerConfig = com.android.apksig.ApkSigner.SignerConfig.Builder(
            "CERT",
            keyPair.private,
            listOf(cert)
        ).build()

        com.android.apksig.ApkSigner.Builder(listOf(signerConfig))
            .setInputApk(inputZip)
            .setOutputApk(outputApk)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .build()
            .sign()

        assertTrue(outputApk.exists())
        assertTrue(outputApk.length() > 0)
        println("Signed APK size with v1, v2, v3: ${outputApk.length()} bytes")
    }

    private fun generateTestCert(keyPair: java.security.KeyPair): X509Certificate {
        val oidSha256WithRsa = byteArrayOf(
            0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x0B
        )
        val nullParams = byteArrayOf(0x05, 0x00)
        val sigAlgId = derSeq(oidSha256WithRsa + nullParams)

        val version = derTagged(0xA0, derInteger(2))
        val serial = derInteger(java.math.BigInteger.valueOf(System.currentTimeMillis()).toByteArray())

        val cAttr = derSet(derSeq(byteArrayOf(0x06, 0x03, 0x55, 0x04, 0x06) + byteArrayOf(0x13, 0x02, 0x55, 0x53)))
        val oBytes = "Android".toByteArray(Charsets.UTF_8)
        val oAttr = derSet(derSeq(byteArrayOf(0x06, 0x03, 0x55, 0x04, 0x0A) + byteArrayOf(0x0C, oBytes.size.toByte()) + oBytes))
        val cnBytes = "Android Debug".toByteArray(Charsets.UTF_8)
        val cnAttr = derSet(derSeq(byteArrayOf(0x06, 0x03, 0x55, 0x04, 0x03) + byteArrayOf(0x0C, cnBytes.size.toByte()) + cnBytes))
        val nameSeq = derSeq(cAttr + oAttr + cnAttr)

        val notBefore = byteArrayOf(0x17, 13) + "200101000000Z".toByteArray(Charsets.US_ASCII)
        val notAfter = byteArrayOf(0x17, 13) + "450101000000Z".toByteArray(Charsets.US_ASCII)
        val validity = derSeq(notBefore + notAfter)

        val publicKeyInfo = keyPair.public.encoded
        val tbsCertificate = derSeq(version + serial + sigAlgId + nameSeq + validity + nameSeq + publicKeyInfo)

        val sig = Signature.getInstance("SHA256withRSA")
        sig.initSign(keyPair.private)
        sig.update(tbsCertificate)
        val signatureBytes = sig.sign()

        val signatureValue = derBitString(signatureBytes)
        val certDer = derSeq(tbsCertificate + sigAlgId + signatureValue)

        val certFactory = CertificateFactory.getInstance("X.509")
        return certFactory.generateCertificate(ByteArrayInputStream(certDer)) as X509Certificate
    }

    private fun derLength(len: Int): ByteArray {
        return when {
            len < 128 -> byteArrayOf(len.toByte())
            len < 256 -> byteArrayOf(0x81.toByte(), len.toByte())
            len < 65536 -> byteArrayOf(0x82.toByte(), (len shr 8).toByte(), (len and 0xFF).toByte())
            else -> byteArrayOf(
                0x83.toByte(),
                (len shr 16).toByte(),
                ((len shr 8) and 0xFF).toByte(),
                (len and 0xFF).toByte()
            )
        }
    }

    private fun derSeq(content: ByteArray): ByteArray = byteArrayOf(0x30) + derLength(content.size) + content
    private fun derSet(content: ByteArray): ByteArray = byteArrayOf(0x31) + derLength(content.size) + content
    private fun derTagged(tag: Int, content: ByteArray): ByteArray = byteArrayOf(tag.toByte()) + derLength(content.size) + content
    private fun derInteger(value: Int): ByteArray = byteArrayOf(0x02, 0x01, value.toByte())
    private fun derInteger(bytes: ByteArray): ByteArray = byteArrayOf(0x02) + derLength(bytes.size) + bytes
    private fun derBitString(bytes: ByteArray): ByteArray = byteArrayOf(0x03) + derLength(bytes.size + 1) + byteArrayOf(0x00) + bytes
}

