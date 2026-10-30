package contracts.dinein
import org.springframework.cloud.contract.spec.Contract
[
    Contract.make {
        name('dine_in_consent_verify_ok')
        description('the code the guest read to the waiter is right: the guest account (existing here) is returned')
        request {
            method POST()
            url '/api/v1/internal/identity/dine-in-consents/a1111111-1111-1111-1111-111111111111/verify'
            headers { contentType(applicationJson()) }
            body(code: '482913')
        }
        response {
            status OK()
            headers { contentType(applicationJson()) }
            body(userId: '77777777-7777-7777-7777-777777777777', newAccount: false)
        }
    },
    Contract.make {
        name('dine_in_consent_verify_wrong_code')
        description('a wrong code is refused and counts one of the five attempts')
        request {
            method POST()
            url '/api/v1/internal/identity/dine-in-consents/a2222222-2222-2222-2222-222222222222/verify'
            headers { contentType(applicationJson()) }
            body(code: '000000')
        }
        response {
            status UNPROCESSABLE_ENTITY()
            headers { contentType(applicationJson()) }
            body(success: false, errorCode: 'CONSENT_CODE_INVALID')
        }
    },
    Contract.make {
        name('dine_in_consent_verify_expired')
        description('a consent past its ten minutes answers 410 so dining can ask for a new code')
        request {
            method POST()
            url '/api/v1/internal/identity/dine-in-consents/a3333333-3333-3333-3333-333333333333/verify'
            headers { contentType(applicationJson()) }
            body(code: '482913')
        }
        response {
            status GONE()
            headers { contentType(applicationJson()) }
            body(success: false, errorCode: 'CONSENT_EXPIRED')
        }
    }
]
