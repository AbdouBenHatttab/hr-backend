package tn.isetbizerte.pfe.hrbackend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "app.leave.accrual-on-startup=false",
        "app.holidays.sync-on-startup=false"
})
class HrBackendApplicationTests {

    @Test
    void contextLoads() {
    }

}
