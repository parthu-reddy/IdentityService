package contracts.dinein
import org.springframework.cloud.contract.spec.Contract
[
    Contract.make {
        name('dine_in_guest_link')
        description('restaurant-service dining links a dine-in guest to a table by phone; identity finds or creates the account')
        request {
            method POST()
            url '/api/v1/internal/identity/dine-in-guests'
            headers { contentType(applicationJson()) }
            body(phone: '8000000123', outletId: '44444444-4444-4444-4444-444444444444',
                 sessionId: '55555555-5555-5555-5555-555555555555', requestedByUserId: '66666666-6666-6666-6666-666666666666')
        }
        response {
            status OK()
            headers { contentType(applicationJson()) }
            body(userId: '77777777-7777-7777-7777-777777777777', newAccount: false, maskedPhone: '******0123')
        }
    },
    Contract.make {
        name('dine_in_guest_link_rate_limited')
        description('a phone that has been linked five times this hour is refused')
        request {
            method POST()
            url '/api/v1/internal/identity/dine-in-guests'
            headers { contentType(applicationJson()) }
            body(phone: '8000000999', outletId: '44444444-4444-4444-4444-444444444444',
                 sessionId: '55555555-5555-5555-5555-555555555555', requestedByUserId: '66666666-6666-6666-6666-666666666666')
        }
        response {
            status TOO_MANY_REQUESTS()
            headers { contentType(applicationJson()) }
            body(success: false, errorCode: 'TOO_MANY_GUEST_LINKS')
        }
    },
    Contract.make {
        name('dine_in_guest_link_account_unavailable')
        description('a blocked account cannot be seated for orders')
        request {
            method POST()
            url '/api/v1/internal/identity/dine-in-guests'
            headers { contentType(applicationJson()) }
            body(phone: '8000000888', outletId: '44444444-4444-4444-4444-444444444444',
                 sessionId: '55555555-5555-5555-5555-555555555555', requestedByUserId: '66666666-6666-6666-6666-666666666666')
        }
        response {
            status FORBIDDEN()
            headers { contentType(applicationJson()) }
            body(success: false, errorCode: 'GUEST_ACCOUNT_UNAVAILABLE')
        }
    }
]
