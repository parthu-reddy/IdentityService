package com.fooddelivery.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.fooddelivery.identity", "com.fooddelivery.common"})
public class IdentityApplication {
	public static void main(String[] args) {
		SpringApplication.run(IdentityApplication.class, args);
	}
}
