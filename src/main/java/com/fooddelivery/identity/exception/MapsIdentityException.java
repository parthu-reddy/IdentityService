package com.fooddelivery.identity.exception;

/** Fixed public classifications; never attach credentials, payloads or infrastructure causes. */
public final class MapsIdentityException extends RuntimeException {
    public enum Code {
        INVALID_REQUEST(400), UNSUPPORTED_VERSION(400), INACTIVE(401), PEER_DENIED(403),
        NOT_FOUND(404), METHOD_NOT_ALLOWED(405), SESSION_LIMIT(409), TOO_LARGE(413), UNSUPPORTED_MEDIA_TYPE(415),
        OVERLOADED(429), UNAVAILABLE(503);
        private final int status;
        Code(int status) { this.status = status; }
        public int status() { return status; }
    }
    private final Code code;
    public MapsIdentityException(Code code) { super(code.name(), null, false, false); this.code = code; }
    public Code code() { return code; }
}
