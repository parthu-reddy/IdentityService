package com.fooddelivery.identity.entitlement.dto;

import com.fooddelivery.common.enums.*;
import java.util.*;

public final class PortalViews {
    private PortalViews() { }
    public enum Portal { CUSTOMER, RESTAURANT, DELIVERY, ADS, BUSINESS, KITCHEN, ADMIN }
    public enum State { AVAILABLE, PENDING_REVIEW, ACTION_REQUIRED, NOT_STARTED, SUSPENDED, STEP_UP_REQUIRED, UNAVAILABLE }
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public record BusinessView(BusinessType businessType,UUID businessId,ApplicationStatus applicationStatus,String reason) { }
    public record OrganisationPortal(UUID organisationId,String displayName,OrganisationRole role,OrganisationStatus status,
                                     List<BusinessView> businesses) { }
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public record PortalView(Portal portal,State state,List<OrganisationPortal> organisations,String reason) { }
}
