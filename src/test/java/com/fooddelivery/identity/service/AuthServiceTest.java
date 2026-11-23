package com.fooddelivery.identity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.audit.AuditTrail;
import com.fooddelivery.common.enums.*;
import com.fooddelivery.identity.dto.AuthRequests.*;
import com.fooddelivery.identity.dto.SessionInfo;
import com.fooddelivery.identity.entity.AppUser;
import com.fooddelivery.identity.entitlement.service.EntitlementService;
import com.fooddelivery.identity.exception.MaxSessionsReachedException;
import com.fooddelivery.identity.port.*;
import com.fooddelivery.identity.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Real token signing and session state machine, mocked infrastructure; never contacts Redis/DB. */
class AuthServiceTest {
    final String phone="8999123456",code="123456";
    final UserRepository users=mock(UserRepository.class);
    final EventPublisherPort events=mock(EventPublisherPort.class);
    final CachePort cache=mock(CachePort.class);
    final EntitlementService entitlements=mock(EntitlementService.class);
    final AuditTrail audit=mock(AuditTrail.class);
    final java.time.Clock clock=mock(java.time.Clock.class);
    final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    final Map<String,String> stored=new HashMap<>();
    final Instant now=Instant.parse("2026-10-04T12:00:00Z");
    AppUser user;AuthService auth;SessionRegistry registry;PublicKey publicKey;
    @BeforeEach void setup() throws Exception {
        when(clock.instant()).thenReturn(now);when(clock.millis()).thenAnswer(i -> clock.instant().toEpochMilli());
        when(cache.get(anyString())).thenAnswer(i -> stored.get(i.getArgument(0)));
        doAnswer(i -> {stored.put(i.getArgument(0),i.getArgument(1));return null;}).when(cache).put(anyString(),anyString(),anyLong());
        doAnswer(i -> {stored.remove(i.getArgument(0));return null;}).when(cache).delete(anyString());
        when(cache.increment(anyString(),anyLong())).thenReturn(1L);
        when(cache.consumeOtp(anyString(),anyString())).thenAnswer(i -> {
            String key=i.getArgument(0),expected=i.getArgument(1);
            if (!expected.equals(stored.get(key))) return false;stored.remove(key);return true;
        });
        user=AppUser.builder().id(UUID.randomUUID()).phoneNumber(phone).isActive(true).build();
        when(users.findByPhoneNumber(phone)).thenReturn(Optional.of(user));when(users.lockById(user.getId())).thenReturn(Optional.of(user));
        when(entitlements.requireActiveUser(user.getId())).thenReturn(user);when(entitlements.rolesFor(user.getId())).thenReturn(List.of(RoleName.CUSTOMER));
        registry=new SessionRegistry(cache,mapper,clock);
        auth=new AuthService(users,new UserProvisioner(users),events,cache,registry,entitlements,clock,new SimpleMeterRegistry(),audit);
        ReflectionTestUtils.setField(auth,"privateKeyResource",new ClassPathResource("certs/private.pem"));auth.init();
        String pem=new String(new ClassPathResource("certs/public.pem").getInputStream().readAllBytes(),StandardCharsets.UTF_8);
        publicKey=KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pem.replace("-----BEGIN PUBLIC KEY-----","").replace("-----END PUBLIC KEY-----","").replaceAll("\\s",""))));
        stored.put("OTP:"+phone+":LOGIN",code);
    }
    SessionRequest request(String os,String browser,String replacement) {return new SessionRequest(phone,code,"Test device",os,browser,replacement);}
    Claims claims(String token) {return Jwts.parserBuilder().setSigningKey(publicKey).setClock(() -> Date.from(clock.instant())).build().parseClaimsJws(token).getBody();}
    String login() {return auth.createSession(request("macOS","Chromium",null)).token();}
    SessionInfo session(OtpPurpose purpose,String os,String browser,Instant expiry) {
        return SessionInfo.builder().sessionId(UUID.randomUUID().toString()).deviceInfo("Test device").purpose(purpose).os(os).browser(browser)
            .createdAt(now.minus(Duration.ofDays(1))).absoluteExpiresAt(expiry).lastActive(now.toEpochMilli()).build();
    }
    void save(List<SessionInfo> rows) throws Exception {stored.put("USER_SESSIONS:"+user.getId(),mapper.writeValueAsString(rows));}
    ResponseStatusException refuses(int status,Runnable work) {var ex=assertThrows(ResponseStatusException.class,work::run);assertEquals(status,ex.getStatusCode().value());return ex;}
    @Test void firstSignInCreatesOneAccountAndOnlyComputedEverydayRoles() {
        when(users.findByPhoneNumber(phone)).thenReturn(Optional.empty());when(users.saveAndFlush(any())).thenReturn(user);
        var result=auth.createSession(request("macOS","Chromium",null));assertTrue(result.newUser());
        assertEquals(List.of("CUSTOMER"),claims(result.token()).get("roles"));verify(users).saveAndFlush(any());
        assertEquals(0,claims(result.token()).get("ev",Number.class).longValue());
    }
    @Test void oneEverydayTokenCarriesAllComputedRolesAndNeverAdministrator() {
        when(entitlements.isAdministrator(user.getId())).thenReturn(true);
        when(entitlements.rolesFor(user.getId())).thenReturn(List.of(RoleName.CUSTOMER,RoleName.DELIVERY,RoleName.RESTAURANT,RoleName.BUSINESS));
        var c=claims(login());assertEquals(List.of("CUSTOMER","DELIVERY","RESTAURANT","BUSINESS"),c.get("roles"));
        assertEquals(user.getId().toString(),c.getSubject());assertEquals(now,c.getIssuedAt().toInstant());
        verify(users,never()).saveAndFlush(any());
    }
    @Test void invalidCodeNeverCreatesAnAccountOrSession() {
        stored.put("OTP:"+phone+":LOGIN","654321");refuses(401,this::login);
        verify(users,never()).saveAndFlush(any());assertNull(stored.get("USER_SESSIONS:"+user.getId()));
    }
    @Test void loginCodeIsStoredForFiveMinutes() {
        stored.clear();auth.initiateLogin(phone);
        verify(cache).put(eq("OTP:"+phone+":LOGIN"),matches("[0-9]{6}"),eq(5L));
    }
    @Test void expiredCodeIsRefusedAndCreatesNoSession() {
        stored.remove("OTP:"+phone+":LOGIN"); // Redis dropped the key when its five minutes ran out
        var ex=refuses(401,this::login);assertEquals("Invalid or expired code",ex.getReason());
        verify(users,never()).saveAndFlush(any());assertNull(stored.get("USER_SESSIONS:"+user.getId()));
    }
    @Test void inactiveAccountCannotReceiveATokenOrBeReactivated() {
        user.setActive(false);refuses(401,this::login);assertNull(stored.get("USER_SESSIONS:"+user.getId()));
        verify(users,never()).saveAndFlush(any());
    }
    @Test void concurrentlyConsumedCodeCannotCreateAnotherSession() {
        when(cache.consumeOtp(anyString(),anyString())).thenReturn(false);refuses(401,this::login);
        assertNull(stored.get("USER_SESSIONS:"+user.getId()));
    }
    @Test void limitIsThreeAcrossPurposesAnd409PreservesCodeForExplicitReplacement() throws Exception {
        var rows=List.of(session(OtpPurpose.LOGIN,"iOS","Safari",now.plus(Duration.ofDays(20))),
            session(OtpPurpose.LOGIN,"Android","Chrome",now.plus(Duration.ofDays(20))),session(OtpPurpose.ADMIN,"Linux","Firefox",now.plus(Duration.ofHours(7))));
        save(rows);var failure=assertThrows(MaxSessionsReachedException.class,this::login);
        assertEquals(3,failure.getActiveSessions().size());assertEquals(code,stored.get("OTP:"+phone+":LOGIN"));
        verify(cache,never()).consumeOtp(anyString(),anyString());assertEquals(3,registry.list(user.getId()).size());
        String replaced=rows.get(0).getSessionId();var result=auth.createSession(request("macOS","Chromium",replaced));
        assertFalse(result.token().isBlank());assertEquals(3,registry.list(user.getId()).size());assertEquals("true",stored.get("BLACKLIST:SESSION:"+replaced));
    }
    @Test void sameDeviceRecoveryOnlyReplacesTheMatchingPurpose() throws Exception {
        var same=session(OtpPurpose.LOGIN,"macOS","Chromium",now.plus(Duration.ofDays(20)));
        var admin=session(OtpPurpose.ADMIN,"macOS","Chromium",now.plus(Duration.ofHours(7)));
        save(List.of(same,admin,session(OtpPurpose.LOGIN,"Android","Chrome",now.plus(Duration.ofDays(20)))));
        login();assertEquals("true",stored.get("BLACKLIST:SESSION:"+same.getSessionId()));
        assertNull(stored.get("BLACKLIST:SESSION:"+admin.getSessionId()));assertEquals(3,registry.list(user.getId()).size());
    }
    @Test void foreignReplacementCannotBlacklistAnotherPersonsSession() {
        String foreign=UUID.randomUUID().toString();refuses(404,()->auth.createSession(request("macOS","Chromium",foreign)));
        assertNull(stored.get("BLACKLIST:SESSION:"+foreign));verify(cache,never()).consumeOtp(anyString(),anyString());
    }
    @Test void refreshRetainsSidAndRecomputesRolesAndVersion() {
        String original=claims(login()).get("sessionId",String.class);user.setEntitlementsVersion(3);
        when(entitlements.rolesFor(user.getId())).thenReturn(List.of(RoleName.CUSTOMER,RoleName.DELIVERY));
        var next=claims(auth.refresh(user.getId(),original).token());assertEquals(original,next.get("sessionId"));
        assertEquals(3,next.get("ev",Number.class).longValue());assertEquals(List.of("CUSTOMER","DELIVERY"),next.get("roles"));
    }
    @Test void blacklistedSessionCannotRefresh() {
        String sid=claims(login()).get("sessionId",String.class);stored.put("BLACKLIST:SESSION:"+sid,"true");
        refuses(401,()->auth.refresh(user.getId(),sid));
    }
    @Test void refreshCannotExtendPastAbsoluteExpiryAndExpiredSessionIsRefused() throws Exception {
        var s=session(OtpPurpose.LOGIN,"macOS","Chromium",now.plus(Duration.ofMinutes(20)));save(List.of(s));
        assertEquals(s.getAbsoluteExpiresAt(),claims(auth.refresh(user.getId(),s.getSessionId()).token()).getExpiration().toInstant());
        when(clock.instant()).thenReturn(s.getAbsoluteExpiresAt());refuses(401,()->auth.refresh(user.getId(),s.getSessionId()));
    }
    @Test void sessionListTtlUsesLongestAbsoluteLifetime() {
        login();verify(cache).put(eq("USER_SESSIONS:"+user.getId()),anyString(),eq(43_200L));
        verify(cache).putVersions(Map.of("ENTITLEMENTS_VERSION:"+user.getId(),"0"),Duration.ofDays(2));
    }
    @Test void staffAssignmentIsRequiredForBothAdminEndpoints() {
        String sid=claims(login()).get("sessionId",String.class);
        refuses(403,()->auth.initiateAdminOtp(user.getId(),sid));
        refuses(403,()->auth.createAdminSession(user.getId(),sid,new AdminSessionRequest(code,null)));
        verifyNoInteractions(events,audit);
    }
    @Test void adminRequiresItsOwnOtpAndProducesAnAuditedEightHourSession() {
        String sid=claims(login()).get("sessionId",String.class);when(entitlements.isAdministrator(user.getId())).thenReturn(true);
        stored.put("OTP:"+phone+":LOGIN",code);
        refuses(401,()->auth.createAdminSession(user.getId(),sid,new AdminSessionRequest(code,null)));
        stored.put("OTP:"+phone+":ADMIN",code);
        var token=claims(auth.createAdminSession(user.getId(),sid,new AdminSessionRequest(code,null)).token());
        assertEquals(List.of("ADMIN"),token.get("roles"));assertNotEquals(sid,token.get("sessionId"));
        assertEquals(now.plus(Duration.ofHours(8)),token.getExpiration().toInstant());assertEquals(2,registry.list(user.getId()).size());
        assertNull(stored.get("BLACKLIST:SESSION:"+sid));
        verify(audit).record(now,user.getId(),"ADMIN",AuditAction.ADMIN_STEP_UP,"USER",user.getId(),null,null,Map.of());
    }
    @Test void adminRefreshDoesNotExtendEightHourLifetimeOrRetainRemovedStaffAccess() throws Exception {
        var admin=session(OtpPurpose.ADMIN,"macOS","Chromium",now.plus(Duration.ofMinutes(1)));save(List.of(admin));
        when(entitlements.isAdministrator(user.getId())).thenReturn(true);
        assertEquals(admin.getAbsoluteExpiresAt(),claims(auth.refresh(user.getId(),admin.getSessionId()).token()).getExpiration().toInstant());
        when(entitlements.isAdministrator(user.getId())).thenReturn(false);refuses(403,()->auth.refresh(user.getId(),admin.getSessionId()));
    }
    @Test void deletingAnUnownedSessionIsRefusedWithoutARevocationWrite() {
        String foreign=UUID.randomUUID().toString();refuses(404,()->auth.removeSession(user.getId(),foreign));
        assertNull(stored.get("BLACKLIST:SESSION:"+foreign));
    }
    @Test void logoutIsIdempotentAndOnlyRevokesTheAuthenticatedSession() {
        String sid=claims(login()).get("sessionId",String.class);auth.logout(user.getId(),sid);auth.logout(user.getId(),sid);
        assertEquals("true",stored.get("BLACKLIST:SESSION:"+sid));assertTrue(registry.list(user.getId()).isEmpty());
    }
    @Test void malformedSessionCacheFailsClosed() {
        stored.put("USER_SESSIONS:"+user.getId(),"[] broken");refuses(503,this::login);
        verify(cache,never()).consumeOtp(anyString(),anyString());
    }
    @Test void ambiguousSessionStateCannotRefreshOrConsumeANewLoginCode() throws Exception {
        var row=session(OtpPurpose.LOGIN,"macOS","Chromium",now.plusSeconds(60));
        String json=mapper.writeValueAsString(List.of(row));
        stored.put("USER_SESSIONS:"+user.getId(),json.replace("\"purpose\":\"LOGIN\"","\"purpose\":\"ADMIN\",\"purpose\":\"LOGIN\""));
        refuses(503,()->auth.refresh(user.getId(),row.getSessionId()));
        refuses(503,this::login);
        assertEquals(code,stored.get("OTP:"+phone+":LOGIN"));
        verify(cache,never()).consumeOtp(anyString(),anyString());
        verify(cache,never()).put(anyString(),anyString(),anyLong());
        verify(cache,never()).putVersions(anyMap(),any());
    }
    @Test void mapsSignInAtTheLimitAsksInsteadOfEndingASameDeviceSession() throws Exception {
        var temp=java.nio.file.Files.createTempDirectory("maps-auth-fixture-");
        try {
            var tokens=new MapsSessionTokens(MapsSessionTokensTest.configured(temp,KeyPairGenerator.getInstance("RSA").generateKeyPair()),clock,new org.springframework.core.io.DefaultResourceLoader());
            var provider=mock(org.springframework.beans.factory.ObjectProvider.class);
            when(provider.getIfAvailable()).thenReturn(tokens);ReflectionTestUtils.setField(auth,"mapsSessionTokens",provider);
            var same=session(OtpPurpose.LOGIN,"macOS","Chromium",now.plus(Duration.ofDays(20)));
            var kept=session(OtpPurpose.LOGIN,"iOS","Safari",now.plus(Duration.ofDays(20)));
            save(List.of(same,kept,session(OtpPurpose.LOGIN,"Android","Chrome",now.plus(Duration.ofDays(20)))));
            var failure=assertThrows(MaxSessionsReachedException.class,()->auth.createMapsSession(request("macOS","Chromium",null)));
            assertEquals(3,failure.getActiveSessions().size());assertNull(stored.get("BLACKLIST:SESSION:"+same.getSessionId()));
            assertEquals(code,stored.get("OTP:"+phone+":LOGIN"));assertEquals(3,registry.list(user.getId()).size());
            auth.createMapsSession(request("macOS","Chromium",same.getSessionId()));
            assertEquals("true",stored.get("BLACKLIST:SESSION:"+same.getSessionId()));assertNull(stored.get("BLACKLIST:SESSION:"+kept.getSessionId()));
            assertEquals(3,registry.list(user.getId()).size());
        } finally {
            try(var paths=java.nio.file.Files.walk(temp)){for(var path:paths.sorted(java.util.Comparator.reverseOrder()).toList())java.nio.file.Files.delete(path);}
        }
    }
    @Test void mapsLoginAndRefreshUseScopedCredentialsAndKeepTheSamePersonSession() throws Exception {
        var temp=java.nio.file.Files.createTempDirectory("maps-auth-fixture-");
        try {
            var pair=KeyPairGenerator.getInstance("RSA").generateKeyPair();
            var properties=MapsSessionTokensTest.configured(temp,pair);
            var tokens=new MapsSessionTokens(properties,clock,new org.springframework.core.io.DefaultResourceLoader());
            var provider=mock(org.springframework.beans.factory.ObjectProvider.class);
            when(provider.getIfAvailable()).thenReturn(tokens);ReflectionTestUtils.setField(auth,"mapsSessionTokens",provider);
            String credential=auth.createMapsSession(request("macOS","Chromium",null)).token();
            var current=tokens.verify(credential,false);assertEquals(user.getId(),current.subject());
            String next=auth.refreshMaps(credential).token();assertEquals(current.sessionId(),tokens.verify(next,false).sessionId());
            user.setEntitlementsVersion(user.getEntitlementsVersion()+1);
            assertThrows(com.fooddelivery.identity.exception.MapsIdentityException.class,()->auth.refreshMaps(next));
            user.setEntitlementsVersion(user.getEntitlementsVersion()-1);
            auth.logoutMaps(next);assertEquals("true",stored.get("BLACKLIST:SESSION:"+current.sessionId()));
            assertThrows(org.springframework.web.server.ResponseStatusException.class,()->auth.refreshMaps(next));
        } finally {
            try(var paths=java.nio.file.Files.walk(temp)){for(var path:paths.sorted(java.util.Comparator.reverseOrder()).toList())java.nio.file.Files.delete(path);}
        }
    }
    @Test void mockedRateLimitsHaveIndependentKeysAndDenyBeforeSensitiveActions() {
        when(cache.increment("RATELIMIT:INITIATE:"+phone,10)).thenReturn(11L);refuses(429,()->auth.initiateLogin(phone));verifyNoInteractions(events);
        when(cache.increment("RATELIMIT:VERIFY:"+phone,5)).thenReturn(6L);refuses(429,this::login);
        assertNull(stored.get("OTP:"+phone+":LOGIN"));verify(users,never()).saveAndFlush(any());
    }
    @Test void mapsVerificationRefusalRetainsItsWindowAndDeletesCodeWithoutChangingFoodDelivery() throws Exception {
        var temp=java.nio.file.Files.createTempDirectory("maps-auth-fixture-");
        try {
            var tokens=new MapsSessionTokens(MapsSessionTokensTest.configured(temp,KeyPairGenerator.getInstance("RSA").generateKeyPair()),clock,new org.springframework.core.io.DefaultResourceLoader());
            var provider=mock(org.springframework.beans.factory.ObjectProvider.class);
            when(provider.getIfAvailable()).thenReturn(tokens);ReflectionTestUtils.setField(auth,"mapsSessionTokens",provider);
            when(cache.increment("RATELIMIT:VERIFY:"+phone,5)).thenReturn(6L);
            assertEquals("300",refuses(429,()->auth.createMapsSession(request("macOS","Chromium",null))).getHeaders().getFirst("Retry-After"));
            assertNull(stored.get("OTP:"+phone+":LOGIN"));verify(cache,never()).consumeOtp(anyString(),anyString());
            verify(users,never()).saveAndFlush(any());
            stored.put("OTP:"+phone+":LOGIN",code);
            assertNull(refuses(429,this::login).getHeaders().getFirst("Retry-After"));
            assertNull(stored.get("OTP:"+phone+":LOGIN"));
        } finally {
            try(var paths=java.nio.file.Files.walk(temp)){for(var path:paths.sorted(java.util.Comparator.reverseOrder()).toList())java.nio.file.Files.delete(path);}
        }
    }
    @Test void mapsRefreshRefusalRetainsItsWindowWithoutTouchingSessionOrChangingFoodDelivery() throws Exception {
        var temp=java.nio.file.Files.createTempDirectory("maps-auth-fixture-");
        try {
            var tokens=new MapsSessionTokens(MapsSessionTokensTest.configured(temp,KeyPairGenerator.getInstance("RSA").generateKeyPair()),clock,new org.springframework.core.io.DefaultResourceLoader());
            var provider=mock(org.springframework.beans.factory.ObjectProvider.class);
            when(provider.getIfAvailable()).thenReturn(tokens);ReflectionTestUtils.setField(auth,"mapsSessionTokens",provider);
            String credential=auth.createMapsSession(request("macOS","Chromium",null)).token();
            String sid=tokens.verify(credential,false).sessionId(),before=stored.get("USER_SESSIONS:"+user.getId());
            when(cache.increment(com.fooddelivery.common.constants.RedisKeyConstants.RL_SESSION_REFRESH+sid,60)).thenReturn(31L);
            assertEquals("3600",refuses(429,()->auth.refreshMaps(credential)).getHeaders().getFirst("Retry-After"));
            assertEquals(before,stored.get("USER_SESSIONS:"+user.getId()));
            assertNull(refuses(429,()->auth.refresh(user.getId(),sid)).getHeaders().getFirst("Retry-After"));
        } finally {
            try(var paths=java.nio.file.Files.walk(temp)){for(var path:paths.sorted(java.util.Comparator.reverseOrder()).toList())java.nio.file.Files.delete(path);}
        }
    }
    @Test void mapsNotificationBudgetIsSharedAndFailsBeforeAnyNotification() {
        when(cache.increment("RATELIMIT:MAPS:OTP:GLOBAL",1440)).thenReturn(1001L);
        assertEquals("86400", refuses(429,()->auth.initiateMapsLogin(phone)).getHeaders().getFirst("Retry-After"));verifyNoInteractions(events);
        when(cache.increment("RATELIMIT:MAPS:OTP:GLOBAL",1440)).thenReturn(null);
        refuses(503,()->auth.initiateMapsLogin(phone));verifyNoInteractions(events);
    }
    @Test void mapsRequestsRefusedForOnePhoneDoNotSpendTheSharedNotificationBudget() {
        var counts=new HashMap<String,Long>();
        when(cache.increment(anyString(),anyLong())).thenAnswer(i->counts.merge(i.getArgument(0),1L,Long::sum));
        ReflectionTestUtils.setField(auth,"mapsOtpMaxPerDay",12);
        for(int i=0;i<10;i++)auth.initiateMapsLogin(phone);
        for(int i=0;i<100;i++)assertEquals("600", refuses(429,()->auth.initiateMapsLogin(phone)).getHeaders().getFirst("Retry-After"));
        assertEquals(10L,counts.get("RATELIMIT:MAPS:OTP:GLOBAL"),"only delivered notifications spend the shared budget");
        verify(events,times(10)).publishNotificationEvent(eq(phone),eq("SMS"),anyString());
        auth.initiateMapsLogin("8999000000");
        verify(events).publishNotificationEvent(eq("8999000000"),eq("SMS"),anyString());
    }
}
