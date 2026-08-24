package com.fooddelivery.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(
    scanBasePackages = {"com.fooddelivery.identity", "com.fooddelivery.common"}
)
@org.springframework.boot.autoconfigure.domain.EntityScan(basePackages = {"com.fooddelivery.identity", "com.fooddelivery.common.entity"})
@org.springframework.data.jpa.repository.config.EnableJpaRepositories(basePackages = {"com.fooddelivery.identity", "com.fooddelivery.common.repository"})
public class IdentityApplication {
	public static void main(String[] args) {
		SpringApplication.run(IdentityApplication.class, args);
	}
}
