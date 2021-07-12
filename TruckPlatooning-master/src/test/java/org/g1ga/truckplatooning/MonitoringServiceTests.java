package org.g1ga.truckplatooning;

import org.g1ga.truckplatooning.monitoring.MonitoringController;
import org.g1ga.truckplatooning.monitoring.MonitoringModel;
import org.g1ga.truckplatooning.monitoring.MonitoringService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes={MonitoringService.class})
public class MonitoringServiceTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private MonitoringController monitoringController;

    @Test
    void contextLoads() {
        Assert.notNull(monitoringController, "Der MonitoringController muss durch den Context erstellt werden.");
    }

    @Test
    void getTruckDataInvalidIndexTest() {
        Assert.isNull(getTruckDataTest(-10), "Es soll null zurückgegeben werden, wenn ein ungültiger Index angegeben wird.");
    }

    @Test
    void getTruckDataTruckNotAvailableTest() {
        Assert.isNull(getTruckDataTest(0), "Es soll null zurückgegeben werden, wenn der Truck mit dem angegebenen Index nicht da ist.");
    }

    private MonitoringModel getTruckDataTest(int index) {
        String url = Util.getBaseUriComponentsBuilder(port, PathRegister.GET_TRUCK_DATA).buildAndExpand(index).toUriString();
        ResponseEntity<MonitoringModel> response = restTemplate.getForEntity(url, MonitoringModel.class);
        return response.getBody();
    }

}
