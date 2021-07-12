package org.g1ga.truckplatooning;

import org.g1ga.truckplatooning.monitoring.MonitoringService;
import org.g1ga.truckplatooning.registration.RegistrationServer;
import org.g1ga.truckplatooning.truck.cruise.CruiseService;
import org.g1ga.truckplatooning.truck.platoon.PlatooningService;

public class TruckPlatooningApplication {

    /**
     * Main-Methode für das TruckPlatooning-Programm.
     * Hier kann über ein Konsolenargument gewählt werden, welcher Microservice genau gestartet werden soll.
     *
     * @param args Konsolen-Argumente
     */
    public static void main(String[] args) {
        if (args.length == 1) {
            switch (args[0].toLowerCase()) {
                case "platooning":
                    PlatooningService.main(args);
                    break;
                case "cruise":
                    CruiseService.main(args);
                    break;
                case "registration":
                    RegistrationServer.main(args);
                    break;
                case "monitoring":
                    MonitoringService.main(args);
                    break;
                default:
                    showUsage();
                    break;
            }
        } else {
            showUsage();
        }
    }

    private static void showUsage() {
        System.out.println("Verwendung: java -jar ... <server-name>\nwobei\nserver-name 'platooning', 'cruise', 'registration' oder 'monitoring' sein kann.");
    }

}
