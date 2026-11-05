package com.perez.signer;

import android.content.Context;
import android.net.Uri;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.UnrecoverableEntryException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import javax.crypto.EncryptedPrivateKeyInfo;

import com.android.apksig.ApkSigner;

public class ApkSigning {

    public static boolean performZipAlign(File targetApk, File outApk) throws IOException, InvalidZipException {
        try (RandomAccessFile raf = new RandomAccessFile(targetApk, "r");
             FileOutputStream fos = new FileOutputStream(outApk)) {
            ZipAlign.alignZip(raf, fos);
        } catch (Exception e) {
            outApk.delete();
            throw e;
        }
        return verifyZipAlign(outApk);
    }

    public static boolean verifyZipAlign(File targetApk) {
        try (RandomAccessFile raf = new RandomAccessFile(targetApk, "r")) {
            return ZipAlign.verifyZip(raf);
        } catch (IOException | InvalidZipException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static class SignerKeys {
        final PrivateKey privateKey;
        final List<X509Certificate> certificates;

        SignerKeys(PrivateKey privateKey, List<X509Certificate> certificates) {
            this.privateKey = privateKey;
            this.certificates = certificates;
        }
    }

    /**
     * Resolves signing credentials from any keystore format (JKS, PKCS12, BKS).
     */
    private static SignerKeys loadSignerKeys(InputStream keyStream, String storePassword,
                                             String targetAlias, String keyPassword) throws Exception {
        byte[] data = readAllBytes(keyStream);

        // JKS format detection (Magic: 0xFEEDFEED)
        if (isJks(data)) {
            return parseJks(data, storePassword, targetAlias, keyPassword);
        }

        // Fall back to standard Android providers (PKCS12, BKS)
        try {
            return loadStandardKeyStore(data, storePassword, targetAlias, keyPassword);
        } catch (Exception e) {
            // Last resort: attempt JKS parser in case magic probe missed
            try {
                return parseJks(data, storePassword, targetAlias, keyPassword);
            } catch (Exception ignored) {
            }
            throw e;
        }
    }

    private static boolean isJks(byte[] data) {
        return data.length >= 4
                && (data[0] & 0xFF) == 0xFE
                && (data[1] & 0xFF) == 0xED
                && (data[2] & 0xFF) == 0xFE
                && (data[3] & 0xFF) == 0xED;
    }

    /**
     * Pure-Java JKS parser.
     */
    private static SignerKeys parseJks(byte[] data, String storePassword,
                                       String targetAlias, String keyPassword) throws Exception {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
        int magic = dis.readInt();
        if (magic != 0xFEEDFEED) {
            throw new IOException("Invalid JKS magic: 0x" + Integer.toHexString(magic));
        }

        int version = dis.readInt();
        int count = dis.readInt();

        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        char[] storePwChars = storePassword != null ? storePassword.toCharArray() : new char[0];
        char[] keyPwChars = (keyPassword != null && !keyPassword.isEmpty())
                ? keyPassword.toCharArray()
                : storePwChars;

        SignerKeys matchedKeys = null;
        SignerKeys firstFoundKeys = null;
        List<String> foundAliases = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            int tag = dis.readInt();
            String alias = dis.readUTF();
            long timestamp = dis.readLong();
            foundAliases.add(alias);

            if (tag == 1) { // KeyEntry
                int keyLen = dis.readInt();
                byte[] encrKey = new byte[keyLen];
                dis.readFully(encrKey);

                int chainLen = dis.readInt();
                List<X509Certificate> chain = new ArrayList<>(chainLen);
                for (int c = 0; c < chainLen; c++) {
                    String certType = dis.readUTF();
                    int certLen = dis.readInt();
                    byte[] certData = new byte[certLen];
                    dis.readFully(certData);
                    Certificate cert = certFactory.generateCertificate(new ByteArrayInputStream(certData));
                    if (cert instanceof X509Certificate) {
                        chain.add((X509Certificate) cert);
                    }
                }

                boolean isTarget = (targetAlias != null && targetAlias.equalsIgnoreCase(alias))
                        || (targetAlias == null || targetAlias.isEmpty());

                if (isTarget || firstFoundKeys == null) {
                    byte[] plainKey = null;
                    try {
                        plainKey = decryptJksPrivateKey(encrKey, keyPwChars);
                    } catch (Exception e1) {
                        // Attempt store password if key password differs
                        if (storePwChars.length > 0 && !Arrays.equals(keyPwChars, storePwChars)) {
                            try {
                                plainKey = decryptJksPrivateKey(encrKey, storePwChars);
                            } catch (Exception ignored) {
                            }
                        }
                        if (plainKey == null && isTarget) {
                            throw e1;
                        }
                    }

                    if (plainKey != null && !chain.isEmpty()) {
                        String keyAlgorithm = chain.get(0).getPublicKey().getAlgorithm();
                        PrivateKey privateKey = decodePrivateKey(plainKey, keyAlgorithm);
                        SignerKeys keys = new SignerKeys(privateKey, chain);
                        if (firstFoundKeys == null) {
                            firstFoundKeys = keys;
                        }
                        if (targetAlias != null && targetAlias.equalsIgnoreCase(alias)) {
                            matchedKeys = keys;
                            break;
                        }
                    }
                }
            } else if (tag == 2) { // TrustedCertEntry
                String certType = dis.readUTF();
                int certLen = dis.readInt();
                byte[] certData = new byte[certLen];
                dis.readFully(certData);
            } else {
                throw new IOException("Unrecognized JKS tag: " + tag);
            }
        }

        if (matchedKeys != null) {
            return matchedKeys;
        }
        if ((targetAlias == null || targetAlias.isEmpty()) && firstFoundKeys != null) {
            return firstFoundKeys;
        }

        throw new KeyStoreException("Alias '" + targetAlias + "' not found in JKS. Available aliases: " + foundAliases);
    }

    /**
     * Decrypts Sun JKS private key by unwrapping EncryptedPrivateKeyInfo ASN.1 payload.
     */
    private static byte[] decryptJksPrivateKey(byte[] encrKey, char[] password) throws Exception {
        // Unwrap ASN.1 EncryptedPrivateKeyInfo structure to extract the raw encrypted octets
        byte[] encrData;
        try {
            EncryptedPrivateKeyInfo epki = new EncryptedPrivateKeyInfo(encrKey);
            encrData = epki.getEncryptedData();
        } catch (Exception e) {
            encrData = encrKey;
        }

        if (encrData.length < 40) {
            throw new IOException("Invalid encrypted key payload length: " + encrData.length);
        }

        // Convert password to UTF-16BE bytes
        byte[] passwordBytes = new byte[password.length * 2];
        for (int i = 0, j = 0; i < password.length; i++) {
            passwordBytes[j++] = (byte) (password[i] >> 8);
            passwordBytes[j++] = (byte) (password[i] & 0xFF);
        }

        // Extract salt (first 20 bytes)
        byte[] salt = new byte[20];
        System.arraycopy(encrData, 0, salt, 0, 20);

        // Extract plaintext digest (last 20 bytes)
        int plainKeyLen = encrData.length - 40;
        byte[] storedDigest = new byte[20];
        System.arraycopy(encrData, encrData.length - 20, storedDigest, 0, 20);

        // Decrypt using SHA-1 keystream
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] xorKey = salt;
        byte[] plainKey = new byte[plainKeyLen];
        int xorOffset = 0;

        while (xorOffset < plainKeyLen) {
            md.reset();
            md.update(passwordBytes);
            md.update(xorKey);
            xorKey = md.digest();

            for (int i = 0; i < 20 && xorOffset < plainKeyLen; i++, xorOffset++) {
                plainKey[xorOffset] = (byte) (encrData[20 + xorOffset] ^ xorKey[i]);
            }
        }

        // Verify computed digest against stored digest
        md.reset();
        md.update(passwordBytes);
        md.update(plainKey);
        byte[] computedDigest = md.digest();

        if (!MessageDigest.isEqual(computedDigest, storedDigest)) {
            throw new UnrecoverableEntryException("Cannot recover key: password mismatch or corrupted data");
        }

        return plainKey;
    }

