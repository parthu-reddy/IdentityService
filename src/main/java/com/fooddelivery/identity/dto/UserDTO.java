package com.fooddelivery.identity.dto;

import java.util.List;
import java.util.UUID;
import com.fooddelivery.common.enums.RoleName;

public class UserDTO {
    private UUID id;
    private String phoneNumber;
    private List<RoleName> roles;
    private boolean isActive;

    @java.lang.SuppressWarnings("all")
    public static class UserDTOBuilder {
        @java.lang.SuppressWarnings("all")
        private UUID id;
        @java.lang.SuppressWarnings("all")
        private String phoneNumber;
        @java.lang.SuppressWarnings("all")
        private List<RoleName> roles;
        @java.lang.SuppressWarnings("all")
        private boolean isActive;

        @java.lang.SuppressWarnings("all")
        UserDTOBuilder() {
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public UserDTO.UserDTOBuilder id(final UUID id) {
            this.id = id;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public UserDTO.UserDTOBuilder phoneNumber(final String phoneNumber) {
            this.phoneNumber = phoneNumber;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public UserDTO.UserDTOBuilder roles(final List<RoleName> roles) {
            this.roles = roles;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public UserDTO.UserDTOBuilder isActive(final boolean isActive) {
            this.isActive = isActive;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public UserDTO build() {
            return new UserDTO(this.id, this.phoneNumber, this.roles, this.isActive);
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public java.lang.String toString() {
            return "UserDTO.UserDTOBuilder(id=" + this.id + ", phoneNumber=" + this.phoneNumber + ", roles=" + this.roles + ", isActive=" + this.isActive + ")";
        }
    }

    @java.lang.SuppressWarnings("all")
    public static UserDTO.UserDTOBuilder builder() {
        return new UserDTO.UserDTOBuilder();
    }

    @java.lang.SuppressWarnings("all")
    public UUID getId() {
        return this.id;
    }

    @java.lang.SuppressWarnings("all")
    public String getPhoneNumber() {
        return this.phoneNumber;
    }

    @java.lang.SuppressWarnings("all")
    public List<RoleName> getRoles() {
        return this.roles;
    }

    @java.lang.SuppressWarnings("all")
    public boolean isActive() {
        return this.isActive;
    }

    @java.lang.SuppressWarnings("all")
    public void setId(final UUID id) {
        this.id = id;
    }

    @java.lang.SuppressWarnings("all")
    public void setPhoneNumber(final String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    @java.lang.SuppressWarnings("all")
    public void setRoles(final List<RoleName> roles) {
        this.roles = roles;
    }

    @java.lang.SuppressWarnings("all")
    public void setActive(final boolean isActive) {
        this.isActive = isActive;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public boolean equals(final java.lang.Object o) {
        if (o == this) return true;
        if (!(o instanceof UserDTO)) return false;
        final UserDTO other = (UserDTO) o;
        if (!other.canEqual((java.lang.Object) this)) return false;
        final java.lang.Object this$id = this.getId();
        final java.lang.Object other$id = other.getId();
        if (this$id == null ? other$id != null : !this$id.equals(other$id)) return false;
        final java.lang.Object this$phoneNumber = this.getPhoneNumber();
        final java.lang.Object other$phoneNumber = other.getPhoneNumber();
        if (this$phoneNumber == null ? other$phoneNumber != null : !this$phoneNumber.equals(other$phoneNumber)) return false;
        final java.lang.Object this$roles = this.getRoles();
        final java.lang.Object other$roles = other.getRoles();
        if (this$roles == null ? other$roles != null : !this$roles.equals(other$roles)) return false;
        if (this.isActive() != other.isActive()) return false;
        return true;
    }

    @java.lang.SuppressWarnings("all")
    protected boolean canEqual(final java.lang.Object other) {
        return other instanceof UserDTO;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final java.lang.Object $id = this.getId();
        result = result * PRIME + ($id == null ? 43 : $id.hashCode());
        final java.lang.Object $phoneNumber = this.getPhoneNumber();
        result = result * PRIME + ($phoneNumber == null ? 43 : $phoneNumber.hashCode());
        final java.lang.Object $roles = this.getRoles();
        result = result * PRIME + ($roles == null ? 43 : $roles.hashCode());
        result = result * PRIME + (this.isActive() ? 79 : 97);
        return result;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public java.lang.String toString() {
        return "UserDTO(id=" + this.getId() + ", phoneNumber=" + this.getPhoneNumber() + ", roles=" + this.getRoles() + ", isActive=" + this.isActive() + ")";
    }

    @java.lang.SuppressWarnings("all")
    public UserDTO() {
    }

    @java.lang.SuppressWarnings("all")
    public UserDTO(final UUID id, final String phoneNumber, final List<RoleName> roles, final boolean isActive) {
        this.id = id;
        this.phoneNumber = phoneNumber;
        this.roles = roles;
        this.isActive = isActive;
    }
}
