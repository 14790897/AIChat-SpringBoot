package com.liuweiqing.aichat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("需要 PostgreSQL 和 Redis 环境，CI 中跳过")
class AichatApplicationTests {

	@Test
	void contextLoads() {
	}

}
