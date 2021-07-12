package org.g1ga.truckplatooning.truck.cruise;

import org.g1ga.truckplatooning.PathRegister;
import org.g1ga.truckplatooning.Util;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Die Cruise Klasse dient zur Durchführung der Geschwindigkeitsregulierung.
 * Dabei gibt es Methoden zum Beschleunigen, Bremsen und Stoppen.
 *
 */
public class Cruise {

    private static Cruise instance;
    private final RestTemplate REST_TEMPLATE = new RestTemplate();

    private int speed = 0;
    //maximal 80 km/h
    private int targetSpeed = 0;
    //in km
    private double traveledDistance = 0;
    private double gap;
    private double traveledDistanceFrontTruck = 0;
    private double truckDistance;

    private boolean isLeader;
    private final int OWN_PORT = CruiseService.getPort();

    private final ScheduledExecutorService execHealth = Executors.newSingleThreadScheduledExecutor();
    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
    private final ScheduledExecutorService execMon = Executors.newSingleThreadScheduledExecutor();

    private ArrayList<Integer> cruisePorts = new ArrayList<>();

    ReentrantLock closeGapLock = new ReentrantLock();


    /**
     * In der Cruise Methode wird ein Thread ausgeführt, der im Sekundentakt targetSpeed und das vorhandensein einer Lücke überprüft.
     * Ist targetSpeed größer als speed, wird je nach Ausgandsgeschwindigkeit unterschiedlich schnell beschleunigt, indem speed unterschiedlich schnell hochgezählt wird.
     * speed kann, aufgrund der Abstufungen, targetSpeed kurzzeitig überschreiten, regelt jedoch automatisch wieder runter. Dies ist für den Realismus so gewollt (keine perfekte Punktlandung beim beschleunigen).
     * Ist targetSpeed kleiner als speed, wird gebremst, indem speed runtergezählt wird.
     * Ist gap ungleich 0, wird die Geschwindigkeiten des jeweiligen Trucks kurzzeitig erhöht um die Lücke zu schließen.
     * Dafür wird die zurückgelegte Strecke des aktuellen Trucks und des Vordermanns, zu dem aufgeschlossen werden muss, sekundengenau berechnet.
     * Sobald der Abstand einen bestimmten Punkt unterschreitet, wird die Geschwindigkeit wieder an das Platoon angepasst.
     * Außerdem qir die aktuelle Geschwindigkeit in regekmäßigen Abständen an die Monitoring Schnittstelle geschickt, um die aktuelle Geschwindigkeit anzuzeigen
     */
    private Cruise() {
        execHealth.scheduleAtFixedRate(new HealthCheckTask(), 0, 5, TimeUnit.SECONDS);
        //updates speed to target speed

        exec.scheduleAtFixedRate(() -> {
            //Umrechnung von speed in km/h in km/s

            //System.out.println("traveled distance: " + traveledDistance);
            if(speed < targetSpeed) {
                if (speed < 5){
                    speed += 1;
                    System.out.println("new speed: " + speed);
                } else if(speed < 20){
                    speed += 3;
                    System.out.println("new speed: " + speed);
                } else if(speed < 50){
                    speed += 2;
                    System.out.println("new speed: " + speed);
                } else {
                    speed += 1;
                    System.out.println("new speed: " + speed);
                }
            } else if(speed > targetSpeed) {
                speed -= 1;
                System.out.println("new speed: " + speed);
            }
            if (getGap() != 0) {
                traveledDistance += speed/3600.0;
                traveledDistanceFrontTruck += (targetSpeed-10)/3600.0;
                truckDistance = gap + traveledDistanceFrontTruck - traveledDistance;
                if (truckDistance <= 0.025){
                    setGap(0);
                    traveledDistance = 0;
                    traveledDistanceFrontTruck = 0;
                    System.out.println("Gap closed!");
                    slowDown(10);
                }
            }

        }, 0, 1, TimeUnit.SECONDS);

        execMon.scheduleAtFixedRate(() -> {
            try {
                String url = Util.getBaseUriComponentsBuilder(1112, PathRegister.SET_SPEED).buildAndExpand(OWN_PORT).toUriString();
                REST_TEMPLATE.put(url, speed);
            } catch(ResourceAccessException e) {
                //Monitoring Service ist offline
            }
        }, 0, 2, TimeUnit.SECONDS);
    }

    /**
     * Getter Methode für Instance von Cruise.
     * @return instance von Cruise
     */
    public static synchronized Cruise getInstance() {
        if (instance == null) {
            instance = new Cruise();
        }
        return instance;
    }

