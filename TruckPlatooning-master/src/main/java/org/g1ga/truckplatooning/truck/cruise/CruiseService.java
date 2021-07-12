package org.g1ga.truckplatooning.truck.cruise;

import org.g1ga.truckplatooning.PathRegister;
import org.g1ga.truckplatooning.Util;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.util.SocketUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Scanner;

/**
 * Diese Klasse startet den Cruise Service und holt sich die benötogten Informationen (zugewiesene Id und Port des zugewiesenen PlatooningServices) vom Registartion Server und nimmt anschließend
 * Kontakt zum Leader (für den Erhalt der aktuellen Geschwindigkeit) und dem eigenen Platooning Service auf.
 */
@SpringBootApplication
public class CruiseService {

    private static int platooningPort;
    private static final int PORT = SocketUtils.findAvailableTcpPort(49152, 65535);
    private static final RestTemplate REST_TEMPLATE = new RestTemplate();

    /**
     * Starten den Cruise Service. Ist nur möglich, wenn der Registration Server läuft und ein Platooning Service frei ist.
     * @param args Konsolen-Argumente
     */
    public static void main(String[] args) {

        int[] data = Util.getRegistrationData(PathRegister.REGISTER_CRUISE, PORT);

        if (data != null) {

            int id = data[0];

            System.setProperty("spring.config.name", "cruise-server");
            System.setProperty("server.port", String.valueOf(PORT));

            System.out.println("CruiseService with ID: " + id + " on PORT: "
                    + System.getProperty("server.port")+" with PID: "+ProcessHandle.current().pid());

            Cruise cruise = Cruise.getInstance();

            if(id == 1) {
                cruise.setLeader(true);
            }

            platooningPort = data[1];

            SpringApplication.run(CruiseService.class, args);

            //Frage den CruiseService des aktuellen Leaders nach der aktuellen targetSpeed
            int initialCruiseLeaderPort = data[2];
            int initialSpeed = 0;
            int initialTargetSpeed = 0;
            if(initialCruiseLeaderPort != PORT) {
                System.out.println("Erfrage aktuellen Speed vom aktuellen Leader...");

                int [] initialSpeeds = REST_TEMPLATE.getForObject(
                        Util.getBaseUriComponentsBuilder(initialCruiseLeaderPort, PathRegister.GET_INITIAL_SPEED).toUriString(),
                        int[].class);

                if(initialSpeeds != null) {
                    initialSpeed = initialSpeeds[0];
                    initialTargetSpeed = initialSpeeds[1];
                }
            } else {
                initialSpeed = 30;
                initialTargetSpeed = 30;
            }
            Cruise.getInstance().setSpeed(initialSpeed);
            Cruise.getInstance().setTargetSpeed(initialTargetSpeed);

            System.out.println("Melde CruiseService beim PlatooningService an...");
            String url = Util.getBaseUriComponentsBuilder(platooningPort, PathRegister.ADD_CRUISE_CONTROL).toUriString();
            REST_TEMPLATE.put(url, PORT);

            Scanner sc = new Scanner(System.in);
            while (sc.hasNext()) {
                String s = sc.next();
                String arg = sc.next();

                if (Cruise.getInstance().isLeader()) {
                    switch(s.toLowerCase()) {
                        case "speedup":
                            System.out.println("new speed: " + cruise.speedUp(Integer.parseInt(arg)));
                            break;
                        case "slowdown":
                            System.out.println("new speed: " + cruise.slowDown(Integer.parseInt(arg)));
                            break;
                        case "stop":
                            System.out.println("new speed: " + cruise.stop());
                            break;
                        default:
                            break;
                    }
                } else {
                    System.err.println("Nur beim Leader ist die Änderung der Geschwindigkeit in der Kommandozeile möglich!");
                }
            }
        } else {
            System.err.println("Der Start musste abgebrochen werden, bitte:" +
                    "\n- stelle sicher, dass gerade keine Wahl läuft" +
                    "\n- beachte, dass immer ein PlatooningService und danach ein CruiseService gestartet werden muss." +
                    "\n- stelle sicher, dass der RegistrationServer online ist");
        }
    }

    /**
     * Ermöglich den Zugriff auf den Port des eigenen Platooning Services, welcher vom Registration Server übergeben wird
     * @return der Port dieses Platooning Services
     */
    static int getPlatooningPort() {
        return platooningPort;
    }

    /**
     * Gibt den eigenen Port dieses Services zurück.
     * @return der Port des Cruise Services
     */
    static int getPort() {
        return PORT;
    }

}
