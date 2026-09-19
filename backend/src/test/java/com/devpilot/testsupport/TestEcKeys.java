package com.devpilot.testsupport;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * 테스트용 EC P-256 키와 PEM을 런타임에 만든다. 개인 키 PEM 문자열은 소스에 리터럴로 두지 않는다(gitleaks, docs/07 §11.3). openssl
 * {@code ecparam -genkey | pkcs8 -topk8}과 같은 구조(SEC1 안에 공개키 포함)의 PKCS#8을 조립한다.
 */
public final class TestEcKeys {

    private static final byte[] OID_EC_PUBLIC_KEY = {
        0x06, 0x07, 0x2A, (byte) 0x86, 0x48, (byte) 0xCE, 0x3D, 0x02, 0x01
    };
    private static final byte[] OID_PRIME256V1 = {
        0x06, 0x08, 0x2A, (byte) 0x86, 0x48, (byte) 0xCE, 0x3D, 0x03, 0x01, 0x07
    };
    private static final int COORDINATE_BYTES = 32;

    private TestEcKeys() {}

    public static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            return generator.generateKeyPair();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** SEC1 {@code [1] publicKey}를 포함한 PKCS#8 PEM. */
    public static String pkcs8Pem(KeyPair pair, boolean includePublicKey) {
        ECPrivateKey privateKey = (ECPrivateKey) pair.getPrivate();
        ECPublicKey publicKey = (ECPublicKey) pair.getPublic();
        ByteArrayOutputStream sec1Body = new ByteArrayOutputStream();
        sec1Body.writeBytes(tlv(0x02, new byte[] {0x01}));
        sec1Body.writeBytes(tlv(0x04, fixed(privateKey.getS())));
        if (includePublicKey) {
            ByteArrayOutputStream point = new ByteArrayOutputStream();
            point.write(0x00); // unused bits
            point.write(0x04); // uncompressed
            point.writeBytes(fixed(publicKey.getW().getAffineX()));
            point.writeBytes(fixed(publicKey.getW().getAffineY()));
            sec1Body.writeBytes(tlv(0xA1, tlv(0x03, point.toByteArray())));
        }
        byte[] sec1 = tlv(0x30, sec1Body.toByteArray());

        ByteArrayOutputStream algorithm = new ByteArrayOutputStream();
        algorithm.writeBytes(OID_EC_PUBLIC_KEY);
        algorithm.writeBytes(OID_PRIME256V1);

        ByteArrayOutputStream pkcs8Body = new ByteArrayOutputStream();
        pkcs8Body.writeBytes(tlv(0x02, new byte[] {0x00}));
        pkcs8Body.writeBytes(tlv(0x30, algorithm.toByteArray()));
        pkcs8Body.writeBytes(tlv(0x04, sec1));
        byte[] der = tlv(0x30, pkcs8Body.toByteArray());

        String label = "PRIVATE" + " KEY";
        return "-----BEGIN "
                + label
                + "-----\n"
                + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(der)
                + "\n-----END "
                + label
                + "-----\n";
    }

    private static byte[] fixed(BigInteger value) {
        byte[] raw = value.toByteArray();
        if (raw.length == COORDINATE_BYTES) {
            return raw;
        }
        if (raw.length > COORDINATE_BYTES) {
            return Arrays.copyOfRange(raw, raw.length - COORDINATE_BYTES, raw.length);
        }
        byte[] padded = new byte[COORDINATE_BYTES];
        System.arraycopy(raw, 0, padded, COORDINATE_BYTES - raw.length, raw.length);
        return padded;
    }

    private static byte[] tlv(int tag, byte[] value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        int length = value.length;
        if (length < 0x80) {
            out.write(length);
        } else if (length < 0x100) {
            out.write(0x81);
            out.write(length);
        } else {
            out.write(0x82);
            out.write(length >> 8);
            out.write(length & 0xFF);
        }
        out.writeBytes(value);
        return out.toByteArray();
    }
}
