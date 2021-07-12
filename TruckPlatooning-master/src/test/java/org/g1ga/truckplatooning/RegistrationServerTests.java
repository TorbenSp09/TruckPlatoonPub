package org.g1ga.truckplatooning;

import org.g1ga.truckplatooning.registration.RegistrationController;
import org.g1ga.truckplatooning.registration.RegistrationServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes={RegistrationServer.class})
class RegistrationServerTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RegistrationController registrationController;

    @Test
    void contextLoads() {
        Assert.notNull(registrationController, "Der RegistrationController muss durch den Context erstellt werden.");
    }

    @Test
    void testCruiseServiceFirstFailure() {
        String url = Util.getBaseUriComponentsBuilder(port, PathRegister.REGISTER_CRUISE).buildAndExpand(1234).toUriString();
        ResponseEntity<int[]> response = restTemplate.getForEntity(url, int[].class);

        Assert.isNull(response.getBody(), "Es soll nicht möglich sein, dass sich der CruiseService zuerst registriert.");
    }

}