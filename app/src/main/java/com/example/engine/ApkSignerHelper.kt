package com.example.engine

import android.content.Context
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.X509Certificate
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object ApkSignerHelper {

    fun signApk(
        context: Context,
        unsignedZipFile: File,
        outputSignedApkFile: File
    ) {
        // Load debug keystore from assets
        val ks = KeyStore.getInstance("PKCS12")
        context.assets.open("debug.keystore").use { stream ->
            ks.load(stream, "android".toCharArray())
        }
        val alias = "androiddebugkey"
        val password = "android".toCharArray()
        val privateKey = ks.getKey(alias, password) as PrivateKey
        val cert = ks.getCertificate(alias) as X509Certificate

        val md = MessageDigest.getInstance("SHA-256")

        // 1. Build MANIFEST.MF
        val manifestSb = StringBuilder()
        manifestSb.append("Manifest-Version: 1.0\r\n")
        manifestSb.append("Created-By: 1.0 (APK Creator)\r\n\r\n")

        val fileDigests = LinkedHashMap<String, String>()

        ZipFile(unsignedZipFile).use { zin ->
            val entries = zin.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory || entry.name.startsWith("META-INF/")) continue

                val bytes = zin.getInputStream(entry).use { it.readBytes() }
                md.reset()
                val digest = Base64.encodeToString(md.digest(bytes), Base64.NO_WRAP)
                fileDigests[entry.name] = digest

                manifestSb.append("Name: ").append(entry.name).append("\r\n")
                manifestSb.append("SHA-256-Digest: ").append(digest).append("\r\n\r\n")
            }
        }

        val manifestBytes = manifestSb.toString().toByteArray(Charsets.UTF_8)
        md.reset()
        val manifestDigest = Base64.encodeToString(md.digest(manifestBytes), Base64.NO_WRAP)

        // 2. Build CERT.SF
        val sfSb = StringBuilder()
        sfSb.append("Signature-Version: 1.0\r\n")
        sfSb.append("Created-By: 1.0 (APK Creator)\r\n")
        sfSb.append("SHA-256-Digest-Manifest: ").append(manifestDigest).append("\r\n\r\n")

        for ((name, digest) in fileDigests) {
            val entryHeader = "Name: $name\r\nSHA-256-Digest: $digest\r\n\r\n"
            md.reset()
            val headerDigest = Base64.encodeToString(md.digest(entryHeader.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
            sfSb.append("Name: ").append(name).append("\r\n")
            sfSb.append("SHA-256-Digest: ").append(headerDigest).append("\r\n\r\n")
        }

        val sfBytes = sfSb.toString().toByteArray(Charsets.UTF_8)

        // 3. Sign CERT.SF
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(privateKey)
        signature.update(sfBytes)
        val signedSfBytes = signature.sign()

        // 4. Build PKCS#7 / CMS SignedData block
        val pkcs7Block = buildPkcs7Block(cert, signedSfBytes)

        // 5. Write final signed APK
        if (outputSignedApkFile.exists()) outputSignedApkFile.delete()

        ZipOutputStream(FileOutputStream(outputSignedApkFile)).use { zout ->
            // Write signature files first
            writeZipEntry(zout, "META-INF/MANIFEST.MF", manifestBytes)
            writeZipEntry(zout, "META-INF/CERT.SF", sfBytes)
            writeZipEntry(zout, "META-INF/CERT.RSA", pkcs7Block)

            // Write all original files
            ZipFile(unsignedZipFile).use { zin ->
                val entries = zin.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory || entry.name.startsWith("META-INF/")) continue
                    val bytes = zin.getInputStream(entry).use { it.readBytes() }
                    writeZipEntry(zout, entry.name, bytes)
                }
            }
        }
    }

    private fun writeZipEntry(zout: ZipOutputStream, name: String, bytes: ByteArray) {
        val entry = ZipEntry(name)
        zout.putNextEntry(entry)
        zout.write(bytes)
        zout.closeEntry()
    }

    private fun buildPkcs7Block(cert: X509Certificate, signatureBytes: ByteArray): ByteArray {
        val certDer = cert.encoded
        val issuerDer = cert.issuerX500Principal.encoded
        val serialBytes = cert.serialNumber.toByteArray()

        // Common OIDs
        val oidSha256 = byteArrayOf(0x06, 0x09, 0x60.toByte(), 0x86.toByte(), 0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x01)
        val oidRsa = byteArrayOf(0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x01)
        val oidData = byteArrayOf(0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x07, 0x01)
        val oidSignedData = byteArrayOf(0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x07, 0x02)

        val nullParams = byteArrayOf(0x05, 0x00)
        val digestAlg = derSeq(oidSha256 + nullParams)
        val digestAlgs = derSet(digestAlg)
        val encapContent = derSeq(oidData)

        val certsBlock = derTagged(0xA0, certDer)

        val issuerAndSerial = derSeq(issuerDer + derInteger(serialBytes))
        val digestEncryptionAlg = derSeq(oidRsa + nullParams)
        val encDigest = derOctetString(signatureBytes)

        val signerInfo = derSeq(
            derInteger(1) +
            issuerAndSerial +
            digestAlg +
            digestEncryptionAlg +
            encDigest
        )
        val signerInfos = derSet(signerInfo)

        val signedData = derSeq(
            derInteger(1) +
            digestAlgs +
            encapContent +
            certsBlock +
            signerInfos
        )

        return derSeq(oidSignedData + derTagged(0xA0, signedData))
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

    private fun derSeq(content: ByteArray): ByteArray {
        return byteArrayOf(0x30) + derLength(content.size) + content
    }

    private fun derSet(content: ByteArray): ByteArray {
        return byteArrayOf(0x31) + derLength(content.size) + content
    }

    private fun derTagged(tag: Int, content: ByteArray): ByteArray {
        return byteArrayOf(tag.toByte()) + derLength(content.size) + content
    }

    private fun derOctetString(content: ByteArray): ByteArray {
        return byteArrayOf(0x04) + derLength(content.size) + content
    }

    private fun derInteger(value: Int): ByteArray {
        return byteArrayOf(0x02, 0x01, value.toByte())
    }

    private fun derInteger(bytes: ByteArray): ByteArray {
        return byteArrayOf(0x02) + derLength(bytes.size) + bytes
    }
}
