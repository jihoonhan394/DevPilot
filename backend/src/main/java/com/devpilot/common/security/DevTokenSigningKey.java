package com.devpilot.common.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

/**
 * devtoken 모드의 EC P-256 서명 키 (docs/03 §4.2). {@code DEVPILOT_DEV_JWT_KEY}(PKCS#8 PEM)에서 읽거나 기동 시 새로
 * 만든다. 발급({@code DevTokenService})과 검증({@code JwtDecoder})이 같은 인스턴스를 쓴다.
 */
public final class DevTokenSigningKey {

    private static final int P256_FIELD_BITS = 256;
    private static final int P256_COORDINATE_BYTES = 32;
    private static final byte UNCOMPRESSED_POINT = 0x04;
    private static final int TAG_SEQUENCE = 0x30;
    private static final int TAG_OCTET_STRING = 0x04;
    private static final int TAG_BIT_STRING = 0x03;
    private static final int TAG_SEC1_PUBLIC_KEY = 0xA1;

    private final ECKey jwk;

    private DevTokenSigningKey(ECPublicKey publicKey, ECPrivateKey privateKey) {
        verifyPair(publicKey, privateKey);
        ECKey withoutKid = new ECKey.Builder(Curve.P_256, publicKey).privateKey(privateKey).build();
        try {
            this.jwk =
                    new ECKey.Builder(withoutKid)
                            .keyUse(KeyUse.SIGNATURE)
                            .algorithm(com.nimbusds.jose.JWSAlgorithm.ES256)
                            .keyID(withoutKid.computeThumbprint().toString())
                            .build();
        } catch (JOSEException exception) {
            throw new IllegalStateException("cannot compute devtoken key thumbprint", exception);
        }
    }

