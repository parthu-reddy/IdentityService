package contracts.http
import org.springframework.cloud.contract.spec.Contract
[
    Contract.make {
        name('internal_organisation_membership_member')
        request { method GET(); url '/api/v1/internal/organisations/22222222-2222-2222-2222-222222222222/members/11111111-1111-1111-1111-111111111111' }
        response {
            status OK()
            headers { contentType(applicationJson()) }
            body([organisationId:'22222222-2222-2222-2222-222222222222',organisationStatus:'ACTIVE',userId:'11111111-1111-1111-1111-111111111111',role:'MANAGER',status:'ACTIVE'])
        }
    },
    Contract.make {
        name('internal_organisation_membership_nonmember')
        request { method GET(); url '/api/v1/internal/organisations/22222222-2222-2222-2222-222222222222/members/33333333-3333-3333-3333-333333333333' }
        response { status NOT_FOUND() }
    }
]
