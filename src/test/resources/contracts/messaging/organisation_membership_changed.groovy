package contracts.messaging
org.springframework.cloud.contract.spec.Contract.make {
    description('Creation grants OWNER through a flat membership event')
    label('organisation_membership_changed')
    input { triggeredBy('fireOrganisationMembershipChanged()') }
    outputMessage {
        sentTo('organisation-events')
        headers { header('eventType','ORGANISATION_MEMBERSHIP_CHANGED'); header('aggregateType','ORGANISATION') }
        body([
            organisationId: $(consumer('22222222-2222-2222-2222-222222222222'), producer(regex('[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}'))),
            userId: '11111111-1111-1111-1111-111111111111',
            role: 'OWNER', status: 'ACTIVE',
            changedBy: '11111111-1111-1111-1111-111111111111',
            changedAt: '2026-10-03T10:00:00Z'
        ])
    }
}
