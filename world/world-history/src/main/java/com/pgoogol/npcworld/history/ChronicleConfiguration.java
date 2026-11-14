package com.pgoogol.npcworld.history;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The chronicle on the data source Spring Boot sets up from application.yaml. */
@Configuration
class ChronicleConfiguration {

    @Bean
    Chronicle chronicle(JdbcClient jdbc, JdbcTemplate template) {
        return new Chronicle(jdbc, template);
    }
}