    /**
     * Decodes PKCS#8 bytes into PrivateKey using preferred algorithm first.
     */
    private static PrivateKey decodePrivateKey(byte[] plainKey, String preferredAlgorithm) throws GeneralSecurityException {
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(plainKey);
        List<String> algos = new ArrayList<>();
        if (preferredAlgorithm != null && !preferredAlgorithm.isEmpty()) {
            algos.add(preferredAlgorithm);
        }
        for (String a : new String[]{"RSA", "EC", "DSA", "EdDSA", "Ed25519"}) {
            if (!algos.contains(a)) {
                algos.add(a);
            }
        }

        for (String algo : algos) {
            try {
                return KeyFactory.getInstance(algo).generatePrivate(spec);
            } catch (Exception ignored) {
            }
        }
        throw new KeyStoreException("Unsupported private key algorithm in PKCS#8 structure");
    }

    /**
     * Standard provider loader for PKCS12 / BKS.
     */
    private static SignerKeys loadStandardKeyStore(byte[] data, String storePassword,
                                                   String targetAlias, String keyPassword) throws Exception {
        char[] storePwChars = storePassword != null ? storePassword.toCharArray() : new char[0];
        char[] keyPwChars = (keyPassword != null && !keyPassword.isEmpty())
                ? keyPassword.toCharArray()
                : storePwChars;

        String[] types = new String[]{"PKCS12", "BKS", "BKS-V1"};
        KeyStore ks = null;
        Exception lastException = null;

        for (String type : types) {
            try {
                KeyStore candidate = KeyStore.getInstance(type);
                candidate.load(new ByteArrayInputStream(data), storePwChars);
                ks = candidate;
                break;
            } catch (Exception e) {
                lastException = e;
            }
        }

        if (ks == null) {
            throw lastException;
        }

        String matchedAlias = null;
        if (targetAlias != null && !targetAlias.isEmpty()) {
            if (ks.containsAlias(targetAlias)) {
                matchedAlias = targetAlias;
            } else {
                Enumeration<String> aliases = ks.aliases();
                while (aliases.hasMoreElements()) {
                    String a = aliases.nextElement();
                    if (a.equalsIgnoreCase(targetAlias)) {
                        matchedAlias = a;
                        break;
                    }
                }
            }
        } else {
            Enumeration<String> aliases = ks.aliases();
            if (aliases.hasMoreElements()) {
                matchedAlias = aliases.nextElement();
            }
        }

        if (matchedAlias == null) {
            throw new KeyStoreException("Alias '" + targetAlias + "' not found in keystore");
        }

        KeyStore.Entry entry = ks.getEntry(matchedAlias, new KeyStore.PasswordProtection(keyPwChars));
        if (!(entry instanceof KeyStore.PrivateKeyEntry)) {
            if (storePwChars.length > 0 && !Arrays.equals(keyPwChars, storePwChars)) {
                entry = ks.getEntry(matchedAlias, new KeyStore.PasswordProtection(storePwChars));
            }
        }

        if (!(entry instanceof KeyStore.PrivateKeyEntry)) {
            throw new KeyStoreException("Alias '" + matchedAlias + "' does not identify a PrivateKeyEntry");
        }

        PrivateKey privateKey = ((KeyStore.PrivateKeyEntry) entry).getPrivateKey();
        Certificate[] certs = ks.getCertificateChain(matchedAlias);
        List<X509Certificate> x509Chain = new ArrayList<>();
        if (certs != null && certs.length > 0) {
            for (Certificate c : certs) {
                if (c instanceof X509Certificate) {
                    x509Chain.add((X509Certificate) c);
                }
            }
        } else {
            Certificate singleCert = ks.getCertificate(matchedAlias);
            if (singleCert instanceof X509Certificate) {
                x509Chain.add((X509Certificate) singleCert);
            }
        }

        if (x509Chain.isEmpty()) {
            throw new CertificateException("Certificate not found for alias '" + matchedAlias + "'");
        }

        return new SignerKeys(privateKey, x509Chain);
    }

