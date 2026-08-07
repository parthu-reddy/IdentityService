package com.fooddelivery.identity.exception;

import com.fooddelivery.identity.dto.SessionInfo;
import java.util.List;

public class MaxSessionsReachedException extends RuntimeException {
    private final List<SessionInfo> activeSessions;

    public MaxSessionsReachedException(String message, List<SessionInfo> activeSessions) {
        super(message);
        this.activeSessions = activeSessions;
    }

    @java.lang.SuppressWarnings("all")
    public List<SessionInfo> getActiveSessions() {
        return this.activeSessions;
    }
}
