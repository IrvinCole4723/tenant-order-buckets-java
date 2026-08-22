package learning.store;

import learning.store.config.InfraiStorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(InfraiStorageProperties.class)
public class TenantOrderApplication {
    public static void main(String[] args) {
        SpringApplication.run(TenantOrderApplication.class, args);
    }
}
