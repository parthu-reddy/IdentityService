package com.fooddelivery.identity.organisation.repository;

import com.fooddelivery.identity.organisation.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.util.*;

public interface OrganisationInvitationRepository extends JpaRepository<OrganisationInvitation,UUID> {
    @org.springframework.data.jpa.repository.Query("select i.organisationId from OrganisationInvitation i where i.id=:id")
    Optional<UUID> organisationIdFor(@org.springframework.data.repository.query.Param("id") UUID id);
    List<OrganisationInvitation> findAllByOrganisationIdAndStatus(UUID org, InvitationStatus status);
    boolean existsByOrganisationIdAndPhoneNumberAndStatus(UUID org, String phone, InvitationStatus status);
    Slice<OrganisationInvitation> findAllByOrganisationId(UUID org, Pageable page);
    Slice<OrganisationInvitation> findAllByPhoneNumberAndStatus(String phone, InvitationStatus status, Pageable page);
}
