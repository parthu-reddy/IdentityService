package com.fooddelivery.identity.port;

import com.fooddelivery.common.enums.OrganisationRole;
import java.util.UUID;

public interface EventPublisherPort {
    void publishNotificationEvent(String recipientPhoneNumber, String channelType, String otp);

    /**
     * Tells an invited phone number about an organisation invitation, inside the inviting transaction.
     *
     * @param existingUserId the invitee's account when one exists, so their channel preferences apply;
     *                       null for a phone that has not signed up yet
     */
    void publishOrganisationInvitation(UUID invitationId, String phoneNumber, UUID existingUserId, OrganisationRole role);

    /** SMSes a dine-in guest the code they read to the waiter, inside the requesting transaction. Code only. */
    void publishDineInConsentCode(UUID consentId, String phoneNumber, String code);
}
