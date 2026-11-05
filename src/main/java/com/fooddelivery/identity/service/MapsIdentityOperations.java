package com.fooddelivery.identity.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fooddelivery.identity.config.MapsIdentityProperties;
import com.fooddelivery.identity.dto.AuthRequests.SessionRequest;
import com.fooddelivery.identity.exception.MapsIdentityException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import jakarta.validation.Validator;
import static com.fooddelivery.identity.exception.MapsIdentityException.Code.*;

/** Small operation facade: peer authentication/HTTP I/O are outside account/session policy. */
public final class MapsIdentityOperations {
    public static final Set<String> OPERATIONS = Set.of("otp", "session", "validate", "refresh", "logout");
    private static final ObjectReader JSON = new ObjectMapper().readerFor(JsonNode.class)
            .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final AuthService auth;
    private final MapsCurrentSessionService current;
    private final MapsIdentityProperties properties;
    private final Validator validator;

    public MapsIdentityOperations(AuthService auth, MapsCurrentSessionService current,
                                  MapsIdentityProperties properties, Validator validator) {
        this.auth = auth; this.current = current; this.properties = properties; this.validator = validator;
    }

    public Map<String, Object> execute(String operation, byte[] bytes) {
        String nonce = null;
        try {
            JsonNode request;
            try {
                String text = java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                        .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
                request = JSON.readValue(text);
            } catch (Exception ex) { throw new MapsIdentityException(INVALID_REQUEST); }
            if (!request.isObject()) throw new MapsIdentityException(INVALID_REQUEST);
            if (!request.path("version").isIntegralNumber() || !request.path("version").canConvertToInt())
                throw new MapsIdentityException(INVALID_REQUEST);
            if (request.path("version").intValue() != 1) throw new MapsIdentityException(UNSUPPORTED_VERSION);
            nonce = string(request, "nonce", false);
            if (!nonce.matches("[A-Za-z0-9_-]{43}") || !properties.getAudience().equals(string(request, "audience", false)))
                throw new MapsIdentityException(INVALID_REQUEST);
            var allowed = new HashSet<>(Set.of("version", "audience", "nonce"));
            switch (operation) {
                case "otp" -> allowed.add("phone_number");
                case "session" -> allowed.addAll(Set.of("phone_number", "otp", "device_info", "os", "browser", "replace_session_id"));
                case "validate", "refresh", "logout" -> allowed.add("credential");
                default -> throw new MapsIdentityException(NOT_FOUND);
            }
            var fields = request.fieldNames();
            while (fields.hasNext()) if (!allowed.contains(fields.next())) throw new MapsIdentityException(INVALID_REQUEST);
            switch (operation) {
                case "otp" -> {
                    String phone = string(request, "phone_number", false);
                    if (!phone.matches("[0-9]{10}")) throw new MapsIdentityException(INVALID_REQUEST);
                    auth.initiateMapsLogin(phone);
                }
                case "session" -> {
                    var input = new SessionRequest(string(request, "phone_number", false), string(request, "otp", false),
                            string(request, "device_info", true), string(request, "os", true), string(request, "browser", true),
                            string(request, "replace_session_id", true));
                    if (!validator.validate(input).isEmpty()) throw new MapsIdentityException(INVALID_REQUEST);
                    if (input.replaceSessionId() != null && !UUID.fromString(input.replaceSessionId()).toString().equals(input.replaceSessionId()))
                        throw new MapsIdentityException(INVALID_REQUEST);
                    return credential(nonce, auth.createMapsSession(input).token());
                }
                case "validate" -> { return current.validate(credential(request), nonce); }
                case "refresh" -> { return credential(nonce, auth.refreshMaps(credential(request)).token()); }
                case "logout" -> auth.logoutMaps(credential(request));
                default -> throw new MapsIdentityException(NOT_FOUND);
            }
            return Map.of("version", 1, "nonce", nonce, "completed", true);
        } catch (MapsIdentityException ex) {
            // Preserve request binding for completed decisions; malformed/untrusted requests need
            // no nonce echo. The HTTP adapter assigns the status separately from the fixed code.
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw new MapsIdentityException(INVALID_REQUEST);
        }
    }
    private static String credential(JsonNode request) {
        String value = string(request, "credential", false);
        if (value.length() > 4096) throw new MapsIdentityException(INVALID_REQUEST);
        return value;
    }
    private static Map<String, Object> credential(String nonce, String credential) {
        return Map.of("version", 1, "nonce", nonce, "credential", credential);
    }
    private static String string(JsonNode node, String field, boolean optional) {
        JsonNode value = node.get(field);
        if (optional && (value == null || value.isNull())) return null;
        if (value == null || !value.isTextual()) throw new MapsIdentityException(INVALID_REQUEST);
        return value.textValue();
    }
}
