package frontend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = { "frontend", "common" })
public class GoogolApplication {
    public static void main(String[] args) {
        SpringApplication.run(GoogolApplication.class, args);
    }
}
