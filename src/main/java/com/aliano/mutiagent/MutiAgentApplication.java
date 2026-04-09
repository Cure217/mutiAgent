package com.aliano.mutiagent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan("com.aliano.mutiagent.infrastructure.persistence.mapper")
public class MutiAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(MutiAgentApplication.class, args);
    }

}
