package com.fooddelivery.identity.dto;

import jakarta.validation.constraints.NotNull;
import com.fooddelivery.common.enums.RoleName;

public class RoleRequestDTO {
    @NotNull
    private RoleName roleName;


    @java.lang.SuppressWarnings("all")
    public static class RoleRequestDTOBuilder {
        @java.lang.SuppressWarnings("all")
        private RoleName roleName;

        @java.lang.SuppressWarnings("all")
        RoleRequestDTOBuilder() {
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public RoleRequestDTO.RoleRequestDTOBuilder roleName(final RoleName roleName) {
            this.roleName = roleName;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public RoleRequestDTO build() {
            return new RoleRequestDTO(this.roleName);
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public java.lang.String toString() {
            return "RoleRequestDTO.RoleRequestDTOBuilder(roleName=" + this.roleName + ")";
        }
    }

    @java.lang.SuppressWarnings("all")
    public static RoleRequestDTO.RoleRequestDTOBuilder builder() {
        return new RoleRequestDTO.RoleRequestDTOBuilder();
    }

    @java.lang.SuppressWarnings("all")
    public RoleName getRoleName() {
        return this.roleName;
    }

    @java.lang.SuppressWarnings("all")
    public void setRoleName(final RoleName roleName) {
        this.roleName = roleName;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public boolean equals(final java.lang.Object o) {
        if (o == this) return true;
        if (!(o instanceof RoleRequestDTO)) return false;
        final RoleRequestDTO other = (RoleRequestDTO) o;
        if (!other.canEqual((java.lang.Object) this)) return false;
        final java.lang.Object this$roleName = this.getRoleName();
        final java.lang.Object other$roleName = other.getRoleName();
        if (this$roleName == null ? other$roleName != null : !this$roleName.equals(other$roleName)) return false;
        return true;
    }

    @java.lang.SuppressWarnings("all")
    protected boolean canEqual(final java.lang.Object other) {
        return other instanceof RoleRequestDTO;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final java.lang.Object $roleName = this.getRoleName();
        result = result * PRIME + ($roleName == null ? 43 : $roleName.hashCode());
        return result;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public java.lang.String toString() {
        return "RoleRequestDTO(roleName=" + this.getRoleName() + ")";
    }

    @java.lang.SuppressWarnings("all")
    public RoleRequestDTO() {
    }

    @java.lang.SuppressWarnings("all")
    public RoleRequestDTO(final RoleName roleName) {
        this.roleName = roleName;
    }
}
