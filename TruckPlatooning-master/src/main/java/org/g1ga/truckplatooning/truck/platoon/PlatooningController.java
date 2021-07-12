package org.g1ga.truckplatooning.truck.platoon;

import org.g1ga.truckplatooning.PathRegister;
import org.g1ga.truckplatooning.Util;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

import static org.g1ga.truckplatooning.Util.getBaseUriComponentsBuilder;

/**
 * Der Rest-Controller des PlatooningService
 */
@RestController
public class PlatooningController {

    private final RestTemplate REST_TEMPLATE = new RestTemplate();
    private final Platooning PLATOONING = Platooning.getInstance();
    private final PlatooningContact PLATOONING_CONTACT = new PlatooningContact(PlatooningService.getPort(), ProcessHandle.current().pid());
    private ScheduledExecutorService healthCheckExecutor = Executors.newSingleThreadScheduledExecutor();
    private boolean waitingForNewFrontTruckPort = false;
    private boolean startElectionAfterNewFrontTruckPort = false;
    private final int MONITORING_PORT = 1112;
    private final int REGISTRATION_PORT = 1111;
    private final ReentrantLock PLATOONING_CONTROLLING_LOCK = new ReentrantLock(true);
    private boolean gapToClose = false;

    /**
     * Wird von dem zugehörigen CruiseService aufgerufen, um sich bei diesem PlatooningService anzumelden.
     * Durch den Aufruf dieser Methode ist der Truck komplett. (Trucks bestehen immer aus einem Platooning- und einem CruiseService.)
     *
     * @param cruisePort Port des sich anmeldenden Cruise Services
     */
    @PutMapping(PathRegister.ADD_CRUISE_CONTROL)
    private void addCruiseControl(@RequestBody Integer cruisePort) {
        enqueueTask(() -> {
            if (PLATOONING.getOwnCruiseControlPort() <= 0) {
                System.out.println("CruiseService mit dem Port " + cruisePort + " wurde hinzugefügt!");
                PLATOONING.setOwnCruiseControlPort(cruisePort);
                PLATOONING_CONTACT.setCruisePort(cruisePort);

                Scanner sc = new Scanner(System.in);
                int initialLeaderPort = PlatooningService.getInitialLeaderPort();
                while (!joinPlatoon(initialLeaderPort)) {
                    System.err.println("Der Leaderport ist nicht mehr aktuell, kann nicht an den Platoon anhängen.\n" +
                            "Bitte gib 'joinplatoon <leaderPort>' ein, wobei 'leaderPort' der PlatooningService-Port des aktuellen Leaders ist, um den Truck dem Platoon anzuhängen.");
                    String s = sc.next();
                    String arg = sc.next();
                    if (s.equalsIgnoreCase("joinplatoon")) {
                        initialLeaderPort = Integer.parseInt(arg);
                    }
                }
            }
        });
    }

    /**
     * Diese Methode setzt das Joinen des Platoons um und setzt im Hintergrund wichtige Werte. So wird der hinterste Truck
     * abgefragt, an dem sich angehängt werden woll, bevor die Wahl startet. Außerdem teilt der Truck der Monitoring Seite mit, dass es ihn gibt.
     * @param initialLeaderPort Port des aktuellen Leaders
     * @return gibt an ob das Joinen erfolgreich ist
     */
    private boolean joinPlatoon(int initialLeaderPort) {
        int ownPlatooningPort = PlatooningService.getPort();

        PLATOONING.setLeader(ownPlatooningPort == initialLeaderPort);

        if (!PLATOONING.isLeader()) {
            PLATOONING.setLeaderPort(initialLeaderPort);
            //Jeder hinten angehängte Truck hat den Leader als BackTruck, um einen bidirektionalen Ring zu bilden.
            PLATOONING.setBackTruckPort(initialLeaderPort);

            System.out.println("Melde beim aktuellen Leader an und erfrage den Port des hintersten Trucks zum Anhängen...");
            try {
                ResponseEntity<Integer> response = REST_TEMPLATE.exchange(Util.getBaseUriComponentsBuilder(initialLeaderPort, PathRegister.NEW_TRUCK_SIGN_IN).toUriString(),
                        HttpMethod.PUT, new HttpEntity<>(ownPlatooningPort), Integer.class);
                Integer frontTruckPort = response.getBody();

                if(frontTruckPort != null && frontTruckPort > 0) {
                    PLATOONING.setFrontTruckPort(frontTruckPort);
                } else {
                    System.err.println("Der gespeicherte Leader ist aktuell nicht der Leader!");
                    return false;
                }
            } catch(ResourceAccessException ex) {
                System.err.println("Der gespeicherte Leader ist nicht verfügbar!");
                return false;
            }
        } else {
            Thread threadM = new Thread(() -> {
                try {
                    ArrayList<PlatooningContact> firstTruck = new ArrayList<>();
                    firstTruck.add(PLATOONING_CONTACT);
                    sendViaPut(PathRegister.SET_LIST, MONITORING_PORT, firstTruck);
                } catch (ResourceAccessException e) {
                    System.err.println("Es wurde versucht, die Liste der aktuellen Trucks an den MonitoringService zu senden, aber dieser ist nicht online.");
                }
            });
            threadM.start();

            Thread thread = new Thread(() -> REST_TEMPLATE.put(getBaseUriComponentsBuilder(REGISTRATION_PORT, PathRegister.UPDATE_ELECTION_STATUS).toUriString(), false));
            thread.start();
        }

        healthCheckExecutor.scheduleAtFixedRate(new HealthCheckTask(), 0, 5, TimeUnit.SECONDS);

        if (!PLATOONING.isLeader()) {
            startElection();
        }
        return true;
    }

