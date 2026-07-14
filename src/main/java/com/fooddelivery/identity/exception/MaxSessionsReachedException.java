package com.fooddelivery.identity.exception;

import com.fooddelivery.identity.dto.SessionInfo;
import lombok.Getter;

import java.util.List;

@Getter
public class MaxSessionsReachedException extends RuntimeException {
    private final List<SessionInfo> activeSessions;

    public MaxSessionsReachedException(String message, List<SessionInfo> activeSessions) {
        super(message);
        this.activeSessions = activeSessions;
    }
}
