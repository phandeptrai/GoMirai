package com.gomirai.communication;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.gomirai.communication", "com.gomirai.common"})
@EnableDiscoveryClient
@EnableScheduling
@org.springframework.data.mongodb.repository.config.EnableMongoRepositories(basePackages = "com.gomirai.communication")
public class CommunicationServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(CommunicationServiceApplication.class, args);
	}

}
