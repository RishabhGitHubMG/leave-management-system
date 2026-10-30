package com.settribe.leave;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the Employee Leave Management System backend application.
 * 
 * The @SpringBootApplication annotation enables:
 * - @Configuration: Defines Spring configuration beans.
 * - @EnableAutoConfiguration: Automatically configures Spring Boot based on jar dependencies.
 * - @ComponentScan: Scans the com.settribe.leave package for components, controllers, and services.
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class LeaveApplication {

	public static void main(String[] args) {
		SpringApplication.run(LeaveApplication.class, args);
	}

}
