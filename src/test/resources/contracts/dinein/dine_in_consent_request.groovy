package contracts.dinein
import org.springframework.cloud.contract.spec.Contract
[
    Contract.make {
        name('dine_in_consent_request')
        description('restaurant-service dining asks identity to SMS a dine-in guest their consent code')
        request {
            method POST()
            url '/api/v1/internal/identity/dine-in-consents'
            headers { contentType(applicationJson()) }
            body(phone: '8000000123', outletId: '44444444-4444-4444-4444-444444444444',
                 sessionId: '55555555-5555-5555-5555-555555555555', requestedByUserId: '66666666-6666-6666-6666-666666666666')
        }
        response {
            status CREATED()
            headers { contentType(applicationJson()) }
            body(consentId: $(consumer('b1111111-1111-1111-1111-111111111111'), producer(regex(uuid()))),
                 expiresAt: '2026-10-29T12:10:00Z',
                 maskedPhone: '******0123')
        }
    },
    Contract.make {
        name('dine_in_consent_request_rate_limited')
        description('a phone that has had five consent requests this hour is refused')
        request {
            method POST()
            url '/api/v1/internal/identity/dine-in-consents'
            headers { contentType(applicationJson()) }
            body(phone: '8000000999', outletId: '44444444-4444-4444-4444-444444444444',
                 sessionId: '55555555-5555-5555-5555-555555555555', requestedByUserId: '66666666-6666-6666-6666-666666666666')
        }
        response {
            status TOO_MANY_REQUESTS()
            headers { contentType(applicationJson()) }
            body(success: false, errorCode: 'TOO_MANY_CONSENT_REQUESTS')
        }
    }
]
