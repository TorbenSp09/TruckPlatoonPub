package org.g1ga.truckplatooning.monitoring;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.SpringApplication;

/**
 * Diese Klasse startet den Monitoring Service und greift dabei auf die in der dazugehörigen monitoring-server.yaml Datei hinterlegten Informationen zu.
 */
@SpringBootApplication
public class MonitoringService {

    /**
     * Startet den Service
     * @param args Argumente die zum Starten übergeben werden
     */
    public static void main(String[] args) {
        // Tell server to look for registration.properties or registration.yml
        //System.setProperty("spring.config.location", "registration-server.yml");
        System.setProperty("spring.config.name", "monitoring-server");

        SpringApplication.run(MonitoringService.class, args);
    }
}
