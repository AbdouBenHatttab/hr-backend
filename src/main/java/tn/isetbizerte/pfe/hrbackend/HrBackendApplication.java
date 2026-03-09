package tn.isetbizerte.pfe.hrbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class HrBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(HrBackendApplication.class, args);
    }

}
