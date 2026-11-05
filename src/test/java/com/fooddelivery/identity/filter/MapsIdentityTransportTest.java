package com.fooddelivery.identity.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.identity.config.MapsIdentityProperties;
import com.fooddelivery.identity.dto.SessionInfo;
import com.fooddelivery.identity.entity.AppUser;
import com.fooddelivery.identity.port.CachePort;
import com.fooddelivery.identity.repository.UserRepository;
import com.fooddelivery.identity.security.MapsPeerAuthenticator;
import com.fooddelivery.identity.service.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.ssl.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.server.WebServer;
import org.springframework.core.io.DefaultResourceLoader;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual HTTPS/mTLS, servlet async upload/deadline and current authority; isolated DB/cache ports. */
class MapsIdentityTransportTest {
    private static final char[] PASSWORD = "isolated-test-store".toCharArray();
    @TempDir static Path temp;
    static HttpClient peer, unrelated, anonymous;
    final Clock clock = Clock.systemUTC();
    final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    final UserRepository users = mock(UserRepository.class);
    final CachePort cache = mock(CachePort.class);
    final Map<String, String> stored = new ConcurrentHashMap<>();
    final SimpleMeterRegistry metrics = new SimpleMeterRegistry();
    WebServer server; MapsIdentityFilter filter; MapsSessionTokens tokens;
    boolean nativeStores;
    MapsIdentityProperties properties; String credential; AppUser user; SessionInfo session;

