package com.gomirai.tracking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
@EnableFeignClients
@ComponentScan(basePackages = {
    "com.gomirai.tracking",
    "com.gomirai.common"
})
public class TrackingServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(TrackingServiceApplication.class, args);
	}

}
