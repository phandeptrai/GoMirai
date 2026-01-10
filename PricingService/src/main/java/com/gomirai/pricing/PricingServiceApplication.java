// PricingService/src/main/java/com/gomirai/pricing/PricingServiceApplication.java
package com.gomirai.pricing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableDiscoveryClient
@ComponentScan(basePackages = {
		"com.gomirai.pricing", // Service này
		"com.gomirai.common" // BẮT BUỘC: để load Security, ExceptionHandler, SecurityUtils...
})
public class PricingServiceApplication {
	public static void main(String[] args) {
		SpringApplication.run(PricingServiceApplication.class, args);
	}
}