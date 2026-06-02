package tn.isetbizerte.pfe.hrbackend.devdata;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Boots the destructive full local demo reset/seed. Only active under the dedicated
 * {@code demo-reset} Spring profile so it can never run during normal startup or tests.
 */
@Component
@Profile("demo-reset")
public class FullDemoSeederRunner implements CommandLineRunner {

    private final FullDemoSeederService seederService;

    public FullDemoSeederRunner(FullDemoSeederService seederService) {
        this.seederService = seederService;
    }

    @Override
    public void run(String... args) {
        seederService.resetAndSeed();
    }
}