    /**
     * Wird vom hinteren Platooning Service aufgerufen, der damit prüft, ob sein Vordermann noch da ist. Außerdem wird diese
     * Methode von dem eigenen Cruise Service aufgerufen, der so auch noch prüft ob der Platooning Service noch online ist.
     * @return den "OK"-Http-Status
     */
    @PutMapping(PathRegister.HEALTH_CHECK)
    private ResponseEntity<?> healthCheck() {
        return new ResponseEntity<>(HttpStatus.OK);
    }

    /**
     * Diese Methode startet den Wahlalgorithmus
     */
    private void startElection() {
        List<PlatooningContact> platooningContacts = new ArrayList<>();
        platooningContacts.add(PLATOONING_CONTACT);
        System.out.println("Zu sendende Liste mit dem eigenen PlatooningContact: " + Arrays.toString(platooningContacts.toArray()));

        Thread thread = new Thread(() -> sendViaPut(PathRegister.RECEIVE_CONTINUE_ELECTION_PATH, PLATOONING.getFrontTruckPort(), platooningContacts));
        thread.start();
    }

    /**
     * Wird beim Leader vom PlatooningService aufgerufen, der erkannt hat, dass sein Vordermann nicht ereichbar ist.
     * Wird anschließend jeweils beim hinteren Truck aufgerufen, bis der Truck gefunden wurde, der den ausgefallenen Truck als backTruckPort gespeichert hat.
     * @param unreachablePort der Port des ausgefallenen Platooning Services
     * @param callerPort der Port des Platoonong Services, dem aufgefallen ist, dass sein Vordermann nicht erreichbar ist
     * @return den HTTP Status "ok"
     */
    @PutMapping(PathRegister.CHECK_BACK_TRUCK_PORT)
    private ResponseEntity<?> checkBackTruckPort(@RequestParam Integer unreachablePort, @RequestParam Integer callerPort) {
        return enqueueTask(() -> {
            if(PLATOONING.getBackTruckPort() == unreachablePort) {
                System.out.println("Dieser Truck war vor dem ausgefallenen Fahrzeug!\nAktualisiere BackTruckPort mit dem Port: " + callerPort);
                //Dieser Truck war vor dem ausgefallenen Fahrzeug
                PLATOONING.setBackTruckPort(callerPort);
                System.out.println("Sende Caller mit dem Port " + callerPort + " den eigenen Port: " + PlatooningService.getPort());
                //Sende dem Caller den eigenen Port, um die Kette zu schließen.
                Thread thread = new Thread(() -> {
                    String url = Util.getBaseUriComponentsBuilder(callerPort, PathRegister.UPDATE_FRONT_TRUCK_PORT).toUriString();
                    REST_TEMPLATE.put(url, PlatooningService.getPort());
                });
                thread.start();
            } else {
                //Reiche den unreachable Port nach hinten weiter
                Thread thread = new Thread(() -> {
                    UriComponentsBuilder builder = getBaseUriComponentsBuilder(PLATOONING.getBackTruckPort(), PathRegister.CHECK_BACK_TRUCK_PORT)
                            .queryParam("unreachablePort", unreachablePort)
                            .queryParam("callerPort", callerPort);
                    REST_TEMPLATE.put(builder.toUriString(), null);
                });
                thread.start();
            }
            return new ResponseEntity<>(HttpStatus.OK);
        });
    }

