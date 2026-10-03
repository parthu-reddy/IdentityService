package contracts.messaging
org.springframework.cloud.contract.spec.Contract.make {
    description('Real organisation creation publishes a flat event on organisation-events')
    label('organisation_created')
    input { triggeredBy('fireOrganisationCreated()') }
    outputMessage {
        sentTo('organisation-events')
        headers { header('eventType','ORGANISATION_CREATED'); header('aggregateType','ORGANISATION') }
        body([
            organisationId: $(producer(regex('[a-f0-9-]{36}'))),
            displayName: 'Contract organisation',
            createdBy: '11111111-1111-1111-1111-111111111111',
            createdAt: '2026-10-03T10:00:00Z'
        ])
    }
}