    /** 새 키를 만든다. 재기동하면 이전 키로 서명한 토큰은 모두 무효가 된다. */
    public static DevTokenSigningKey generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair pair = generator.generateKeyPair();
            return new DevTokenSigningKey(
                    (ECPublicKey) pair.getPublic(), (ECPrivateKey) pair.getPrivate());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("cannot generate EC P-256 key", exception);
        }
    }

    /**
     * {@code openssl ecparam -genkey -name prime256v1 -noout | openssl pkcs8 -topk8 -nocrypt}로 만든
     * PKCS#8 PEM을 읽는다. 환경변수에 한 줄로 넣은 {@code \n} 이스케이프도 받는다. 공개키는 PEM 안의 SEC1 {@code publicKey}에서
     * 읽는다.
     */
    public static DevTokenSigningKey fromPem(String pem) {
        String normalized = pem.replace("\\n", "\n");
        String base64 =
                normalized
                        .replaceAll("-----BEGIN [A-Z ]+-----", "")
                        .replaceAll("-----END [A-Z ]+-----", "")
                        .replaceAll("\\s", "");
        byte[] der;
        try {
            der = Base64.getDecoder().decode(base64.getBytes(StandardCharsets.US_ASCII));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "DEVPILOT_DEV_JWT_KEY is not valid base64 PEM", exception);
        }
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            ECPrivateKey privateKey =
                    (ECPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(der));
            if (privateKey.getParams().getCurve().getField().getFieldSize() != P256_FIELD_BITS) {
                throw new IllegalStateException("DEVPILOT_DEV_JWT_KEY must be an EC P-256 key");
            }
            ECPoint point = decodeUncompressedPoint(extractSec1PublicKey(der));
            ECPublicKey publicKey =
                    (ECPublicKey)
                            keyFactory.generatePublic(
                                    new ECPublicKeySpec(point, privateKey.getParams()));
            return new DevTokenSigningKey(publicKey, privateKey);
        } catch (GeneralSecurityException | ClassCastException exception) {
            throw new IllegalStateException(
                    "DEVPILOT_DEV_JWT_KEY must be a PKCS#8 EC P-256 private key", exception);
        }
    }

    /** 서명용 JWK (private 포함). */
    public ECKey signingJwk() {
        return jwk;
    }

    /** 검증용 공개 JWK. */
    public ECKey publicJwk() {
        return jwk.toPublicJWK();
    }

    public String keyId() {
        return jwk.getKeyID();
    }

    /** {@code GET /api/v1/dev/jwks.json} 본문 (docs/05 §1.4.5). private 필드({@code d})는 없다. */
    public Map<String, Object> publicJwks() {
        return Map.of("keys", java.util.List.of(publicJwk().toJSONObject()));
    }

    private static void verifyPair(ECPublicKey publicKey, ECPrivateKey privateKey) {
        byte[] probe = "devpilot-devtoken-key-check".getBytes(StandardCharsets.US_ASCII);
        try {
            Signature signer = Signature.getInstance("SHA256withECDSA");
            signer.initSign(privateKey);
            signer.update(probe);
            byte[] signature = signer.sign();
            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(publicKey);
            verifier.update(probe);
            if (!verifier.verify(signature)) {
                throw new IllegalStateException("devtoken public key does not match private key");
            }
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("cannot verify devtoken key pair", exception);
        }
    }

    /**
     * PKCS#8 PrivateKeyInfo 안의 SEC1 ECPrivateKey에서 {@code [1] publicKey} BIT STRING 값을 꺼낸다.
     * openssl이 만든 키에는 공개키가 들어 있다. 없으면 키를 다시 만들라고 알린다.
     */
    static byte[] extractSec1PublicKey(byte[] pkcs8) {
        DerReader outer = DerReader.expect(pkcs8, 0, TAG_SEQUENCE);
        int offset = outer.valueStart();
        while (offset < outer.valueEnd()) {
            DerReader element = DerReader.at(pkcs8, offset);
            if (element.tag() == TAG_OCTET_STRING) {
                DerReader sec1 = DerReader.expect(pkcs8, element.valueStart(), TAG_SEQUENCE);
                int inner = sec1.valueStart();
                while (inner < sec1.valueEnd()) {
                    DerReader field = DerReader.at(pkcs8, inner);
                    if (field.tag() == TAG_SEC1_PUBLIC_KEY) {
                        DerReader bitString =
                                DerReader.expect(pkcs8, field.valueStart(), TAG_BIT_STRING);
                        // 첫 바이트는 unused bits 수(0)다
                        return Arrays.copyOfRange(
                                pkcs8, bitString.valueStart() + 1, bitString.valueEnd());
                    }
                    inner = field.valueEnd();
                }
            }
            offset = element.valueEnd();
        }
        throw new IllegalStateException(
                "DEVPILOT_DEV_JWT_KEY has no embedded public key; generate it with"
                        + " 'openssl ecparam -genkey -name prime256v1 -noout | openssl pkcs8 -topk8"
                        + " -nocrypt'");
    }

    private static ECPoint decodeUncompressedPoint(byte[] encoded) {
        if (encoded.length != 1 + 2 * P256_COORDINATE_BYTES || encoded[0] != UNCOMPRESSED_POINT) {
            throw new IllegalStateException(
                    "devtoken public key must be an uncompressed P-256 point");
        }
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(encoded, 1, 1 + P256_COORDINATE_BYTES));
        BigInteger y =
                new BigInteger(
                        1,
                        Arrays.copyOfRange(
                                encoded, 1 + P256_COORDINATE_BYTES, 1 + 2 * P256_COORDINATE_BYTES));
        return new ECPoint(x, y);
    }

    /** 최소 DER TLV 읽기 (길이 형식: short, long 1~3바이트). */
    private record DerReader(int tag, int valueStart, int valueEnd) {

        static DerReader expect(byte[] der, int offset, int expectedTag) {
            DerReader reader = at(der, offset);
            if (reader.tag() != expectedTag) {
                throw new IllegalStateException("unexpected DER tag in DEVPILOT_DEV_JWT_KEY");
            }
            return reader;
        }

        static DerReader at(byte[] der, int offset) {
            if (offset + 2 > der.length) {
                throw new IllegalStateException("truncated DER in DEVPILOT_DEV_JWT_KEY");
            }
            int tag = der[offset] & 0xFF;
            int first = der[offset + 1] & 0xFF;
            int lengthBytes = (first & 0x80) == 0 ? 0 : first & 0x7F;
            if (lengthBytes > 3 || offset + 2 + lengthBytes > der.length) {
                throw new IllegalStateException("unsupported DER length in DEVPILOT_DEV_JWT_KEY");
            }
            int length = lengthBytes == 0 ? first : 0;
            for (int i = 0; i < lengthBytes; i++) {
                length = (length << 8) | (der[offset + 2 + i] & 0xFF);
            }
            int valueStart = offset + 2 + lengthBytes;
            int valueEnd = valueStart + length;
            if (valueEnd > der.length) {
                throw new IllegalStateException("truncated DER in DEVPILOT_DEV_JWT_KEY");
            }
            return new DerReader(tag, valueStart, valueEnd);
        }
    }
}
