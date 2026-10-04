import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description("Should initiate login via Identity Service")
    request {
        method 'POST'
        urlPath('/api/v1/auth/otp')
        headers {
            contentType(applicationJson())
        }
        body(phoneNumber: '8000000001')
    }
    response {
        status OK()
        headers {
            contentType(applicationJson())
        }
        body([
            success: true,
            data: null,
            message: "Code sent"
        ])
    }
}
