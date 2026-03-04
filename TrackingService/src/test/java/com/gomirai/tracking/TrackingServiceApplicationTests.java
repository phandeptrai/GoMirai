package com.gomirai.tracking;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.gomirai.tracking.config.TestRedisConfig;

@SpringBootTest
@Import(TestRedisConfig.class)
class TrackingServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
