package com.aliano.mutiagent;

import com.aliano.mutiagent.config.BackendInstanceLock;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan("com.aliano.mutiagent.infrastructure.persistence.mapper")
public class MutiAgentApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(MutiAgentApplication.class);
        application.addInitializers(context -> BackendInstanceLock.acquire(context.getEnvironment()));
        application.run(args);
        Runtime.getRuntime().addShutdownHook(new Thread(BackendInstanceLock::release, "muti-agent-lock-release"));
    }

}
