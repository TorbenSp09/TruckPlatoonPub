package org.g1ga.truckplatooning.truck.platoon;

import org.g1ga.truckplatooning.PathRegister;
import org.g1ga.truckplatooning.Util;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.util.SocketUtils;

import java.util.Scanner;

/**
 * Diese Klasse startet den Platooning Service und holt sich die benötogten Informationen (Port des aktuellen Leaders und eine zugewiesene Id) vom Registartion Server und
 * wartet anschließend darauf, bis der Cruise Service startet, da sie dann zusammen als 1 Truck dem Platoon joinen.
 */
@SpringBootApplication
public class PlatooningService {

    private static final int PORT = SocketUtils.findAvailableTcpPort(49152, 65535);
    private static int initialLeaderPort;
    static Thread scannerThread;

    /**
     * Startet den Platooning Service. Ist nur möglich wenn der Registration Server läuft und gerade kein anderer Platooning
     * Service auf seinen Cruise Service wartet.
     * @param args die Argumente, die zum Start übergeben werden können
     */
    public static void main(String[] args) {

        int[] idAndLeaderTruckPort = Util.getRegistrationData(PathRegister.REGISTER_PLATOON, PORT);

        if (idAndLeaderTruckPort != null) {
            int id = idAndLeaderTruckPort[0];
            initialLeaderPort = idAndLeaderTruckPort[1];

            // Tell server to look for platooning-server.yml
            System.setProperty("spring.config.name", "platooning-server");
            System.setProperty("spring.application.instance_id", String.valueOf(id));
            System.setProperty("server.port", String.valueOf(PORT));

            SpringApplication.run(PlatooningService.class, args);

            Platooning PLATOONING = Platooning.getInstance();

            Scanner scanner = new Scanner(System.in);

            scannerThread = new Thread(() -> {
                while(scanner.hasNext()) {
                    if (scanner.next().equalsIgnoreCase("status")) {
                        System.out.println("Leader: " + PLATOONING.isLeader());
                        System.out.println("LeaderPort: " + PLATOONING.getLeaderPort());
                        System.out.println("frontTruckPort: " + PLATOONING.getFrontTruckPort());
                        System.out.println("backTruckport: " + PLATOONING.getBackTruckPort());
                    }
                }
            });
            scannerThread.start();

            System.out.println("PlatooningService mit Id: " + id + " und Port: "
                    + System.getProperty("server.port")+" und Pid: "+ProcessHandle.current().pid());
        } else {
            System.err.println("Der Start musste abgebrochen werden, bitte:" +
                    "\n- stelle sicher, dass gerade keine Wahl läuft" +
                    "\n- beachte, dass immer ein PlatooningService und danach ein CruiseService gestartet werden muss bevor ein weiterer PlatooningService gestartet werden kann." +
                    "\n- stelle sicher, dass der RegistrationServer online ist");
        }
    }

    static int getPort() {
        return PORT;
    }

    static int getInitialLeaderPort() {
        return initialLeaderPort;
    }
}