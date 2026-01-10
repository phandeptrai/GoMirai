package com.gomirai.review;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication
@EnableDiscoveryClient
@ComponentScan(basePackages = { "com.gomirai.review", "com.gomirai.common" })
@EnableMongoRepositories(basePackages = "com.gomirai.review.repository") // Đảm bảo Spring tìm thấy Repository
public class ReviewServiceApplication {
	public static void main(String[] args) {
		SpringApplication.run(ReviewServiceApplication.class, args);
	}
}