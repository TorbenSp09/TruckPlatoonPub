package org.g1ga.truckplatooning.registration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Diese Klasse startet den Registration Server und greift dabei auf die in der dazugehörigen reg-server.yml Datei hinterlegten Informationen zu.
 */
@SpringBootApplication
public class RegistrationServer {

    /**
     * Starte den Registration Service
     * @param args die Argumente die übergeben werden können
     */
    public static void main(String[] args) {
        // Tell server to look for registration.properties or registration.yml
        //System.setProperty("spring.config.location", "registration-server.yml");
        System.setProperty("spring.config.name", "reg-server");

        SpringApplication.run(RegistrationServer.class, args);
    }

}
