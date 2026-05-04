package tn.isetbizerte.pfe.hrbackend.devdata;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev-seed")
public class DevDataSeederRunner implements CommandLineRunner {

    private final DevDataSeederService seederService;

    public DevDataSeederRunner(DevDataSeederService seederService) {
        this.seederService = seederService;
    }

    @Override
    public void run(String... args) {
        seederService.resetAndSeed();
    }
}
