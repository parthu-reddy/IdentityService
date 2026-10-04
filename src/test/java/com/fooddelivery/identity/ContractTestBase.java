package com.fooddelivery.identity;

import com.fooddelivery.identity.controller.AuthController;
import com.fooddelivery.identity.service.AuthService;
import com.fooddelivery.identity.service.DevOtpAccessPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

/** Authentication contracts use the JSON one-login controller; infrastructure is mocked. */
public abstract class ContractTestBase {
    @BeforeEach public void setup() {
        AuthService auth=Mockito.mock(AuthService.class);
        ObjectProvider<DevOtpAccessPolicy> dev=Mockito.mock(ObjectProvider.class);
        var controller=new AuthController(auth,dev);
        com.fooddelivery.common.contract.PlatformJson.standaloneSetup(controller);
    }
}
