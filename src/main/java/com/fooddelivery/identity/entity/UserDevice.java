package com.fooddelivery.identity.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_devices")
public class UserDevice {
    @Id
    private String sessionId; // UUID representing the session and Primary Key
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private AppUser user;
    @Column(name = "device_id")
    private String deviceId;
    @Column(name = "device_model")
    private String deviceModel;
    @Column(name = "portal")
    private String portal;
    @CreationTimestamp
    @Column(name = "login_time")
    private LocalDateTime loginTime;


    @java.lang.SuppressWarnings("all")
    public static class UserDeviceBuilder {
        @java.lang.SuppressWarnings("all")
        private String sessionId;
        @java.lang.SuppressWarnings("all")
        private AppUser user;
        @java.lang.SuppressWarnings("all")
        private String deviceId;
        @java.lang.SuppressWarnings("all")
        private String deviceModel;
        @java.lang.SuppressWarnings("all")
        private String portal;
        @java.lang.SuppressWarnings("all")
        private LocalDateTime loginTime;

        @java.lang.SuppressWarnings("all")
        UserDeviceBuilder() {
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public UserDevice.UserDeviceBuilder sessionId(final String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @JsonIgnore
        @java.lang.SuppressWarnings("all")
        public UserDevice.UserDeviceBuilder user(final AppUser user) {
            this.user = user;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public UserDevice.UserDeviceBuilder deviceId(final String deviceId) {
            this.deviceId = deviceId;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public UserDevice.UserDeviceBuilder deviceModel(final String deviceModel) {
            this.deviceModel = deviceModel;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public UserDevice.UserDeviceBuilder portal(final String portal) {
            this.portal = portal;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public UserDevice.UserDeviceBuilder loginTime(final LocalDateTime loginTime) {
            this.loginTime = loginTime;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public UserDevice build() {
            return new UserDevice(this.sessionId, this.user, this.deviceId, this.deviceModel, this.portal, this.loginTime);
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public java.lang.String toString() {
            return "UserDevice.UserDeviceBuilder(sessionId=" + this.sessionId + ", user=" + this.user + ", deviceId=" + this.deviceId + ", deviceModel=" + this.deviceModel + ", portal=" + this.portal + ", loginTime=" + this.loginTime + ")";
        }
    }

    @java.lang.SuppressWarnings("all")
    public static UserDevice.UserDeviceBuilder builder() {
        return new UserDevice.UserDeviceBuilder();
    }

    @java.lang.SuppressWarnings("all")
    public String getSessionId() {
        return this.sessionId;
    }

    @java.lang.SuppressWarnings("all")
    public AppUser getUser() {
        return this.user;
    }

    @java.lang.SuppressWarnings("all")
    public String getDeviceId() {
        return this.deviceId;
    }

    @java.lang.SuppressWarnings("all")
    public String getDeviceModel() {
        return this.deviceModel;
    }

    @java.lang.SuppressWarnings("all")
    public String getPortal() {
        return this.portal;
    }

    @java.lang.SuppressWarnings("all")
    public LocalDateTime getLoginTime() {
        return this.loginTime;
    }

    @java.lang.SuppressWarnings("all")
    public void setSessionId(final String sessionId) {
        this.sessionId = sessionId;
    }

    @JsonIgnore
    @java.lang.SuppressWarnings("all")
    public void setUser(final AppUser user) {
        this.user = user;
    }

    @java.lang.SuppressWarnings("all")
    public void setDeviceId(final String deviceId) {
        this.deviceId = deviceId;
    }

    @java.lang.SuppressWarnings("all")
    public void setDeviceModel(final String deviceModel) {
        this.deviceModel = deviceModel;
    }

    @java.lang.SuppressWarnings("all")
    public void setPortal(final String portal) {
        this.portal = portal;
    }

    @java.lang.SuppressWarnings("all")
    public void setLoginTime(final LocalDateTime loginTime) {
        this.loginTime = loginTime;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public boolean equals(final java.lang.Object o) {
        if (o == this) return true;
        if (!(o instanceof UserDevice)) return false;
        final UserDevice other = (UserDevice) o;
        if (!other.canEqual((java.lang.Object) this)) return false;
        final java.lang.Object this$sessionId = this.getSessionId();
        final java.lang.Object other$sessionId = other.getSessionId();
        if (this$sessionId == null ? other$sessionId != null : !this$sessionId.equals(other$sessionId)) return false;
        final java.lang.Object this$user = this.getUser();
        final java.lang.Object other$user = other.getUser();
        if (this$user == null ? other$user != null : !this$user.equals(other$user)) return false;
        final java.lang.Object this$deviceId = this.getDeviceId();
        final java.lang.Object other$deviceId = other.getDeviceId();
        if (this$deviceId == null ? other$deviceId != null : !this$deviceId.equals(other$deviceId)) return false;
        final java.lang.Object this$deviceModel = this.getDeviceModel();
        final java.lang.Object other$deviceModel = other.getDeviceModel();
        if (this$deviceModel == null ? other$deviceModel != null : !this$deviceModel.equals(other$deviceModel)) return false;
        final java.lang.Object this$portal = this.getPortal();
        final java.lang.Object other$portal = other.getPortal();
        if (this$portal == null ? other$portal != null : !this$portal.equals(other$portal)) return false;
        final java.lang.Object this$loginTime = this.getLoginTime();
        final java.lang.Object other$loginTime = other.getLoginTime();
        if (this$loginTime == null ? other$loginTime != null : !this$loginTime.equals(other$loginTime)) return false;
        return true;
    }

    @java.lang.SuppressWarnings("all")
    protected boolean canEqual(final java.lang.Object other) {
        return other instanceof UserDevice;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final java.lang.Object $sessionId = this.getSessionId();
        result = result * PRIME + ($sessionId == null ? 43 : $sessionId.hashCode());
        final java.lang.Object $user = this.getUser();
        result = result * PRIME + ($user == null ? 43 : $user.hashCode());
        final java.lang.Object $deviceId = this.getDeviceId();
        result = result * PRIME + ($deviceId == null ? 43 : $deviceId.hashCode());
        final java.lang.Object $deviceModel = this.getDeviceModel();
        result = result * PRIME + ($deviceModel == null ? 43 : $deviceModel.hashCode());
        final java.lang.Object $portal = this.getPortal();
        result = result * PRIME + ($portal == null ? 43 : $portal.hashCode());
        final java.lang.Object $loginTime = this.getLoginTime();
        result = result * PRIME + ($loginTime == null ? 43 : $loginTime.hashCode());
        return result;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public java.lang.String toString() {
        return "UserDevice(sessionId=" + this.getSessionId() + ", user=" + this.getUser() + ", deviceId=" + this.getDeviceId() + ", deviceModel=" + this.getDeviceModel() + ", portal=" + this.getPortal() + ", loginTime=" + this.getLoginTime() + ")";
    }

    @java.lang.SuppressWarnings("all")
    public UserDevice() {
    }

    @java.lang.SuppressWarnings("all")
    public UserDevice(final String sessionId, final AppUser user, final String deviceId, final String deviceModel, final String portal, final LocalDateTime loginTime) {
        this.sessionId = sessionId;
        this.user = user;
        this.deviceId = deviceId;
        this.deviceModel = deviceModel;
        this.portal = portal;
        this.loginTime = loginTime;
    }
}
