package org.g1ga.truckplatooning;

import org.g1ga.truckplatooning.truck.cruise.CruiseController;
import org.g1ga.truckplatooning.truck.cruise.CruiseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.Assert;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes={CruiseService.class})
public class CruiseServiceTests {

    @Autowired
    private CruiseController cruiseController;

    @Test
    void contextLoads() {
        Assert.notNull(cruiseController, "Der CruiseController muss durch den Context erstellt werden.");
    }

}
