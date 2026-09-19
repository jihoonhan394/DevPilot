package com.devpilot.testsupport;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * supabase 모드 JWT 검증용 로컬 JWKS 서버 (docs/09 §2, §6.2). JDK {@code HttpServer}만 쓴다. {@code
 * /auth/v1/.well-known/jwks.json}으로 공개키를 제공하고 요청 횟수를 센다.
 */
public final class TestJwksServer implements AutoCloseable {

    public static final String JWKS_PATH = "/auth/v1/.well-known/jwks.json";
    public static final String FIRST_KEY_ID = "test-key-1";

    private final HttpServer server;
    private final List<ECKey> keys = new CopyOnWriteArrayList<>();
    private final AtomicInteger requestCount = new AtomicInteger();

    private TestJwksServer(HttpServer server) {
        this.server = server;
    }

    public static TestJwksServer start() {
        try {
            HttpServer server =
                    HttpServer.create(
                            new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            TestJwksServer jwksServer = new TestJwksServer(server);
            jwksServer.addKey(FIRST_KEY_ID);
            server.createContext(JWKS_PATH, jwksServer::serveJwks);
            server.start();
            return jwksServer;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public ECKey addKey(String keyId) {
        try {
            ECKey key =
                    new ECKeyGenerator(Curve.P_256)
                            .keyID(keyId)
                            .keyUse(KeyUse.SIGNATURE)
                            .algorithm(com.nimbusds.jose.JWSAlgorithm.ES256)
                            .generate();
            keys.add(key);
            return key;
        } catch (JOSEException exception) {
            throw new IllegalStateException("cannot generate test key", exception);
        }
    }

    public ECKey key(String keyId) {
        return keys.stream().filter(key -> keyId.equals(key.getKeyID())).findFirst().orElseThrow();
    }

    public String issuer() {
        return baseUrl() + "/auth/v1";
    }

    public String jwksUri() {
        return baseUrl() + JWKS_PATH;
    }

    public int requestCount() {
        return requestCount.get();
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void serveJwks(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        requestCount.incrementAndGet();
        byte[] body =
                new JWKSet(
                                keys.stream()
                                        .map(key -> (com.nimbusds.jose.jwk.JWK) key.toPublicJWK())
                                        .toList())
                        .toString()
                        .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
