package com.fooddelivery.identity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotNull;

public class SessionInfo {
    @NotNull
    private String sessionId;
    @NotNull
    private String deviceInfo;
    @NotNull
    private String os;
    @NotNull
    private String browser;
    @Schema(requiredMode = RequiredMode.REQUIRED)
    private long lastActive;
    @NotNull
    private String serviceName;


    @java.lang.SuppressWarnings("all")
    public static class SessionInfoBuilder {
        @java.lang.SuppressWarnings("all")
        private String sessionId;
        @java.lang.SuppressWarnings("all")
        private String deviceInfo;
        @java.lang.SuppressWarnings("all")
        private String os;
        @java.lang.SuppressWarnings("all")
        private String browser;
        @java.lang.SuppressWarnings("all")
        private long lastActive;
        @java.lang.SuppressWarnings("all")
        private String serviceName;

        @java.lang.SuppressWarnings("all")
        SessionInfoBuilder() {
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public SessionInfo.SessionInfoBuilder sessionId(final String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public SessionInfo.SessionInfoBuilder deviceInfo(final String deviceInfo) {
            this.deviceInfo = deviceInfo;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public SessionInfo.SessionInfoBuilder os(final String os) {
            this.os = os;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public SessionInfo.SessionInfoBuilder browser(final String browser) {
            this.browser = browser;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public SessionInfo.SessionInfoBuilder lastActive(final long lastActive) {
            this.lastActive = lastActive;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public SessionInfo.SessionInfoBuilder serviceName(final String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public SessionInfo build() {
            return new SessionInfo(this.sessionId, this.deviceInfo, this.os, this.browser, this.lastActive, this.serviceName);
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public java.lang.String toString() {
            return "SessionInfo.SessionInfoBuilder(sessionId=" + this.sessionId + ", deviceInfo=" + this.deviceInfo + ", os=" + this.os + ", browser=" + this.browser + ", lastActive=" + this.lastActive + ", serviceName=" + this.serviceName + ")";
        }
    }

    @java.lang.SuppressWarnings("all")
    public static SessionInfo.SessionInfoBuilder builder() {
        return new SessionInfo.SessionInfoBuilder();
    }

    @java.lang.SuppressWarnings("all")
    public String getSessionId() {
        return this.sessionId;
    }

    @java.lang.SuppressWarnings("all")
    public String getDeviceInfo() {
        return this.deviceInfo;
    }

    @java.lang.SuppressWarnings("all")
    public String getOs() {
        return this.os;
    }

    @java.lang.SuppressWarnings("all")
    public String getBrowser() {
        return this.browser;
    }

    @java.lang.SuppressWarnings("all")
    public long getLastActive() {
        return this.lastActive;
    }

    @java.lang.SuppressWarnings("all")
    public String getServiceName() {
        return this.serviceName;
    }

    @java.lang.SuppressWarnings("all")
    public void setSessionId(final String sessionId) {
        this.sessionId = sessionId;
    }

    @java.lang.SuppressWarnings("all")
    public void setDeviceInfo(final String deviceInfo) {
        this.deviceInfo = deviceInfo;
    }

    @java.lang.SuppressWarnings("all")
    public void setOs(final String os) {
        this.os = os;
    }

    @java.lang.SuppressWarnings("all")
    public void setBrowser(final String browser) {
        this.browser = browser;
    }

    @java.lang.SuppressWarnings("all")
    public void setLastActive(final long lastActive) {
        this.lastActive = lastActive;
    }

    @java.lang.SuppressWarnings("all")
    public void setServiceName(final String serviceName) {
        this.serviceName = serviceName;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public boolean equals(final java.lang.Object o) {
        if (o == this) return true;
        if (!(o instanceof SessionInfo)) return false;
        final SessionInfo other = (SessionInfo) o;
        if (!other.canEqual((java.lang.Object) this)) return false;
        if (this.getLastActive() != other.getLastActive()) return false;
        final java.lang.Object this$sessionId = this.getSessionId();
        final java.lang.Object other$sessionId = other.getSessionId();
        if (this$sessionId == null ? other$sessionId != null : !this$sessionId.equals(other$sessionId)) return false;
        final java.lang.Object this$deviceInfo = this.getDeviceInfo();
        final java.lang.Object other$deviceInfo = other.getDeviceInfo();
        if (this$deviceInfo == null ? other$deviceInfo != null : !this$deviceInfo.equals(other$deviceInfo)) return false;
        final java.lang.Object this$os = this.getOs();
        final java.lang.Object other$os = other.getOs();
        if (this$os == null ? other$os != null : !this$os.equals(other$os)) return false;
        final java.lang.Object this$browser = this.getBrowser();
        final java.lang.Object other$browser = other.getBrowser();
        if (this$browser == null ? other$browser != null : !this$browser.equals(other$browser)) return false;
        final java.lang.Object this$serviceName = this.getServiceName();
        final java.lang.Object other$serviceName = other.getServiceName();
        if (this$serviceName == null ? other$serviceName != null : !this$serviceName.equals(other$serviceName)) return false;
        return true;
    }

    @java.lang.SuppressWarnings("all")
    protected boolean canEqual(final java.lang.Object other) {
        return other instanceof SessionInfo;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final long $lastActive = this.getLastActive();
        result = result * PRIME + (int) ($lastActive >>> 32 ^ $lastActive);
        final java.lang.Object $sessionId = this.getSessionId();
        result = result * PRIME + ($sessionId == null ? 43 : $sessionId.hashCode());
        final java.lang.Object $deviceInfo = this.getDeviceInfo();
        result = result * PRIME + ($deviceInfo == null ? 43 : $deviceInfo.hashCode());
        final java.lang.Object $os = this.getOs();
        result = result * PRIME + ($os == null ? 43 : $os.hashCode());
        final java.lang.Object $browser = this.getBrowser();
        result = result * PRIME + ($browser == null ? 43 : $browser.hashCode());
        final java.lang.Object $serviceName = this.getServiceName();
        result = result * PRIME + ($serviceName == null ? 43 : $serviceName.hashCode());
        return result;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public java.lang.String toString() {
        return "SessionInfo(sessionId=" + this.getSessionId() + ", deviceInfo=" + this.getDeviceInfo() + ", os=" + this.getOs() + ", browser=" + this.getBrowser() + ", lastActive=" + this.getLastActive() + ", serviceName=" + this.getServiceName() + ")";
    }

    @java.lang.SuppressWarnings("all")
    public SessionInfo() {
    }

    @java.lang.SuppressWarnings("all")
    public SessionInfo(final String sessionId, final String deviceInfo, final String os, final String browser, final long lastActive, final String serviceName) {
        this.sessionId = sessionId;
        this.deviceInfo = deviceInfo;
        this.os = os;
        this.browser = browser;
        this.lastActive = lastActive;
        this.serviceName = serviceName;
    }
}