    private static byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int nRead;
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }

    public static InputStream openKeystoreStream(Context context, String uriOrPath) throws FileNotFoundException {
        if (uriOrPath == null || uriOrPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Keystore path or URI cannot be null or empty");
        }

        Uri uri = Uri.parse(uriOrPath);
        String scheme = uri.getScheme();

        if ("content".equalsIgnoreCase(scheme)) {
            InputStream stream = context.getContentResolver().openInputStream(uri);
            if (stream == null) {
                throw new FileNotFoundException("Unable to open stream for URI: " + uriOrPath);
            }
            return stream;
        }

        if ("file".equalsIgnoreCase(scheme)) {
            String path = uri.getPath();
            if (path == null) {
                throw new FileNotFoundException("Invalid file URI: " + uriOrPath);
            }
            return new FileInputStream(path);
        }

        return new FileInputStream(uriOrPath);
    }

    /**
     * Core signing engine accepting raw PrivateKey and Certificates directly.
     */
    public static void signApk(PrivateKey privateKey, List<X509Certificate> certificates,
                               File inputApk, File outApk,
                               boolean v1, boolean v2, boolean v3, boolean v4) throws Exception {
        File v4OutputFile = v4 ? new File(outApk.getAbsolutePath() + ".idsig") : null;
        try {
            ApkSigner.SignerConfig signerConfig = new ApkSigner.SignerConfig.Builder(
                    "CERT",
                    privateKey,
                    certificates
            ).build();

            ApkSigner.Builder builder = new ApkSigner.Builder(Collections.singletonList(signerConfig))
                    .setInputApk(inputApk)
                    .setOutputApk(outApk)
                    .setCreatedBy("Android Gradle 8.1.4")
                    .setV1SigningEnabled(v1)
                    .setV2SigningEnabled(v2)
                    .setV3SigningEnabled(v3)
                    .setV4SigningEnabled(v4);

            if (v4 && v4OutputFile != null) {
                builder.setV4SignatureOutputFile(v4OutputFile);
            }

            builder.build().sign();
        } catch (Exception e) {
            outApk.delete();
            if (v4OutputFile != null) {
                v4OutputFile.delete();
            }

            StringBuilder sb = new StringBuilder();
            Throwable curr = e;
            while (curr != null) {
                if (sb.length() > 0) {
                    sb.append(" -> Caused by: ");
                }
                sb.append(curr.getClass().getSimpleName()).append(": ").append(curr.getMessage());
                if (curr.getCause() == null || curr.getCause() == curr) {
                    break;
                }
                curr = curr.getCause();
            }
            throw new Exception("APK Signing failed: " + sb.toString(), e);
        }
    }

    /**
     * KeyStore-based signing method. Delegates to core signing engine.
     */
    public static void signApk(InputStream key, String storePassword, String keyAlias, String keyPassword,
                               File inputApk, File outApk,
                               boolean v1, boolean v2, boolean v3, boolean v4)
            throws Exception {
        SignerKeys signerKeys = loadSignerKeys(key, storePassword, keyAlias, keyPassword);
        signApk(signerKeys.privateKey, signerKeys.certificates, inputApk, outApk, v1, v2, v3, v4);
    }

    public static void signApk(InputStream key, String storePassword, String keyAlias, String keyPassword,
                               File inputApk, File output)
            throws Exception {
        signApk(key, storePassword, keyAlias, keyPassword, inputApk, output, true, true, true, false);
    }

    public static void signDebugKey(Context c, File inputApk, File outApk,
                                    boolean v1, boolean v2, boolean v3, boolean v4)
            throws Exception {
        try (InputStream is = c.getAssets().open("debug.keystore")) {
            signApk(is, "android", "androiddebugkey", "android",
                    inputApk, outApk, v1, v2, v3, v4);
        }
    }

    public static void signDebugKey(Context c, File inputApk, File outApk)
            throws Exception {
        signDebugKey(c, inputApk, outApk, true, true, true, false);
    }

    // The following part is designed specifically for the testkey.pk8 and testkey.x509.pem certificate key pair.
    // This set of keys originates from the AOSP native build system (located at build/target/product/security/testkey.*),
    // and the signapk.jar file in early versions of the Android SDK also used these two files directly.
    // They differ fundamentally from single-container formats such as JKS, PKCS12, and BKS, and are natively supported by JVM and ART.

    /**
     * Parses all X.509 certificates (public key) from a PEM format stream.
     */
    public static List<X509Certificate> loadCertificatesFromPem(InputStream pemStream) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        List<X509Certificate> certList = new ArrayList<>();
        for (Certificate cert : cf.generateCertificates(pemStream)) {
            if (cert instanceof X509Certificate) {
                certList.add((X509Certificate) cert);
            }
        }
        if (certList.isEmpty()) {
            throw new CertificateException("No X509Certificate found in PEM input stream");
        }
        return certList;
    }

    /**
     * Loads an unencrypted PKCS#8 private key from a binary stream.
     */
    public static PrivateKey loadPrivateKeyFromPk8(InputStream pk8Stream, String preferredAlgorithm) throws Exception {
        byte[] keyBytes = readAllBytes(pk8Stream);
        return decodePrivateKey(keyBytes, preferredAlgorithm);
    }

    /**
     * Signs an APK using separate PKCS#8 private key and PEM certificate streams.
     */
    public static void signApk(InputStream pk8Stream, InputStream pemStream,
                               File inputApk, File outApk,
                               boolean v1, boolean v2, boolean v3, boolean v4) throws Exception {
        List<X509Certificate> certificates = loadCertificatesFromPem(pemStream);
        String preferredAlgo = certificates.get(0).getPublicKey().getAlgorithm();
        PrivateKey privateKey = loadPrivateKeyFromPk8(pk8Stream, preferredAlgo);
        signApk(privateKey, certificates, inputApk, outApk, v1, v2, v3, v4);
    }

    public static void signApk(InputStream pk8Stream, InputStream pemStream,
                               File inputApk, File output) throws Exception {
        signApk(pk8Stream, pemStream, inputApk, output, true, true, true, false);
    }

    /**
     * Signs APK using legacy AOSP testkey (testkey.pk8 + testkey.x509.pem) from assets.
     */
    public static void signTestKey(Context c, File inputApk, File outApk,
                                   boolean v1, boolean v2, boolean v3, boolean v4) throws Exception {
        try (InputStream pk8Is = c.getAssets().open("testkey.pk8");
             InputStream pemIs = c.getAssets().open("testkey.x509.pem")) {
            signApk(pk8Is, pemIs, inputApk, outApk, v1, v2, v3, v4);
        }
    }

    public static void signTestKey(Context c, File inputApk, File outApk) throws Exception {
        signTestKey(c, inputApk, outApk, true, true, true, false);
    }
}