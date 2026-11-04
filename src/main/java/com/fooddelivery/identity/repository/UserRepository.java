package com.fooddelivery.identity.repository;

import com.fooddelivery.identity.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<AppUser, UUID> {
    // Maps current authority may not be served from a lagging read replica.
    @org.springframework.data.jpa.repository.Query(value="SELECT NOT pg_catalog.pg_is_in_recovery()", nativeQuery=true)
    boolean mapsAuthorityIsPrimary();
    Optional<AppUser> findByPhoneNumber(String phoneNumber);
    java.util.List<AppUser> findAllByPhoneNumberIn(java.util.Collection<String> phoneNumbers);
    @org.springframework.data.jpa.repository.Query("""
        select u from AppUser u where
        (:role='ADMIN' and exists(select r.id from UserRole r where r.user=u and r.roleName=com.fooddelivery.common.enums.RoleName.ADMIN))
        or (u.isActive=true and (
          :role='CUSTOMER'
          or (:role='DELIVERY' and exists(select d.userId from DeliveryEntitlement d where d.userId=u.id and d.applicationStatus=com.fooddelivery.common.enums.ApplicationStatus.APPROVED))
          or (:role in ('RESTAURANT','BUSINESS') and exists(
            select m.id from OrganisationMember m, Organisation o, BusinessEntitlement b
            where m.userId=u.id and m.status=com.fooddelivery.common.enums.MembershipStatus.ACTIVE
            and o.id=m.organisationId and o.status=com.fooddelivery.common.enums.OrganisationStatus.ACTIVE
            and b.organisationId=o.id and b.applicationStatus=com.fooddelivery.common.enums.ApplicationStatus.APPROVED
            and (:role='BUSINESS' or b.businessType=com.fooddelivery.common.enums.BusinessType.RESTAURANT)))
          or (:role='KITCHEN' and exists(
            select k.staffId from KitchenEntitlement k, Organisation o, BusinessEntitlement b
            where (k.userId=u.id or (k.userId is null and k.phoneNumber=u.phoneNumber))
            and k.status in (com.fooddelivery.common.enums.KitchenStaffStatus.INVITED,com.fooddelivery.common.enums.KitchenStaffStatus.ACTIVE)
            and o.id=k.organisationId and o.status=com.fooddelivery.common.enums.OrganisationStatus.ACTIVE
            and b.organisationId=o.id and b.applicationStatus=com.fooddelivery.common.enums.ApplicationStatus.APPROVED
            and b.businessType=com.fooddelivery.common.enums.BusinessType.RESTAURANT))
        ))
        """)
    org.springframework.data.domain.Page<AppUser> findWithEntitlement(@org.springframework.data.repository.query.Param("role") String role,
            org.springframework.data.domain.Pageable pageable);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from AppUser u where u.id=:id")
    Optional<AppUser> lockById(@org.springframework.data.repository.query.Param("id") UUID id);
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically=true)
    @org.springframework.data.jpa.repository.Query("UPDATE AppUser u SET u.entitlementsVersion=u.entitlementsVersion+1 WHERE u.id IN :ids")
    int bumpEntitlements(@org.springframework.data.repository.query.Param("ids") java.util.Collection<UUID> ids);
    @org.springframework.data.jpa.repository.Query("select u.id,u.entitlementsVersion from AppUser u where u.id in :ids order by u.id")
    java.util.List<Object[]> entitlementVersions(@org.springframework.data.repository.query.Param("ids") java.util.Collection<UUID> ids);
}
