package org.g1ga.truckplatooning.truck.cruise;

import org.g1ga.truckplatooning.PathRegister;
import org.g1ga.truckplatooning.Util;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 *  Die CruiseController Klasse dient als Controller für die Cruise Schnittstelle.
 *  Mithilfe der Pfade wird der Zugriff durch das Platooning auf die verschiedenen Methoden der Cruise Klasse ermöglicht.
 */
@RestController
public class CruiseController {

    private final Cruise CRUISECONTROL = Cruise.getInstance();
    ReentrantLock speedLock = new ReentrantLock();
    ReentrantLock leaderLock = new ReentrantLock();


    /**
     * Konstruktor, welcher auf der Konsole lediglich ausgibt, dass der HealthCheck begonnen hat
     */
    public CruiseController() {
        System.out.println("Starte regelmäßige Überprüfung ob Platoonong-Service am Port: " +CruiseService.getPlatooningPort() + " noch erreichbar ist.");
    }

    /**
     * GetMapping um speed eines Trucks abzufragen.
     * @return speed des angesprochenen Trucks
     */
    @GetMapping(PathRegister.GET_SPEED)
    public int getSpeed() {
        return enqueueTask(speedLock, CRUISECONTROL::getSpeed);

    }

    /**
     * PutMapping um die Beschleunigung eines Trucks anzufordern.
     * @param plus Wert um den beschleunigt werden soll
     */
    @PutMapping(PathRegister.SPEEDUP)
    private void speedUp(@RequestBody Integer plus){
        enqueueTask(speedLock, () -> System.out.println("new speed: " + CRUISECONTROL.speedUp(plus)));
    }

    /**
     * PutMapping um das Bremsen eines Trucks anzufordern.
     * @param minus Wert um den gebremst werden soll
     */
    @PutMapping(PathRegister.SLOW_DOWN)
    private void slowDown(@RequestBody Integer minus){
        enqueueTask(speedLock, () -> System.out.println("new speed: " + CRUISECONTROL.slowDown(minus)));
    }

    /**
     * PutMapping um einen Truck zu stoppen.
     */
    @PutMapping(PathRegister.STOP)
    private void stop(){
        enqueueTask(speedLock, () -> System.out.println("new speed: " + CRUISECONTROL.stop()));
    }

    /**
     * GetMapping um initialSpeed abzufragen
     * @return int Liste mit speed und targetSpeed
     */
    @GetMapping(PathRegister.GET_INITIAL_SPEED)
    private int[] getInitialSpeed() {
        return enqueueTask(speedLock, () -> new int[] {CRUISECONTROL.getSpeed(), CRUISECONTROL.getTargetSpeed()});
    }

    /**
     * GetMapping um abzufragen ob der angepsprochene Truck der Platoon Leader ist.
     * @return booleschen Wert von isLeader
     */
    @GetMapping(PathRegister.IS_LEADER)
    private boolean isLeader() {
        return enqueueTask(leaderLock, CRUISECONTROL::isLeader);
    }

    /**
     * PutMapping um den Leader Status eines Trucks zu aktualisieren.
     * @param leader boolescher Wert der zeigt ob angesprochener Truck leader ist
     */
    @PutMapping(PathRegister.SET_LEADER)
    private @ResponseBody void setLeader(@RequestBody boolean leader){
        enqueueTask(leaderLock, () -> CRUISECONTROL.setLeader(leader));
    }

    /**
     * PutMapping um cruisePorts beim neuen Leader zu sezen.
     * @param ports ports der aktuell vorhandenen Cruises
     */
    @PutMapping(PathRegister.SET_CRUISE_PORTS)
    private void addCruises(@RequestBody ArrayList<Integer> ports) {
        enqueueTask(leaderLock, () -> CRUISECONTROL.setCruisePorts(ports));
    }

    /**
     * PutMapping für die Weiterleitung von closeGap an alle hinteren Trucks.
     * @param cruiseControlPort
     */
    @PutMapping(PathRegister.CLOSE_GAP_LEADER)
    private void closeGapLeader(@RequestBody Integer cruiseControlPort) {
        CRUISECONTROL.sendCloseGap(cruiseControlPort);
    }

    /**
     * PutMapping um das Schließen einer Lücke anzufordern.
     */
    @PutMapping(PathRegister.CLOSE_GAP)
    private void closeGap(){
        if (!CRUISECONTROL.isLeader()) {
            ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
            exec.scheduleAtFixedRate(() -> {
                if(CRUISECONTROL.getTargetSpeed() == CRUISECONTROL.getSpeed()){
                    double gap = CRUISECONTROL.getExpectedDistance() * 2 + CRUISECONTROL.getTruckLength();
                    CRUISECONTROL.setGap(gap);
                    CRUISECONTROL.speedUp(10);
                    exec.shutdown();
                }
            }, 0, 1, TimeUnit.SECONDS);
        }
    }

    /**
     * Diese Methode antwortet mit einem HTTP Code auf die Health-Check Anfrage des Platooning Services
     * @return HttpStatus "ok"
     */
    @PutMapping(PathRegister.HEALTH_CHECK)
    private ResponseEntity<?> healthCheck() {
        return new ResponseEntity<>(HttpStatus.OK);
    }


    /**
     * Greift auf die Util Methode zu und lockt Bereiche. Im gelockten Bereich wird nichts zurückgegeben.
     * @param lock der zu verwendende Lock
     * @param runnable das, was in dem kritischen Bereich ausgeführt werden soll in einem Objekt einer Klasse, dass das Runnable-Interface implementiert.
     */
    private void enqueueTask(ReentrantLock lock, Runnable runnable) {
        Util.enqueueTask(lock, runnable);
    }

    /**
     * Greift auf die Util Methode zu und lockt Bereiche. Im gelockten Bereich wird etwas zurückgegeben.
     * @param lock der zu verwendende Lock
     * @param callable das, was in dem kritischen Bereich ausgeführt werden soll in einem Objekt einer Klasse, dass das Callable-Interface implementiert.
     * @param <T> Typ des Rückgabewertes
     * @return Rückgabe von den in dem in callable ausgeführten Code, null, falls eine Exception auftritt
     */
    private <T> T enqueueTask(ReentrantLock lock, Callable<T> callable) {
        return Util.enqueueTask(lock, callable);
    }

}
