package com.fooddelivery.identity.entitlement.service;

import com.fooddelivery.common.dto.organisation.OrganisationBusinessDto;
import com.fooddelivery.identity.entitlement.entity.BusinessEntitlement;
import com.fooddelivery.identity.entitlement.repository.BusinessEntitlementRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** The businesses an organisation has applied for, read from the entitlement projection. */
@Service
@lombok.RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganisationBusinessQuery {
    private final BusinessEntitlementRepository businesses;

    public List<OrganisationBusinessDto> of(UUID organisationId) {
        return businesses.findAllByOrganisationIdIn(List.of(organisationId)).stream()
                .sorted(
                        Comparator.comparing(BusinessEntitlement::getBusinessType)
                                .thenComparing(BusinessEntitlement::getBusinessId))
                .map(
                        b ->
                                new OrganisationBusinessDto(
                                        b.getBusinessType(),
                                        b.getBusinessId(),
                                        b.getApplicationStatus()))
                .toList();
    }
}
