package contracts.http
import org.springframework.cloud.contract.spec.Contract
Contract.make {
    name('internal_organisation_members_ads_manage')
    description('People who may manage the organisation\'s ads: MANAGER and above, never STAFF')
    request {
        method GET()
        urlPath('/api/v1/internal/organisations/22222222-2222-2222-2222-222222222222/members') {
            queryParameters { parameter('permission', 'ADS_MANAGE') }
        }
    }
    response {
        status OK()
        headers { contentType(applicationJson()) }
        body(['11111111-1111-1111-1111-111111111111'])
    }
}
