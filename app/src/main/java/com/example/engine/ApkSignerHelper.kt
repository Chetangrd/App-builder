package com.example.engine

import android.content.Context
import android.util.Base64
import android.util.Log
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object ApkSignerHelper {

    private const val TAG = "ApkSignerHelper"

    data class SigningCredentials(
        val privateKey: PrivateKey,
        val certificate: X509Certificate
    )

    @Volatile
    private var cachedCredentials: SigningCredentials? = null

    @Synchronized
    fun getSigningCredentials(context: Context): SigningCredentials {
        cachedCredentials?.let { return it }

        val password = "android".toCharArray()
        val defaultAlias = "androiddebugkey"

        // 1. Try loading from assets ("debug.keystore")
        val fromAssets = tryLoadFromAssets(context, password, defaultAlias)
        if (fromAssets != null) {
            Log.d(TAG, "Successfully loaded keystore from assets")
            cachedCredentials = fromAssets
            return fromAssets
        }

        // 2. Try loading from internal files directory
        val internalKeystoreFile = File(context.filesDir, "debug.keystore")
        val fromInternal = tryLoadFromFile(internalKeystoreFile, password, defaultAlias)
        if (fromInternal != null) {
            Log.d(TAG, "Successfully loaded keystore from internal storage")
            cachedCredentials = fromInternal
            return fromInternal
        }

        // 3. Dynamically generate an RSA-2048 KeyPair and self-signed X.509 certificate on device
        Log.i(TAG, "Generating dynamic signing credentials on device...")
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val keyPair = kpg.generateKeyPair()
        val cert = generateSelfSignedCertificate(keyPair)

        val creds = SigningCredentials(keyPair.private, cert)

        // Attempt to save to internal storage for reuse
        try {
            val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null)
                setKeyEntry(defaultAlias, keyPair.private, password, arrayOf(cert))
            }
            FileOutputStream(internalKeystoreFile).use { fos ->
                ks.store(fos, password)
            }
            Log.d(TAG, "Cached dynamically generated keystore to ${internalKeystoreFile.absolutePath}")
        } catch (e: Exception) {
            Log.w(TAG, "Could not persist keystore to internal storage (non-fatal)", e)
        }

        cachedCredentials = creds
        return creds
    }

    private fun tryLoadFromAssets(context: Context, password: CharArray, defaultAlias: String): SigningCredentials? {
        val formats = listOf("PKCS12", KeyStore.getDefaultType())
        for (format in formats) {
            try {
                val ks = KeyStore.getInstance(format)
                context.assets.open("debug.keystore").use { stream ->
                    ks.load(stream, password)
                }
                val creds = extractCredentials(ks, password, defaultAlias)
                if (creds != null) return creds
            } catch (ignored: Exception) {
                // Try next format
            }
        }
        return null
    }

    private fun tryLoadFromFile(file: File, password: CharArray, defaultAlias: String): SigningCredentials? {
        if (!file.exists() || file.length() == 0L) return null
        val formats = listOf(KeyStore.getDefaultType(), "PKCS12")
        for (format in formats) {
            try {
                val ks = KeyStore.getInstance(format)
                FileInputStream(file).use { stream ->
                    ks.load(stream, password)
                }
                val creds = extractCredentials(ks, password, defaultAlias)
                if (creds != null) return creds
            } catch (ignored: Exception) {
                // Try next
            }
        }
        return null
    }

    private fun extractCredentials(ks: KeyStore, password: CharArray, defaultAlias: String): SigningCredentials? {
        val targetAlias = if (ks.containsAlias(defaultAlias)) {
            defaultAlias
        } else {
            ks.aliases().asSequence().firstOrNull { ks.isKeyEntry(it) } ?: return null
        }

        val key = ks.getKey(targetAlias, password) as? PrivateKey ?: return null
        val cert = ks.getCertificate(targetAlias) as? X509Certificate ?: return null
        return SigningCredentials(key, cert)
    }

    fun generateSelfSignedCertificate(
        keyPair: KeyPair,
        validityYears: Int = 25
    ): X509Certificate {
        val oidSha256WithRsa = byteArrayOf(
            0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x0B
        )
        val nullParams = byteArrayOf(0x05, 0x00)
        val sigAlgId = derSeq(oidSha256WithRsa + nullParams)

        // Version: v3 (0xA0 [0] EXPLICIT INTEGER 2)
        val version = derTagged(0xA0, derInteger(2))

        // Serial Number
        val serialNumberBytes = BigInteger.valueOf(System.currentTimeMillis()).toByteArray()
        val serial = derInteger(serialNumberBytes)

        // Issuer & Subject Name: CN=APK Creator, O=Android, C=US
        val cAttr = derSet(derSeq(byteArrayOf(0x06, 0x03, 0x55, 0x04, 0x06) + byteArrayOf(0x13, 0x02, 0x55, 0x53)))
        val oBytes = "Android".toByteArray(Charsets.UTF_8)
        val oAttr = derSet(derSeq(byteArrayOf(0x06, 0x03, 0x55, 0x04, 0x0A) + byteArrayOf(0x0C, oBytes.size.toByte()) + oBytes))
        val cnBytes = "Android Debug".toByteArray(Charsets.UTF_8)
        val cnAttr = derSet(derSeq(byteArrayOf(0x06, 0x03, 0x55, 0x04, 0x03) + byteArrayOf(0x0C, cnBytes.size.toByte()) + cnBytes))
        val nameSeq = derSeq(cAttr + oAttr + cnAttr)

        // Validity: 2020 to 2045+
        val notBefore = byteArrayOf(0x17, 13) + "200101000000Z".toByteArray(Charsets.US_ASCII)
        val notAfter = byteArrayOf(0x17, 13) + "450101000000Z".toByteArray(Charsets.US_ASCII)
        val validity = derSeq(notBefore + notAfter)

        // SubjectPublicKeyInfo (pre-encoded by RSAPublicKey)
        val publicKeyInfo = keyPair.public.encoded

        // TBSCertificate structure
        val tbsCertificate = derSeq(version + serial + sigAlgId + nameSeq + validity + nameSeq + publicKeyInfo)

        // Sign TBSCertificate using SHA256withRSA
        val sig = Signature.getInstance("SHA256withRSA")
        sig.initSign(keyPair.private)
        sig.update(tbsCertificate)
        val signatureBytes = sig.sign()

        // signatureValue as BIT STRING
        val signatureValue = derBitString(signatureBytes)

        // Certificate = SEQUENCE { tbsCertificate, signatureAlgorithm, signatureValue }
        val certDer = derSeq(tbsCertificate + sigAlgId + signatureValue)

        val certFactory = CertificateFactory.getInstance("X.509")
        return certFactory.generateCertificate(ByteArrayInputStream(certDer)) as X509Certificate
    }

    fun signApk(
        context: Context,
        unsignedZipFile: File,
        outputSignedApkFile: File
    ) {
        val credentials = getSigningCredentials(context)
        val privateKey = credentials.privateKey
        val cert = credentials.certificate

        try {
            Log.i(TAG, "Signing APK using Android ApkSigner (v1, v2, v3 schemes)...")
            val signerConfig = com.android.apksig.ApkSigner.SignerConfig.Builder(
                "CERT",
                privateKey,
                listOf(cert)
            ).build()

            val apkSigner = com.android.apksig.ApkSigner.Builder(listOf(signerConfig))
                .setInputApk(unsignedZipFile)
                .setOutputApk(outputSignedApkFile)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .setOtherSignersSignaturesPreserved(false)
                .build()

            apkSigner.sign()
            Log.i(TAG, "APK successfully signed with v1, v2, and v3 schemes: ${outputSignedApkFile.length()} bytes")
            return
        } catch (e: Throwable) {
            Log.w(TAG, "ApkSigner encountered issue, falling back to manual JAR signer: ${e.message}", e)
        }

        signApkManual(context, unsignedZipFile, outputSignedApkFile)
    }

    private fun signApkManual(
        context: Context,
        unsignedZipFile: File,
        outputSignedApkFile: File
    ) {
        val credentials = getSigningCredentials(context)
        val privateKey = credentials.privateKey
        val cert = credentials.certificate

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

    private fun derBitString(bytes: ByteArray): ByteArray {
        return byteArrayOf(0x03) + derLength(bytes.size + 1) + byteArrayOf(0x00) + bytes
    }
}