    /**
     * Diese Methode stoppt den Truck, indem targetSpeed auf 0 gesetzt wird.
     * @return gibt speed mit dem Wert 0 zurück.
     */
    public int stop() {
        sendIfLeader(PathRegister.STOP, null);
        targetSpeed = 0;
        return speed = 0;
    }

    /**
     * Diese Methode dient zur Beschleunigung des Trucks.
     * Wenn plus größer als 0 ist und targetSpeed und plus gemeinsam nicht über 80 kommen, wird beschleunigt indem targetSpeed um plus erhöht wird.
     * Wenn gap ungleich 0 ist wird targetSpeed unabhängig der 80er Grenze um plus erhöht.
     * @param plus Wert um den die Geschwindigkeit erhöht werden soll
     * @return Wert von speed
     */
    public int speedUp(int plus) {
        if(plus>0){
            if(targetSpeed+plus<=80){
                targetSpeed+=plus;
            } else if (gap != 0){
                targetSpeed+=plus;
            }
        }
        sendIfLeader(PathRegister.SPEEDUP, plus);
        return speed;
    }

    /**
     * Diese Methode dient zum abbremsen des Trucks.
     * Wenn minus größer als 0 ist und targetSpeed abzüglich minus 0 nicht unterschreitet wird minus vom targetSpeed abgezogen.
     * @param minus Wert um den die Geschwindigkeit verringert werden soll
     * @return Wert von speed
     */
    public int slowDown(int minus) {
        if(minus>0) {
            if (targetSpeed-minus >= 0) {
                targetSpeed-=minus;
            }
        }
        sendIfLeader(PathRegister.SLOW_DOWN, minus);
        return speed;
    }

    /**
     * Setter Methode für targetSpeed.
     * @param targetSpeed angestrebte Geschwindigkeit
     */
    public void setTargetSpeed(int targetSpeed) {
        if(targetSpeed >= 0){
            this.targetSpeed = targetSpeed;
        }
    }

    /**
     * Getter Methode für targetSpeed.
     * @return aktuellen Wert von targetSpeed
     */
    public int getTargetSpeed() {
        return targetSpeed;
    }

    /**
     * Setter Methode für cruisePorts
     * @param ports Wert auf den cruisePorts gesetzt werden soll
     */
    public void setCruisePorts(ArrayList<Integer> ports) {
        cruisePorts = ports;
    }

    /**
     * Getter Methode für cruisePorts
     * @return Wert von cruisePorts
     */
    public ArrayList<Integer> getCruisePorts() {
        return cruisePorts;
    }

    /**
     * Getter Methode für speed.
     * @return Wert von speed
     */
    public int getSpeed() {
        return speed;
    }

    /**
     * Setzt die Geschwindigkeit eines Trucks direkt.
     * @param speed die gewollte Geschwindigkeit
     */
    public void setSpeed(int speed) {
        this.speed = speed;
    }

    /**
     * Fragt ab, ob der Cruise Service zum Leadertruck gehört
     * @return Wert von isLeader
     */
    public boolean isLeader(){
        return isLeader;
    }

    /**
     * Setter Methode für isLeader.
     * Wenn leader false ist wird cruisePorts auf null gesetzt, weil er den anderen Trucks dann keine Nachrichten mehr
     * schicken muss.
     * @param leader Wert auf den isLeader gesetzt wird
     */
    public void setLeader(boolean leader) {
        isLeader = leader;
        if (!leader) {
            cruisePorts = null;
        }
    }

    /**
     * Setter Methode für gap
     * @param gap Wert auf den gap gesetzt werden soll
     */
    public void setGap(double gap) {
        enqueueTask(closeGapLock, () -> this.gap=gap);
    }

    /**
     * Getter Methode für gap
     * @return Wert von gap
     */
    public double getGap() {
        return enqueueTask(closeGapLock,()-> gap);
    }



    /**
     * Sendet closeGap an andere Trucks weiter.
     * @param startCruisePort port des Trucks der closeGap startet.
     */
    public void sendCloseGap(int startCruisePort) {
        Thread thread = new Thread(() -> {
            int startIndex = cruisePorts.indexOf(startCruisePort);
            sendToOtherCruiseServices(startIndex, PathRegister.CLOSE_GAP, null);
        });
        thread.start();

    }

