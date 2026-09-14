package com.example.HRMS;

import com.example.HRMS.auth.service.BootstrapProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * Application entry point.
 *
 * <p>{@link UserDetailsServiceAutoConfiguration} is excluded so Spring Boot does
 * not create its default in-memory security user (and startup-generated
 * password). Authentication is performed against {@code app_user} via the JWT
 * filter instead.
 */
@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class})
@EnableConfigurationProperties(BootstrapProperties.class)
public class HrmsApplication {

	public static void main(String[] args) {
		SpringApplication.run(HrmsApplication.class, args);
	}

}