    @BeforeAll static void certificates() throws Exception {
        key("server", "CN=localhost", "SAN=DNS:localhost,IP:127.0.0.1", "serverAuth");
        key("peer", "CN=maps-adapter", "SAN=URI:spiffe://maps/adapter", "clientAuth");
        key("other", "CN=unrelated-service", "SAN=URI:spiffe://other/service", "clientAuth");
        for (String name : List.of("server", "peer", "other")) {
            keytool("-exportcert", "-alias", name, "-keystore", temp.resolve(name + ".p12").toString(),
                    "-storepass", new String(PASSWORD), "-file", temp.resolve(name + ".cer").toString());
        }
        for (String name : List.of("peer", "other"))
            keytool("-importcert", "-noprompt", "-alias", name, "-file", temp.resolve(name + ".cer").toString(),
                    "-keystore", temp.resolve("server-trust.p12").toString(), "-storetype", "PKCS12", "-storepass", new String(PASSWORD));
        peer = client("peer"); unrelated = client("other"); anonymous = client(null);
    }
    static void key(String name, String dn, String san, String usage) throws Exception {
        keytool("-genkeypair", "-alias", name, "-keyalg", "RSA", "-keysize", "2048", "-validity", "2",
                "-dname", dn, "-ext", san, "-ext", "EKU=" + usage, "-keystore", temp.resolve(name + ".p12").toString(),
                "-storetype", "PKCS12", "-storepass", new String(PASSWORD), "-keypass", new String(PASSWORD));
    }
    static void keytool(String... arguments) throws Exception {
        var command = new ArrayList<String>(); command.add(Path.of(System.getProperty("java.home"), "bin", "keytool").toString());
        command.addAll(List.of(arguments));
        var process = new ProcessBuilder(command).redirectErrorStream(true).start();
        process.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
        assertEquals(0, process.waitFor(), "Owned TLS fixture certificate generation");
    }
    static HttpClient client(String name) throws Exception {
        KeyManager[] managers = null;
        if (name != null) {
            var keys = KeyStore.getInstance("PKCS12");
            try (var input = Files.newInputStream(temp.resolve(name + ".p12"))) { keys.load(input, PASSWORD); }
            var factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()); factory.init(keys, PASSWORD); managers = factory.getKeyManagers();
        }
        var trust = KeyStore.getInstance("PKCS12"); trust.load(null, PASSWORD);
        try (var input = Files.newInputStream(temp.resolve("server.cer"))) {
            trust.setCertificateEntry("server", java.security.cert.CertificateFactory.getInstance("X.509").generateCertificate(input));
        }
        var factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()); factory.init(trust);
        var ssl = SSLContext.getInstance("TLS"); ssl.init(managers, factory.getTrustManagers(), null);
        return HttpClient.newBuilder().sslContext(ssl).connectTimeout(Duration.ofSeconds(1)).version(HttpClient.Version.HTTP_1_1).build();
    }
    @BeforeEach void start() throws Exception {
        properties = new MapsIdentityProperties(); properties.setEnabled(true);
        properties.setAuthority("https://identity.test"); properties.setAudience("maps-editor"); properties.setSigningKeyId("current");
        properties.setAllowedPeers(List.of("spiffe://maps/adapter")); properties.setConcurrency(1);
        var pair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        Path privateKey = Files.createTempFile(temp, "sign-", ".pem"), publicKey = Files.createTempFile(temp, "verify-", ".pem");
        Files.writeString(privateKey, pem(pair.getPrivate().getEncoded(), "PRIVATE KEY"));
        Files.setPosixFilePermissions(privateKey, PosixFilePermissions.fromString("rw-------"));
        Files.writeString(publicKey, pem(pair.getPublic().getEncoded(), "PUBLIC KEY"));
        properties.setPrivateKey(privateKey.toUri().toString()); properties.setVerificationKeys(Map.of("current", publicKey.toUri().toString()));
        tokens = new MapsSessionTokens(properties, clock, new DefaultResourceLoader());
        Instant now = clock.instant();
        user = AppUser.builder().id(UUID.randomUUID()).isActive(true).entitlementsVersion(4).build();
        session = SessionInfo.builder().sessionId(UUID.randomUUID().toString()).purpose(OtpPurpose.LOGIN)
                .createdAt(now.minusSeconds(30)).absoluteExpiresAt(now.plusSeconds(3600)).build();
        stored.put("USER_SESSIONS:" + user.getId(), json.writeValueAsString(List.of(session)));
        when(cache.get(anyString())).thenAnswer(invocation -> stored.get(invocation.getArgument(0)));
        when(users.findById(user.getId())).thenReturn(Optional.of(user)); credential = tokens.issue(user, session);
        when(users.mapsAuthorityIsPrimary()).thenReturn(true);
        var current = new MapsCurrentSessionService(users, new SessionRegistry(cache, json, clock), tokens, properties, clock);
        startServer(current, mock(AuthService.class));
    }
    void startServer(MapsCurrentSessionService current, AuthService auth) {
        var validator = jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();
        var operations = new MapsIdentityOperations(auth, current, properties, validator);
        filter = new MapsIdentityFilter(properties, new MapsPeerAuthenticator(properties, clock), operations, metrics);
        var factory = new TomcatServletWebServerFactory(0);
        var ssl = new Ssl(); ssl.setEnabled(true); ssl.setKeyStore(temp.resolve("server.p12").toUri().toString());
        ssl.setKeyStorePassword(new String(PASSWORD)); ssl.setKeyStoreType("PKCS12");
        ssl.setTrustStore(temp.resolve("server-trust.p12").toUri().toString()); ssl.setTrustStoreType("PKCS12");
        ssl.setTrustStorePassword(new String(PASSWORD)); ssl.setClientAuth(Ssl.ClientAuth.NEED); factory.setSsl(ssl);
        server = factory.getWebServer(context -> {
            var registration = context.addFilter("maps-identity", filter); registration.setAsyncSupported(true);
            registration.addMappingForUrlPatterns(null, false, "/*");
            var fallback = context.addServlet("fallback", new HttpServlet() {
                @Override protected void service(HttpServletRequest req, HttpServletResponse res) { res.setStatus(404); }
            }); fallback.setAsyncSupported(true); fallback.addMapping("/*");
        }); server.start();
    }
    static String pem(byte[] bytes, String type) {
        return "-----BEGIN " + type + "-----\n" + Base64.getEncoder().encodeToString(bytes) + "\n-----END " + type + "-----";
    }
    @AfterEach void stop() { if (server != null) server.stop(); if (filter != null) filter.close(); }
    HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("https://localhost:" + server.getPort() + path)).timeout(Duration.ofSeconds(4))
                .header("Content-Type", "application/json");
    }
    String body() throws Exception {
        return json.writeValueAsString(Map.of("version", 1, "audience", "maps-editor", "nonce", "n".repeat(43), "credential", credential));
    }
    HttpResponse<String> send(HttpClient client, String path, String body) throws Exception {
        return client.send(request(path).POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    @Test void actualTlsPeerValidatesTheHumanAndRechecksRevocationAndDisable() throws Exception {
        var result = send(peer, "/internal/maps/v1/validate", body()); assertEquals(200, result.statusCode());
        var value = json.readTree(result.body()); assertEquals(user.getId().toString(), value.path("subject").asText());
        assertEquals("n".repeat(43), value.path("nonce").asText()); assertTrue(value.path("active").booleanValue());
        assertFalse(value.path("authentication").path("mfa").booleanValue());
        stored.put("BLACKLIST:SESSION:" + session.getSessionId(), "true");
        assertEquals(401, send(peer, "/internal/maps/v1/validate", body()).statusCode());
        stored.remove("BLACKLIST:SESSION:" + session.getSessionId()); user.setActive(false);
        assertEquals(401, send(peer, "/internal/maps/v1/validate", body()).statusCode());
    }
    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="MAPS_IDENTITY_NATIVE_CONFIG", matches=".+")
    void realPostgresAndRedisDriveOtpCurrentAuthorityAndPassiveReadSemantics() throws Exception {
        stop();
        try (var nativeAuthority = new MapsNativeAuthority(Path.of(System.getenv("MAPS_IDENTITY_NATIVE_CONFIG")), properties, tokens, clock, json, metrics)) {
            nativeStores=true;
            properties.setConcurrency(4);
            startServer(nativeAuthority.current(), nativeAuthority.auth());
            String phone = "9000000001";
            var input = new HashMap<String,Object>(Map.of("version",1,"audience","maps-editor","nonce","n".repeat(43),"phone_number",phone));
            assertEquals(200,send(peer,"/internal/maps/v1/otp",json.writeValueAsString(input)).statusCode());
            input.put("otp",nativeAuthority.deliveredOtp());
            var created=send(peer,"/internal/maps/v1/session",json.writeValueAsString(input));
            assertEquals(200,created.statusCode());
            credential=json.readTree(created.body()).path("credential").asText();
            var verified=tokens.verify(credential,false);
            if(System.getenv("MAPS_GO_CONTRACT_PROBE")!=null) probeGo(verified.subject().toString(),verified.sessionId(),true);
            String sessions="USER_SESSIONS:"+verified.subject();
            var redis=nativeAuthority.redis();
            var otpInput=new HashMap<>(input);otpInput.remove("otp");
            assertEquals(200,send(peer,"/internal/maps/v1/otp",json.writeValueAsString(otpInput)).statusCode());
            otpInput.put("otp",nativeAuthority.deliveredOtp());
            String sharedProof=json.writeValueAsString(otpInput);
            try(var executor=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                var barrier=new java.util.concurrent.CountDownLatch(1);
                var first=executor.submit(()->{barrier.await();return send(peer,"/internal/maps/v1/session",sharedProof).statusCode();});
                var second=executor.submit(()->{barrier.await();return send(peer,"/internal/maps/v1/session",sharedProof).statusCode();});
                barrier.countDown();
                var statuses=new ArrayList<>(List.of(first.get(5,java.util.concurrent.TimeUnit.SECONDS),second.get(5,java.util.concurrent.TimeUnit.SECONDS)));
                Collections.sort(statuses);assertEquals(List.of(200,401),statuses,"OTP consumed more than once across transactions");
            }
            assertEquals(2,json.readTree(redis.opsForValue().get(sessions)).size(),"Failed concurrent login left a person-session");
            String before=redis.opsForValue().get(sessions);
            Long ttl=redis.getExpire(sessions,java.util.concurrent.TimeUnit.MILLISECONDS);
            assertNotNull(before);assertNotNull(ttl);assertTrue(ttl>0);
            for(int i=0;i<3;i++) assertEquals(200,send(peer,"/internal/maps/v1/validate",body()).statusCode());
            assertEquals(before,redis.opsForValue().get(sessions),"Passive validation wrote registry/activity");
            assertTrue(redis.getExpire(sessions,java.util.concurrent.TimeUnit.MILLISECONDS)<=ttl,"Passive validation extended TTL");
            assertEquals(401,send(peer,"/internal/maps/v1/session",json.writeValueAsString(input)).statusCode(),"Consumed OTP replay");
            var jdbc=nativeAuthority.jdbc();
            assertThrows(org.springframework.dao.DataAccessException.class,()->nativeAuthority.runtimeJdbc().execute("CREATE TABLE forbidden_identity_schema_change(x integer)"));
            assertThrows(org.springframework.dao.DataAccessException.class,()->nativeAuthority.runtimeJdbc().execute("DELETE FROM flyway_schema_history"));
            jdbc.update("UPDATE users SET is_active=false WHERE id=?",verified.subject());
            assertEquals(401,send(peer,"/internal/maps/v1/validate",body()).statusCode());
            if(System.getenv("MAPS_GO_CONTRACT_PROBE")!=null) probeGo(verified.subject().toString(),verified.sessionId(),false);
            jdbc.update("UPDATE users SET is_active=true,entitlements_version=entitlements_version+1 WHERE id=?",verified.subject());
            assertEquals(401,send(peer,"/internal/maps/v1/validate",body()).statusCode(),"Old account version cached");
            jdbc.update("UPDATE users SET entitlements_version=0 WHERE id=?",verified.subject()); // isolated authority fixture only
            assertEquals(200,send(peer,"/internal/maps/v1/validate",body()).statusCode());
            jdbc.execute("ALTER TABLE users RENAME TO users_owned_outage");
            try { assertEquals(503,send(peer,"/internal/maps/v1/validate",body()).statusCode(),"Missing database authority used a cached positive"); }
            finally { jdbc.execute("ALTER TABLE users_owned_outage RENAME TO users"); }
            var refreshed=send(peer,"/internal/maps/v1/refresh",body());assertEquals(200,refreshed.statusCode());
            String next=json.readTree(refreshed.body()).path("credential").asText();
            assertEquals(verified.sessionId(),tokens.verify(next,false).sessionId());
            assertEquals(verified.expiresAt(),tokens.verify(next,false).expiresAt(),"Refresh extended absolute lifetime");
            redis.opsForValue().set("BLACKLIST:SESSION:"+verified.sessionId(),"true");
            assertEquals(401,send(peer,"/internal/maps/v1/validate",body()).statusCode());
            redis.delete("BLACKLIST:SESSION:"+verified.sessionId());
            redis.opsForValue().set(sessions,"corrupt-private-fixture");
            var corrupt=send(peer,"/internal/maps/v1/validate",body());assertEquals(503,corrupt.statusCode());
            assertFalse(corrupt.body().contains("corrupt-private-fixture"));
            redis.opsForValue().set(sessions,before,Duration.ofMinutes(10));
            try(var executor=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                var barrier=new java.util.concurrent.CountDownLatch(1);
                var refresh=executor.submit(()->{barrier.await();return send(peer,"/internal/maps/v1/refresh",body()).statusCode();});
                var logout=executor.submit(()->{barrier.await();return send(peer,"/internal/maps/v1/logout",body()).statusCode();});
                barrier.countDown();
                assertEquals(200,logout.get(5,java.util.concurrent.TimeUnit.SECONDS));
                assertTrue(List.of(200,401).contains(refresh.get(5,java.util.concurrent.TimeUnit.SECONDS)),"Refresh/revoke row-lock ordering");
            }
            assertEquals(401,send(peer,"/internal/maps/v1/validate",body()).statusCode());
            // An unavailable actual cache cannot be replaced by a positive process cache.
            nativeAuthority.stopRedisClient();
            assertEquals(503,send(peer,"/internal/maps/v1/validate",body()).statusCode());
        }
    }
    @Test void trustedButUnrelatedCertificateAndMissingCertificateCannotCallAuthority() throws Exception {
        assertEquals(403, send(unrelated, "/internal/maps/v1/validate", body()).statusCode());
        assertThrows(java.io.IOException.class, () -> send(anonymous, "/internal/maps/v1/validate", body()));
        verifyNoInteractions(users, cache);
    }
    @Test void dedicatedMapsInstanceCannotReachOrdinaryIdentityRoutes() throws Exception {
        for(String path:List.of("/api/v1/auth/session","/api/v1/auth/otp","/api/v1/admin/users"))
            assertEquals(404,send(peer,path,"{}").statusCode());
        verifyNoInteractions(users,cache);
    }
    @Test void legacyHeadersQueryAndUnsupportedBodyNeverEnterTheAuthority() throws Exception {
        var header = peer.send(request("/internal/maps/v1/validate").header("X-User-Id", "SERVICE")
                .POST(HttpRequest.BodyPublishers.ofString(body())).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(403, header.statusCode());
        assertEquals(400, send(peer, "/internal/maps/v1/validate?token=forbidden", body()).statusCode());
        assertEquals(400, send(peer, "/internal/maps/v1/validate", body().replace("\"version\":1", "\"version\":1,\"version\":1")).statusCode());
        assertEquals(413, send(peer, "/internal/maps/v1/validate", " ".repeat(8193)).statusCode());
        var nonUtf8=peer.send(request("/internal/maps/v1/validate").POST(HttpRequest.BodyPublishers.ofByteArray(
                body().getBytes(java.nio.charset.StandardCharsets.UTF_16))).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(400,nonUtf8.statusCode());
        verifyNoInteractions(users, cache);
    }
    @Test void unavailableCurrentAuthorityReturnsFinite503WithoutPrivateExceptionText() throws Exception {
        when(cache.get(anyString())).thenThrow(new IllegalStateException("DO_NOT_DISCLOSE private credential"));
        var result = send(peer, "/internal/maps/v1/validate", body());
        assertEquals(503, result.statusCode()); assertEquals("no-store", result.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals("UNAVAILABLE", json.readTree(result.body()).path("code").asText()); assertFalse(result.body().contains("DO_NOT_DISCLOSE"));
    }
    @Test void aSlowUploadUsesTheSameWholeRequestDeadline() throws Exception {
        for(int attempt=0;attempt<3;attempt++)
        try (var socket = (SSLSocket) peer.sslContext().getSocketFactory().createSocket("localhost", server.getPort())) {
            socket.setSoTimeout(3500); socket.startHandshake(); long started = System.nanoTime();
            socket.getOutputStream().write(("POST /internal/maps/v1/validate HTTP/1.1\r\nHost: localhost\r\n"
                    + "Content-Type: application/json\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n1\r\n{\r\n")
                    .getBytes(java.nio.charset.StandardCharsets.US_ASCII)); socket.getOutputStream().flush();
            String status = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(), java.nio.charset.StandardCharsets.US_ASCII)).readLine();
            assertTrue(status.contains("503"), status);
            assertTrue(Duration.ofNanos(System.nanoTime() - started).compareTo(Duration.ofMillis(2500)) < 0);
        }
        verifyNoInteractions(users, cache);
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="MAPS_GO_CONTRACT_PROBE", matches=".+")
    void realGoConsumerReadsActualJavaTlsCurrentDecisions() throws Exception {
        for(boolean active:List.of(true,false)) {
            user.setActive(active);probeGo(user.getId().toString(),session.getSessionId(),active);
        }
    }
    void probeGo(String subject, String sessionID, boolean active) throws Exception {
        var keys = KeyStore.getInstance("PKCS12");
        try(var input=Files.newInputStream(temp.resolve("peer.p12"))){keys.load(input,PASSWORD);}
        Path ca=temp.resolve("go-ca.pem"),cert=temp.resolve("go-peer.pem"),key=temp.resolve("go-peer-key.pem"),fixture=temp.resolve("go-fixture.json");
        Files.writeString(ca,pem(Files.readAllBytes(temp.resolve("server.cer")),"CERTIFICATE"));
        Files.writeString(cert,pem(keys.getCertificate("peer").getEncoded(),"CERTIFICATE"));
        Files.writeString(key,pem(keys.getKey("peer",PASSWORD).getEncoded(),"PRIVATE KEY"));
        Files.setPosixFilePermissions(key,PosixFilePermissions.fromString("rw-------"));

            var material=new HashMap<String,Object>(Map.of("client",Map.of("url","https://localhost:"+server.getPort(),
                    "authority",properties.getAuthority(),"audience",properties.getAudience(),"ca_file",ca.toString(),
                    "certificate_file",cert.toString(),"key_file",key.toString()),"credential",credential,
                    "subject",subject,"session",sessionID,"active",active));
            if(nativeStores && System.getenv("MAPS_GO_RELAY_DSN")!=null) {
                material.put("relay_dsn_file",System.getenv("MAPS_GO_RELAY_DSN"));
                material.put("otp_delivery_file",System.getenv("MAPS_GO_OTP_DELIVERY"));
            }
            Files.writeString(fixture,json.writeValueAsString(material));
            Files.setPosixFilePermissions(fixture,PosixFilePermissions.fromString("rw-------"));
            var builder=new ProcessBuilder(System.getenv("MAPS_GO_CONTRACT_PROBE"),"-test.run=^TestJavaProducerContract$","-test.v");
            builder.environment().put("MAPS_JAVA_CONTRACT_FIXTURE",fixture.toString());builder.redirectErrorStream(true);
            var process=builder.start();
            boolean completed=process.waitFor(8,java.util.concurrent.TimeUnit.SECONDS);
            if(!completed)process.destroyForcibly();
            String result=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(completed,"owned Go probe deadline");assertEquals(0,process.exitValue(),result);

    }
}