    /**
     * Methode, die die Anfrage zum updaten des Fronttruck-Ports verarbeitet. Dazu wird der Port des neuen Fronttrucks übergeben.
     * Dies ist z.B. der Fall, wenn sich ein neuer Truck verbindet oder eine Lücke geschlossen wird.
     * @param platooningPort Der neue Front-Truck-Port
     */
    @PutMapping(PathRegister.UPDATE_FRONT_TRUCK_PORT)
    private void updateFrontTruck(@RequestBody Integer platooningPort) {
        healthCheckExecutor.shutdownNow();
        enqueueTask(() -> updateFrontTruckHelper(platooningPort));
    }

    /**
     * Wird ausgeführt, wenn der Front Truck aus verschiedenen Gründen neu gesetzt werden muss
     * @param platooningPort der Port des neuen Vorderfahrzeugs
     */
    private void updateFrontTruckHelper(int platooningPort) {
        PLATOONING.setFrontTruckPort(platooningPort);

        //Überprüfung, ob der vordere Truck vorher ausgefallen ist und die Verbindung zum FrontTruck deshalb wiederhergestellt wurde.
        if (waitingForNewFrontTruckPort) {
            waitingForNewFrontTruckPort = false;
            if (gapToClose) {
                gapToClose = false;
                System.out.println("aktueller Leader-Port: " + PLATOONING.getLeaderPort());
                Thread thread = new Thread(() -> sendViaPut(PathRegister.CLOSE_GAP, PLATOONING.getLeaderPort(), PLATOONING.getOwnCruiseControlPort()));
                thread.start();
            }
        }
        System.out.println("Starte HealthCheck für neuen Front-Truck...");
        healthCheckExecutor = Executors.newSingleThreadScheduledExecutor();
        healthCheckExecutor.scheduleAtFixedRate(new HealthCheckTask(), 0, 5, TimeUnit.SECONDS);
        if (startElectionAfterNewFrontTruckPort) {
            startElectionAfterNewFrontTruckPort = false;
            System.out.println("Ring wiederhergestellt, starte Wahl...");
            REST_TEMPLATE.put(getBaseUriComponentsBuilder(REGISTRATION_PORT, PathRegister.UPDATE_ELECTION_STATUS).toUriString(), true);
            startElection();
        }
    }

    /**
     * Diese Methode wird aufgerufen, wenn der Truck das Platoon kontrolliert verlässt. So teilt er seinem Hintermann mit,
     * dass der neue Vordermann der Vordermann des verlassenden Trucks ist.
     * @param newFrontTruckPort der Port des neuen Front-Trucks
     * @param nowAlone gibt an, ob das der letzte Truck des Platoonings ist
     */
    @PutMapping(PathRegister.NOTIFY_BACK_TRUCK_LEAVE_PLATOON)
    private void notifyBackTruckLeavePlatoon(@RequestBody Integer newFrontTruckPort, @RequestParam boolean nowAlone) {
        healthCheckExecutor.shutdownNow();
        enqueueTask(() -> {
            //Der Platooning-Port, der das Platoon verlässt
            int frontTruckPort = PLATOONING.getFrontTruckPort();

            int leaderPort = PLATOONING.getLeaderPort();
            boolean isLeader = PLATOONING.isLeader();

            //Wenn der Truck Leader ist oder den Leader vor sich hatte und nicht der vorletzte Truck ausgefallen ist.
            if (!nowAlone && frontTruckPort == leaderPort && !isLeader) {
                startElectionAfterNewFrontTruckPort = true;
            } else if (nowAlone) {
                //Ist dieser Truck der letzte Truck, der übrig ist?
                //Dieser Truck ist der neue Leader, weil er der einzige Truck ist, der übrig ist.
                System.out.println("Ich bin der neue Leader, weil kein anderer Truck mehr übrig ist.");
                handleNewLeader(PLATOONING_CONTACT);
            }
            updateFrontTruckHelper(newFrontTruckPort);
        });
    }

    /**
     * Wird vom Truck, der den Ausfall eines PlatooningServices erkannt hat, beim Leader aufgerufen, damit dieser über seinen CruiseService
     * die CruiseServices der anderen Trucks befehlen kann, die Lücke durch Beschleunigung zu schließen.
     * @param cruiseControlPort der Port des Cruise Services des Trucks, der den Platoon verlassen hat
     */
    @PutMapping(PathRegister.CLOSE_GAP)
    private void closeGap(@RequestBody Integer cruiseControlPort) {
        enqueueTask(() -> {
            if (PLATOONING.isLeader()) {
                Thread thread = new Thread(() -> sendViaPut(PathRegister.CLOSE_GAP_LEADER, PLATOONING.getOwnCruiseControlPort(), cruiseControlPort));
                thread.start();
            }
        });
    }