    /**
     * Methode sendet anderen Trucks zu, ob aktueller Truck der Leadertruck ist.
     * @param path Pfad des Zieltrucks
     * @param value Der zu sendende Wert
     */
    public void sendIfLeader(String path, Integer value) {
        if(isLeader) {
            Thread thread = new Thread(() -> sendToOtherCruiseServices(0, path, value));
            thread.start();
        }
    }

    /**
     * Sendet Informationen abhängig von der Position im Platoon an andere Cruise Services.
     * @param startIndex Position im Platoon, ab der alle Trucks die Nschricht bekommen sollen
     * @param path Pfad im Controller des Zieltrucks, der ausgeführt werden soll
     * @param toSend Inhalt der Nachricht
     * @param <T> Objekttyp der Nachricht
     */
    private <T> void sendToOtherCruiseServices(int startIndex, String path, T toSend) {
        ArrayList<Integer> portIndexesToRemove = new ArrayList<>();
        for (int i = startIndex; i < cruisePorts.size(); i++) {
            int cruisePort = cruisePorts.get(i);
            try {
                sendToTruck(path, cruisePort, toSend);
            } catch (ResourceAccessException e) {
                System.out.println("CruiseService mit dem Port " + cruisePort + " ist nicht erreichbar und wird im Anschluss aus der Liste gelöscht...");
                portIndexesToRemove.add(i);
            }
        }
        for (int index : portIndexesToRemove) {
            cruisePorts.remove(index);
        }
    }

    /**
     * Sendet Informationen an einen anderen Truck
     * @param path Pfad im Controller des Zieltrucks, der ausgeführt werden soll
     * @param port Port des Trucks
     * @param toSend Inhalt der Nachricht
     * @param <T> Objekttyp der Nachricht
     */
    private <T> void sendToTruck(String path, int port, T toSend) throws ResourceAccessException {
        UriComponentsBuilder builder = Util.getBaseUriComponentsBuilder(port, path);
        HttpEntity<T> entity = null;
        if (toSend != null) {
            entity = new HttpEntity<>(toSend);
        }
        REST_TEMPLATE.put(builder.toUriString(), entity);
    }

    /**
     * Schickt eine Anfrage an den eigenen Platooning_Service die prüft, ob er noch online ist. Wenn ja passiert nichts,
     * wenn nein fährt auch der Cruise Service herunter
     */
    private class HealthCheckTask implements Runnable {

        @Override
        public void run() {
            int ownPlatooningPort = CruiseService.getPlatooningPort();
            if (ownPlatooningPort > 0) {
                try {
                    UriComponentsBuilder builder = Util.getBaseUriComponentsBuilder(ownPlatooningPort, PathRegister.HEALTH_CHECK);
                    REST_TEMPLATE.put(builder.toUriString(), null);
                } catch (ResourceAccessException e) {
                    System.out.println("Platooning-Service mit Port: " + ownPlatooningPort + " ist ausgefallen!");
                    System.out.println("Service wird heruntergefahren...");
                    System.exit(0);
                }
            }
        }
    }

    /**
     * Verlässt den Platoon ordentlich und fährt den Service herunter. Wird bei System.exit ausgeführt, wenn der Cruise
     * Service merkt, dass der Platoon Service das Platoon verlassen hat.
     */
    @PreDestroy
    public void shutdown() {
        System.out.println("Stoppe CruiseService...");
        execHealth.shutdown();
        execMon.shutdown();
        exec.shutdown();
        System.out.println("CruiseService wird heruntergefahren...");
    }

    /**
     * Gibt den normalen Bastand zwischen 2 Trucks zurück
     * @return die Distanz zwischen zwei Trucks
     */
    public double getExpectedDistance(){
        return 0.02;
    }

    /**
     * Gibt die konstante Länge des Trucks zurück, um die entsandene Lücke zu berechnen.
     * @return die Lönge eines Trucks
     */
    public double getTruckLength(){
        return 0.02;
    }

    /**
     * Übernimmt die Sychronisierung und arbeitet mit der Util Klasse. In dem gelockten Bereich wird etwas zurückgegeben.
     * @param lock der Lock der verwendet werden soll.
     * @param callable das, was in dem kritischen Bereich ausgeführt werden soll in einem Objekt einer Klasse, dass das Callable-Interface implementiert.
     * @param <T> Typ des Rückgabewertes
     * @return Rückgabe von den in dem in callable ausgeführten Code, null, falls eine Exception auftritt
     */
    private <T> T enqueueTask(ReentrantLock lock, Callable<T> callable) {
        return Util.enqueueTask(lock, callable);
    }

}
