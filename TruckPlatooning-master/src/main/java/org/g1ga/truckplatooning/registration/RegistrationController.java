package org.g1ga.truckplatooning.registration;

import org.g1ga.truckplatooning.PathRegister;
import org.g1ga.truckplatooning.Util;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Der Registration-Controller ist lediglich dafür da, neuen Services die benötigten Ports zum Platoonbeitritt mitzuteilen.
 * Er Macht damit die manuelle Eingabe der Ports hinfällig. Dazu teilt er zum einen neuen Cruise-Services den Port ihrer
 * Platooning-Services mit, zum anderen teilt er neuen Trucks den Port des aktuellen Leaders mit.
 */
@RestController
public class RegistrationController {

    private int platoonIdCounter = 1;
    private int cruiseIdCounter = 1;
    private int leaderPlatooningPort = -1;
    private int leaderCruisePort = -1;
    private int waitingPlatooningServicePort = -1;
    private boolean firstPlatooningService = true;
    private boolean firstCruiseService = true;
    //Verhindert die Registrierung mehrerer Services zur selben Zeit, was zu Fehlern geführt hätte.
    private final ReentrantLock REGISTER_LOCK = new ReentrantLock(true);
    private boolean runningElection = false;

    /**
     * Diese Methode wird am Ende des Wahlalgorithmus aufgerufen und aktualisiert die Leader-Ports, damit neue Trucks
     * stets die Möglichkeit haben, den Leader bezüglich des Platoonbeitritts zu kontaktieren.
     * @param platooningPort Port des neuen Leader-Platooning-Services
     * @param cruisePort Port des CruiseService des neuen Leaders
     */
    @PutMapping(PathRegister.SET_LEADER_PORT)
    private void updateLeaderPort(@RequestParam Integer platooningPort, @RequestParam Integer cruisePort) {
        enqueueTask(() -> {
            System.out.println("Setze Leader-Port: PlatooningPort: " + platooningPort + ", CruisePort: " + cruisePort);
            leaderPlatooningPort = platooningPort;
            leaderCruisePort = cruisePort;
        });
    }

    /**
     * Diese Methode ist dafür da, zu verhindern, dass Trucks joinen während der Wahlalgorithmus gerade ausgeführt wird.
     * Ansonsten können komische Probleme entstehen.
     * @param running
     */
    @PutMapping(PathRegister.UPDATE_ELECTION_STATUS)
    private void updateElectionStatus(@RequestBody boolean running) {
        enqueueTask(() -> {
            runningElection = running;
        });
    }

    /**
     * Startende Platoon-Services rufen diese Methode auf. Dabei geben sie ihren Port mit, damit der Registration Server diesen den
     * dazugehörigen Cruise-Services mitteilen kann. Außerdem wird der übergebene Port, falls es der erste Truck ist,
     * automatisch zum Leader.
     *
     * @param port Port des neuen Platooning Service
     * @return Integer Array mit Platoon-Counter und Leader-Port, null wenn bereits ein Platoon-Service auf seinen Cruise-Service wartet
     */
    @GetMapping(PathRegister.REGISTER_PLATOON)
    private int[] registerPlatooningService(@PathVariable("port") int port) {
        return enqueueTask(() -> {
            int[] data = null;
            if (waitingPlatooningServicePort == -1 && !runningElection) {
                if (firstPlatooningService) {
                    leaderPlatooningPort = port;
                    firstPlatooningService = false;
                }
                waitingPlatooningServicePort = port;

                data = new int[] {platoonIdCounter++, leaderPlatooningPort};
            }
            return data;
        });
    }

    /**
     * Diese Methode wird von startenden Cruise-Services aufgerufen. Dabei wird geprüft,
     * ob deren Platooning-Service bereits registriert ist. Ist dies der Fall, wird ihnen der Port zurückgegeben.
     * Ist dies nicht der Fall, wird nichts zurückgegeben, sodass der Start des Cruise-Service abgebrochen wird.
     *
     * @param port Port des neuen Cruise Service
     * @return Integer Array mit Cruise-Counter und Platoon-Port, null wenn keine Platoon wartet
     */
    @GetMapping(PathRegister.REGISTER_CRUISE)
    private int[] registerCruiseService(@PathVariable("port") int port) {
         return enqueueTask(() -> {
            int[] data = null;
            if (waitingPlatooningServicePort != -1 && !runningElection) {
                if (firstCruiseService) {
                    leaderCruisePort = port;
                    firstCruiseService = false;
                }
                data = new int[] {cruiseIdCounter++, waitingPlatooningServicePort, leaderCruisePort};
                waitingPlatooningServicePort = -1;
                runningElection = true;

            }
            return data;
        });
    }

    /**
     * Hier wird der Registration Server zurückgesetzt, damit neue Trucks erkennen, dass der Platoon nicht existiert.
     * Wird nur ausgeführt, wenn Truck das Platoon "ordentlich" verlässt.
     */
    @PutMapping(PathRegister.RESET)
    private void reset() {
        enqueueTask(() -> {
            System.out.println("Registration Server wurde zurückgesetzt, weil kein Truck mehr im Platoon ist.");
            platoonIdCounter = 1;
            cruiseIdCounter = 1;
            leaderPlatooningPort = -1;
            leaderCruisePort = -1;
            waitingPlatooningServicePort = -1;
            firstPlatooningService = true;
            firstCruiseService = true;
        });
    }



    /**
     * Greift auf die Util Methode zu und lockt Bereiche, nutzt den REGISTER_LOCK, der ein fairer ReentrantLock ist,
     * um eine Warteschlange für Anfragen zu ermöglichen. (REST-Anfragen werden standardmäßig asynchron verarbeitet.)
     * Im gelockten Bereich wird etwas zurückgegeben.
     * @param callable das, was in dem kritischen Bereich ausgeführt werden soll in einem Objekt einer Klasse, dass das Callable-Interface implementiert.
     * @param <T> Typ des Rückgabewertes
     * @return Rückgabe von den in dem in callable ausgeführten Code,
     *         null, falls eine Exception auftritt
     */
    private <T> T enqueueTask(Callable<T> callable) {
        return Util.enqueueTask(REGISTER_LOCK, callable);
    }

    /**
     * Greift auf die Util Methode zu und lockt Bereiche, nutzt den REGISTER_LOCK, der ein fairer ReentrantLock ist,
     * um eine Warteschlange für Anfragen zu ermöglichen. (REST-Anfragen werden standardmäßig asynchron verarbeitet.)
     * Im gelockten Bereich wird nichts zurückgegeben.
     * @param runnable das, was in dem kritischen Bereich ausgeführt werden soll in einem Objekt einer Klasse, dass das Runnable-Interface implementiert.
     */
    private void enqueueTask(Runnable runnable) {
        Util.enqueueTask(REGISTER_LOCK, runnable);
    }

}
