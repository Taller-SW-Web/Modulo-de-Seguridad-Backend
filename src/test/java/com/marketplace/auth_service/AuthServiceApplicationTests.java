package com.marketplace.auth_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.rabbitmq.listener.simple.auto-startup=false",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitConnectionFactoryAutoConfiguration,org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
})
class AuthServiceApplicationTests {

	@Test
	void contextLoads() {
	}
}