    /**
     * Wird nur beim Leadertruck aufgerufen und gibt den Frontruck von diesem zurück (letzte Fahrzeug des Platoons durch Ring), weil das der Truck ist,
     * an dem sich neue Fahrzeige hinten anhängen.
     * @return Port des letzten Fahrzeugs
     */
    @PutMapping(PathRegister.NEW_TRUCK_SIGN_IN)
    private int newTruckSignIn(@RequestBody Integer platooningPort) {
        return enqueueTask(() -> {
            if (PLATOONING.isLeader()) {
                int currentFrontTruckPort = PLATOONING.getFrontTruckPort();
                if (currentFrontTruckPort > 0) {
                    System.out.println("Melde den neuen PlatooningService beim aktuell letzten Truck an...");
                    REST_TEMPLATE.put(Util.getBaseUriComponentsBuilder(currentFrontTruckPort, PathRegister.UPDATE_BACK_TRUCK).toUriString(), platooningPort);
                } else {
                    //Schicke den eigenen Port, um direkt am Leader anzuhängen.
                    currentFrontTruckPort = PlatooningService.getPort();
                }
                healthCheckExecutor.shutdownNow();
                updateFrontTruckHelper(platooningPort);
                if(PLATOONING.getBackTruckPort() < 1) {
                    PLATOONING.setBackTruckPort(platooningPort);
                }
                return currentFrontTruckPort;
            } else {
                return -1;
            }
        });

    }

    /**
     * Aktualisiert den Back Truck dieses Platooning Services
     * @param platooningPort der Port des Platooning Services des neuen Backtrucks
     */
    @PutMapping(PathRegister.UPDATE_BACK_TRUCK)
    private void updateBackTruck(@RequestBody Integer platooningPort) {
        enqueueTask(() -> PLATOONING.setBackTruckPort(platooningPort));
    }

