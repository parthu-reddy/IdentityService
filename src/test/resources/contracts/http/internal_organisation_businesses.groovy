package contracts.http
import org.springframework.cloud.contract.spec.Contract
[
    Contract.make {
        name('internal_organisation_businesses_approved')
        request { method GET(); url '/api/v1/internal/organisations/22222222-2222-2222-2222-222222222222/businesses' }
        response {
            status OK()
            headers { contentType(applicationJson()) }
            body([[businessType:'RESTAURANT',businessId:'55555555-5555-5555-5555-555555555555',applicationStatus:'APPROVED']])
        }
    },
    Contract.make {
        name('internal_organisation_businesses_none')
        request { method GET(); url '/api/v1/internal/organisations/44444444-4444-4444-4444-444444444444/businesses' }
        response {
            status OK()
            headers { contentType(applicationJson()) }
            body([])
        }
    }
]
