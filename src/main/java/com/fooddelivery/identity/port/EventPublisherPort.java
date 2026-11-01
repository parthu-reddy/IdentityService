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

    /**
     * SMSes a dine-in guest that a waiter seated them (no parameters), inside the linking transaction; once per session and
     * guest, so adding the same guest again sends nothing.
     */
    void publishDineInSeated(UUID sessionId, UUID guestUserId, String phoneNumber);
}