    /**
     * Diese Methode verarbeit die Informationen zu dem aus der Wahl resultierenden Leader. Je nachem ob die Wahl gewonnen
     * oder verloren wurde, wird der CruiseService benachrichtigt und der Port des neuen Leaders dem RegistrationServer mitgeteilt.
     * @param newLeaderContact der PlatooningContact des neuen Leaders
     */
    private void handleNewLeader(PlatooningContact newLeaderContact) {
        ExecutorService es = Executors.newCachedThreadPool();
        int newLeaderPort;
        boolean isLeader;

        if (newLeaderContact.getPlatooningPid() == PLATOONING_CONTACT.getPlatooningPid()) {
            //Dieser Truck ist der neue Leader
            System.out.println("Ich bin der neue Leader mit PID: " + PLATOONING_CONTACT.getPlatooningPid());
            newLeaderPort = -1;
            isLeader = true;

            es.submit(() -> setCruiseControlAsLeader(true));
            es.submit(() -> setRegistrationServerLeaderPort(PLATOONING_CONTACT.getPlatooningPort(), PLATOONING_CONTACT.getCruisePort()));
        } else {
            //Überprüfung, ob dieser Truck vorher Leader war
            if (PLATOONING.isLeader()) {
                es.submit(() -> setCruiseControlAsLeader(false));
            }
            System.out.println("Speichere Port des Leaders: " + newLeaderContact.getPlatooningPort());
            newLeaderPort = newLeaderContact.getPlatooningPort();
            isLeader = false;
        }
        PLATOONING.setLeaderPort(newLeaderPort);
        PLATOONING.setLeader(isLeader);
        es.shutdown();

        try {
            if (!es.awaitTermination(60, TimeUnit.SECONDS)) {
                System.err.println("handleNewLeader konnte nicht erfolgreich abgeschlossen werden!");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    /**
     * Diese Methode ist wichtiger Bestandteil des Wahlalgorithmus. Da hier der Ringalgorithmus umgesetzt wurde, übergibt ein
     * PlatooningService eine Nachricht an EINEN anderen PlatooningService. In dieser Methode wird geprüft, ob die Wahlliste
     * mit dem PlatooningContact-Objekten der Trucks bereits einmal von jedem Truck empfangen wurde (die Trucks fügen nacheinander ihr eigenes PlatooningContact-Objekt in die Liste hinzu).
     * Wenn der eigene PlatooningContact bereits in der Liste drin ist, bedeutet das, dass die Nachticht den
     * Ring vollständig durchlaufen hat. Das heißt der neue Leader kann anhand der PID ermittelt werden und über den Ring den anderen
     * Trucks mitgeteilt werden. Ist das eigene PlatooningContact-Objekt allerdings nicht in der Liste drin, wird das PlatooningContact-Objekt dieses Trucks in die Liste hinzugefügt
     * und an den Vordermann im Ring weitergeschickt.
     * @param platooningContacts Liste mit den PlatooningContacts der Trucks, die diese Nachricht bereits erhalten haben
     * @return HTTP-Status "ok"
     */
    @PutMapping(PathRegister.RECEIVE_CONTINUE_ELECTION_PATH)
    private ResponseEntity<?> receiveContinueElection(@RequestBody List<PlatooningContact> platooningContacts) {
        return enqueueTask(() -> {
            System.out.println("PlatooningContacts: " + Arrays.toString(platooningContacts.toArray()));
            System.out.println("Eigener PlatooningContact: " + PLATOONING_CONTACT);
            if (platooningContacts.contains(PLATOONING_CONTACT)) {
                //Die eigene Wahl-Nachricht ist einmal durch den Ring gelaufen.

                //Ermittle neuen Leader aus der Liste (den PlatoonService mit der größten PID)
                System.out.println("Ermittle Maximum von: " + Arrays.toString(platooningContacts.toArray()));
                PlatooningContact newLeaderContact = Collections.max(platooningContacts);
                System.out.println("Maximale Pid ist: " + newLeaderContact);

                /*Stellt richtige Reihenfolge der Liste, beginnend mit dem Leader, her.
                  Dazu wird sie zunächst invertiert, da immer der fronttruck, nicht der backtruck,
                  auf einen truck folgt. Dann wird geschaut, an welcher Stelle der Leader in der
                  Liste ist, um diese anschließend so zu rotieren, dass er vorne ist.*/
                Collections.reverse(platooningContacts);
                int leaderIndex = platooningContacts.indexOf(newLeaderContact);
                Collections.rotate(platooningContacts,platooningContacts.size()-leaderIndex);

                //Sende vollständige Liste der Trucks an die Monitoring Schnittstelle
                Thread threadM = new Thread(() -> {
                    try {
                        sendViaPut(PathRegister.SET_LIST, MONITORING_PORT, platooningContacts);
                    } catch(ResourceAccessException e) {
                        System.err.println("Es wurde versucht, die Liste der aktuellen Trucks an den MonitoringService zu senden, aber dieser ist nicht online.");
                    }
                });
                threadM.start();

                handleNewLeader(newLeaderContact);

                ArrayList<Integer> cruisePortsForLeader = new ArrayList<>();
                for (PlatooningContact pc: platooningContacts){
                    if (pc.getCruisePort() != newLeaderContact.getCruisePort()) {
                        cruisePortsForLeader.add(pc.getCruisePort());
                    }
                }

                Thread threadC = new Thread(() -> sendViaPut(PathRegister.SET_CRUISE_PORTS, newLeaderContact.getCruisePort(), cruisePortsForLeader));
                threadC.start();

                //Sende an nächsten Truck, wer der neue Koordinator ist.
                System.out.println("Sende, dass " + newLeaderContact.getPlatooningPid() + " der neue Koordinator ist!");
                sendNewLeader(PLATOONING_CONTACT.getPlatooningPid(), newLeaderContact);
            } else {
                //Füge eigene PID hinzu und leite die Wahl-Nachricht weiter.
                platooningContacts.add(PLATOONING_CONTACT);

                Thread thread = new Thread(() -> sendViaPut(PathRegister.RECEIVE_CONTINUE_ELECTION_PATH, PLATOONING.getFrontTruckPort(), platooningContacts));
                thread.start();
            }
            return new ResponseEntity<>(HttpStatus.OK);
        });
    }

    /**
     * Diese Methode bildet en zweiten Teil des Wahlagortihmus und wird bei jedem Truck einmal ausgeführt, indem der Ring ein zweites
     * Mal durchlaufen wird. Hierüber wird jedem Truck mitgeteilt, welcher Truck der neue Leader ist. Ob diese Nachricht den Ring
     * bereits einmal komplett druchlaufen hat wird geprüft, indem die Initiator-PID immer mitgeschickt wird.
     * @param newLeaderContact der Platooning Contact des neuen Leaders
     * @param senderPID die Prozess-ID des Initiators der New-Leader Nachricht
     * @return HTTP-Status "ok"
     */
    @PutMapping(PathRegister.RECEIVE_NEW_LEADER_PATH)
    private ResponseEntity<?> receiveNewLeader(@RequestBody PlatooningContact newLeaderContact, @RequestParam int senderPID) {
        return enqueueTask(() -> {
            System.out.println("Sender-PID: " + senderPID);
            //System.out.println("Meine PID: " + PLATOONING.getPid());
            long platooningPID = PLATOONING_CONTACT.getPlatooningPid();
            System.out.println("Platooning Contact PID: " + platooningPID);

            if (platooningPID != senderPID) {
                //Es ist nicht der Absender der ursprünglichen Nachricht
                handleNewLeader(newLeaderContact);

                //Sende an nächsten Truck, wer der neue Koordinator ist.
                System.out.println("Leite weiter, dass " + newLeaderContact.getPlatooningPid() + " der neue Koordinator ist!");
                sendNewLeader(senderPID, newLeaderContact);
            } else {
                Thread thread = new Thread(() -> REST_TEMPLATE.put(getBaseUriComponentsBuilder(REGISTRATION_PORT, PathRegister.UPDATE_ELECTION_STATUS).toUriString(), false));
                thread.start();
            }
            return new ResponseEntity<>(HttpStatus.OK);
        });
    }


    /**
     * Hier wird die Nachricht über den Gewinner der Wahl losgeschickt
     * @param senderPID die PID des Platooning Services, der die Wahl analysiert hat
     * @param newLeaderContact der Contact des Platooning Services, der der neue Leader ist
     */
    private void sendNewLeader(long senderPID, PlatooningContact newLeaderContact) {
        UriComponentsBuilder builder = getBaseUriComponentsBuilder(PLATOONING.getFrontTruckPort(), PathRegister.RECEIVE_NEW_LEADER_PATH)
                .queryParam("senderPID", senderPID);
        Thread thread = new Thread(() -> REST_TEMPLATE.put(builder.toUriString(), newLeaderContact));
        thread.start();
    }

    /**
     * Diese Methode ermöglicht das kontrollierte Verlassen des Platoons. Dabei wird sowohl dem Truck hinter dem Verlassenden
     * mitgeteilt, welcher Truck sein neuer Vordermann ist, als auch dem Truck vor dem Verlassenden mitgeteilt, welcher Truck
     * nun hinter ihm ist, bevor das Platoon verlassen wird.
     */
    @PreDestroy
    private void leavePlatoon() {
        System.out.println("Verlasse Platoon...");
        enqueueTask(() -> {
            try {
                String url = getBaseUriComponentsBuilder(MONITORING_PORT, PathRegister.REMOVE_TRUCK_BY_PLATOON).buildAndExpand(PlatooningService.getPort()).toUriString();
                REST_TEMPLATE.delete(url);
            } catch (ResourceAccessException ex) {
                System.err.println("Monitoring-Seite ist offline, die Meldung über den Truckausfall kann nicht gesendet werden...");
            }

            int frontTruckPort = PLATOONING.getFrontTruckPort();
            int backTruckPort = PLATOONING.getBackTruckPort();

            int frontTruckPortToSet;
            int backTruckPortToSet;

            if(frontTruckPort == backTruckPort) {
                frontTruckPortToSet = 0;
                backTruckPortToSet = 0;
            } else {
                frontTruckPortToSet = frontTruckPort;
                backTruckPortToSet = backTruckPort;
            }

            if(frontTruckPort <= 0 && backTruckPort <= 0){
                resetRegistrationServer();
            }

            Thread thread = new Thread(() -> REST_TEMPLATE.put(Util.getBaseUriComponentsBuilder(frontTruckPort, PathRegister.UPDATE_BACK_TRUCK).toUriString(), backTruckPortToSet));
            if (frontTruckPort > 0) {
                thread.start();
            }
            Thread thread1 = new Thread(() -> REST_TEMPLATE.put(Util.getBaseUriComponentsBuilder(backTruckPort, PathRegister.NOTIFY_BACK_TRUCK_LEAVE_PLATOON).queryParam("nowAlone", frontTruckPortToSet == 0 && backTruckPortToSet == 0).toUriString(), frontTruckPortToSet));
            if (backTruckPort > 0) {
                thread1.start();
            }
            System.out.println("aktueller Leader-Port: " + PLATOONING.getLeaderPort());
            Thread thread3 = new Thread(() -> sendViaPut(PathRegister.CLOSE_GAP, PLATOONING.getLeaderPort(), PLATOONING.getOwnCruiseControlPort()));
            if (!PLATOONING.isLeader()) {
                thread3.start();
            }

            int cruisePort = PLATOONING.getOwnCruiseControlPort();

            Thread thread2 = new Thread(() -> {
                if (cruisePort > 0) {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);

                    HttpEntity<String> entity = new HttpEntity<>(null, headers);
                    try {
                        ResponseEntity<String> response = REST_TEMPLATE.postForEntity(Util.getBaseUriComponentsBuilder(cruisePort, PathRegister.SHUTDOWN).toUriString(), entity, String.class);
                        System.out.println("response: " + response.getBody());
                    } catch(ResourceAccessException e) {
                        System.out.println("CruiseService ist schon offline.");
                    }
                }
            });
            thread2.start();

            try {
                thread.join();
                thread1.join();
                thread2.join();
            } catch (InterruptedException ex) {
                System.out.println("interrupted!");
            }
            System.out.println("PlatooningService wird heruntergefahren...");
        });
    }

    /**
     * Lagert das Senden als Put-Request aus
     * @param path Pfad der aufgerufen werden soll
     * @param port Port des Trucks, an den die Nachricht geschickt werden soll
     * @param toSend der zu sendende Inhalt
     * @param <T> Objekttyp der Nachricht
     */
    private <T> void sendViaPut(String path, int port, T toSend) {
        UriComponentsBuilder builder = getBaseUriComponentsBuilder(port, path);
        REST_TEMPLATE.put(builder.toUriString(), toSend);
    }

    /**
     * Setzt den eigenen Crusie Service als Leader
     * @param bool Übergebener Wert true wenn die Wahl gewonnen wurde, false wenn sie verloren wurde UND man vorher Leader war.
     */
    private void setCruiseControlAsLeader(boolean bool) {
        sendViaPut(PathRegister.SET_LEADER, PLATOONING.getOwnCruiseControlPort(), bool);
    }

    /**
     * Teilt dem registration Server die Daten des neuen Leaders mit, indem diese Methode ausgeführt wird.
     * @param platooningPort der Platooning Port des neuen Leaders
     * @param cruisePort der Cruise Port des neuen Leaders
     */
    private void setRegistrationServerLeaderPort(int platooningPort, int cruisePort) {
        System.out.println("Aktualisiere Leader-Ports beim RegistrationServer");
        UriComponentsBuilder builder = getBaseUriComponentsBuilder(REGISTRATION_PORT, PathRegister.SET_LEADER_PORT)
                .queryParam("platooningPort", platooningPort)
                .queryParam("cruisePort", cruisePort);
        REST_TEMPLATE.put(builder.toUriString(), null);
    }

    /**
     * Wird aufgerufen wenn nur noch ein Truck im Platoon ist und dieser ihn orderntlich verlässt,
     * um die Daten des RegistrationServers zurückzusetzen.
     */
    private void resetRegistrationServer(){
        UriComponentsBuilder builder = getBaseUriComponentsBuilder(REGISTRATION_PORT, PathRegister.RESET);
        try {
            REST_TEMPLATE.put(builder.toUriString(), null);
        } catch(ResourceAccessException ex) {
            System.err.println("Der Registration-Server ist offline, kann Anfrage zum Zurücksetzen nicht schicken.");
        }
    }


    /**
     * Diese Klasse ist für die Überprüfung eines Aufalls im Platoon verantwortlich, indem jeder Platooning Service seinem Vordermann
     * UND seinem eigenen Cruise Service in kurzen, regelmäßigen Abständen eine Anfrage schickt, die dieser mit einem einfachen HTTP "OK"
     * zu beantworten hat. Bleibt diese Antwort aus,wird auf einen Ausfall geschlossen und die notwendigen Schritte werden eingeleitet.
     */
    private final class HealthCheckTask implements Runnable {
        private boolean shutdown = false;

        @Override
        public void run() {
            enqueueTask(() -> {
                int ownCruisePort = PLATOONING.getOwnCruiseControlPort();

                //Überprüfung, ob der eigene CruiseService noch da ist. Falls nicht -> Abschaltung des PlatooningServices (Truck kaputt)
                if (ownCruisePort > 0) {
                    try {
                        sendViaPut(PathRegister.HEALTH_CHECK, ownCruisePort, null);
                    } catch (ResourceAccessException e) {
                        System.out.println("CruiseService mit dem Port: " + ownCruisePort + " ist unerreichbar!");
                        //Truck ist kaputt
                        shutdown = true;
                    }
                }

                int frontTruckPort = PLATOONING.getFrontTruckPort();
                //Überprüfung, ob der FrontTruck noch da ist. Falls nicht -> Leader kontaktieren um Lücke zu schließen
                if (frontTruckPort > 0) {
                    try {
                        sendViaPut(PathRegister.HEALTH_CHECK, frontTruckPort, null);
                    } catch (ResourceAccessException e) {
                        System.out.println("Front-Truck mit Port " + frontTruckPort + " ist ausgefallen!");
                        try {
                            String url = getBaseUriComponentsBuilder(MONITORING_PORT, PathRegister.REMOVE_TRUCK_BY_PLATOON).buildAndExpand(frontTruckPort).toUriString();
                            REST_TEMPLATE.delete(url);
                        } catch (ResourceAccessException ex) {
                            System.err.println("Monitoring-Seite ist offline, die Meldung über den Truckausfall kann nicht gesendet werden...");
                        }
                        int portToSendTo = -1;
                        int backTruckPort = PLATOONING.getBackTruckPort();
                        int leaderPort = PLATOONING.getLeaderPort();
                        boolean isLeader = PLATOONING.isLeader();
                        int ownPlatooningPort = PlatooningService.getPort();
                        //Wenn der Truck Leader ist oder den Leader vor sich hatte und nicht der vorletzte Truck ausgefallen ist.
                        if ((isLeader || frontTruckPort == leaderPort) && frontTruckPort != backTruckPort) {
                            //Reiche den unreachable Port nach hinten weiter, um am Ende den neuen FrontTruckPort zu erhalten.
                            if(frontTruckPort == leaderPort) {
                                startElectionAfterNewFrontTruckPort = true;
                            }
                            portToSendTo = backTruckPort;
                        } else {
                            //Ist dieser Truck der letzte Truck, der übrig ist?
                            if (frontTruckPort == backTruckPort && frontTruckPort == leaderPort) {
                                //Dieser Truck ist der neue Leader, weil er der einzige Truck ist, der übrig ist.
                                System.out.println("Ich bin der neue Leader, weil kein anderer Truck mehr übrig ist.");
                                PLATOONING.setFrontTruckPort(0);
                                PLATOONING.setBackTruckPort(0);
                                handleNewLeader(PLATOONING_CONTACT);
                            } else {
                                //Es ist nicht der letzte Truck und der Front Truck ist nicht der Leader
                                if(!isLeader) {
                                    gapToClose = true;
                                }
                                //Sende Leader den Port des Vordermanns, der nicht erreichbar ist.
                                portToSendTo = leaderPort;
                            }
                        }
                        if (portToSendTo > 0) {
                            UriComponentsBuilder builder = getBaseUriComponentsBuilder(portToSendTo, PathRegister.CHECK_BACK_TRUCK_PORT)
                                    .queryParam("unreachablePort", frontTruckPort)
                                    .queryParam("callerPort", ownPlatooningPort);
                            REST_TEMPLATE.put(builder.toUriString(), null);
                        }
                        waitingForNewFrontTruckPort = true;
                        healthCheckExecutor.shutdown();
                    }
                }
            });
            if (shutdown) {
                healthCheckExecutor.shutdown();
                System.exit(0);
            }
        }
    }

    /**
     * Greift auf die Util Methode zu und lockt Bereiche, nutzt den PLATOONING_CONTROLLING_LOCK, der ein fairer ReentrantLock ist,
     * um eine Warteschlange für Anfragen zu ermöglichen. (REST-Anfragen werden standardmäßig asynchron verarbeitet.)
     * Im gelockten Bereich wird etwas zurückgegeben.
     * @param callable das, was in dem kritischen Bereich ausgeführt werden soll in einem Objekt einer Klasse, dass das Callable-Interface implementiert.
     * @param <T> Typ des Rückgabewertes
     * @return Rückgabe von den in dem in callable ausgeführten Code,
     *         null, falls eine Exception auftritt
     */
    private <T> T enqueueTask(Callable<T> callable) {
        return Util.enqueueTask(PLATOONING_CONTROLLING_LOCK, callable);
    }

    /**
     * Greift auf die Util Methode zu und lockt Bereiche, nutzt den PLATOONING_CONTROLLING_LOCK, der ein fairer ReentrantLock ist,
     * um eine Warteschlange für Anfragen zu ermöglichen. (REST-Anfragen werden standardmäßig asynchron verarbeitet.)
     * Im gelockten Bereich wird nichts zurückgegeben.
     * @param runnable das, was in dem kritischen Bereich ausgeführt werden soll in einem Objekt einer Klasse, dass das Runnable-Interface implementiert.
     */
    private void enqueueTask(Runnable runnable) {
        Util.enqueueTask(PLATOONING_CONTROLLING_LOCK, runnable);
    }

}
