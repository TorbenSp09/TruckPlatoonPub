package org.g1ga.truckplatooning.monitoring;


import org.g1ga.truckplatooning.PathRegister;
import org.g1ga.truckplatooning.Util;
import org.g1ga.truckplatooning.truck.platoon.PlatooningContact;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Dieser Controller ist für die Verwaltung der Monitoring Schnittstelle zuständig. Er definiert die Methoden, die auf
 * dieser zur Verfügung stehen und empfängt sowohl Informationen von den einzelnen Trucks, gibt aer auch Informationen an
 * diese weiter.
 */
@Controller
public class MonitoringController {

    final RestTemplate REST_TEMPLATE = new RestTemplate();

    PlatooningContact leaderContact;

    //Synchronisierte Liste, welche Monitoring Models zur Anzeige auf der Website beinhaltet
    List<MonitoringModel> trucks = Collections.synchronizedList(new ArrayList<>());

    //Locks zur Synchronisierung; Verbieten gleichzeitigen Zugriff auf wichtige Ressourcen
    ReentrantLock truckListLock = new ReentrantLock(true);
    ReentrantLock controlTruckLock = new ReentrantLock(true);

    /**
     * Gibt monitoring.html bei url: http://localhost:1112 wieder
     * @param model das Model der Monitoring-Website
     * @return aktuelle Website monitoring.html
     */
    @GetMapping("/")
    private String monitoring(Model model) {
        model.addAttribute("input", new Input());
        model.addAttribute("trucks", trucks);

        return "monitoring";
    }

    /**
     * Diese Methode wird nach der Ausführung des Wahlalgorithmus aufgerufen,
     * um dem Monitoring Informationen über alle aktuellen Trucks zu senden. Der Methode wird dazu die gefüllte, geordnete
     * Liste am Ende des Wahlalgorithmus übergeben.
     * @param platooningContacts Liste der Trucks mit PID, Cruise Port und Platoon Port
     * @param model das Model der Monitoring-Website
     * @return aktuelle Website monitoring.html
     */
    @PutMapping(PathRegister.SET_LIST)
    private String setList(@RequestBody List<PlatooningContact> platooningContacts, Model model){
        return enqueueTask(truckListLock, () -> {
            leaderContact = Collections.max(platooningContacts);
            trucks.clear();
            for (PlatooningContact platooningContact : platooningContacts) {
                trucks.add(new MonitoringModel(platooningContact.getPlatooningPid(), platooningContact.getPlatooningPort(), platooningContact.getCruisePort(), 0));
            }
            model.addAttribute("input", new Input());
            model.addAttribute("trucks", trucks);
            return "monitoring";
        });
    }

    /**
     * Wird in regelmäßigen zeitlichen Abständen von jedem Cruise Serice ausgeführt, um die aktuelle Geschwindigkeit
     * mitzuteilen. Dazu werden Port und Geschwindigkeit übergeben, um die Geschwindigkeit an der richtigen Stelle
     * in der Liste dem richtigen Truck (Monitoring Model) zuzuweisen.
     * @param cruisePort Port des Cruise Services der seine Geschwindigkeit sendet
     * @param speed aktuelle Geschwindigkeit des Cruise Services
     */
    @PutMapping(PathRegister.SET_SPEED)
    private String setSpeed(@PathVariable("cruisePort") int cruisePort, @RequestBody int speed, Model model){
        return enqueueTask(truckListLock, () -> {
            for (MonitoringModel monitoringModel: trucks) {
                if (monitoringModel.getCruisePort() == cruisePort) {
                    monitoringModel.setSpeed(speed);
                }
            }
            model.addAttribute("input", new Input());
            model.addAttribute("trucks", trucks);
            return "monitoring";
        });
    }

    /**
     * Diese Methode wird benötigt, um einenTruck, falls er aufällt, aus der Anzeige zu entfernen. Dies geschieht, falls
     * einem anderen Truck beim Healthcheck auffällt, dass sein Vordermann nicht mehr da ist.
     * @param platooningPort Port des Platoons das entfernt werden soll
     * @param model das Model der Monitoring-Website
     * @return aktuelle Website monitoring.html
     */
    @DeleteMapping(PathRegister.REMOVE_TRUCK_BY_PLATOON)
    private String removeTruckByPlatoon(@PathVariable int platooningPort, Model model){
        return enqueueTask(truckListLock, () -> {
            trucks.removeIf(monitoringModel -> monitoringModel.getPlatoonPort() == platooningPort);
            if (platooningPort == leaderContact.getPlatooningPort() && trucks.size() == 1) {
                MonitoringModel truck = trucks.get(0);
                leaderContact = new PlatooningContact(truck.getPlatoonPort(), truck.getCruisePort(), truck.getPid());
            }
            model.addAttribute("input", new Input());
            model.addAttribute("trucks", trucks);
            return "monitoring";
        });
    }

    /**
     * Diese Methode wird beim betätigen des "Beschleunige"-Buttons auf der Monitoring-Website ausgeführt.
     * Sie sendet die geforderte Geschwindigkeit an den Leader Truck, welcher dann beginnt die Geschwindigkeit
     * dementsprechend zu erhöhen.
     * @param input Input Objekt der Monitoring-Website
     * @param model das Model der Monitoring-Website
     * @return aktuelle Website monitoring.html
     */
    @RequestMapping(value="/", method=RequestMethod.POST, params="action=speedup")
    private String accelerate(@ModelAttribute Input input, Model model) {
        return enqueueTask(controlTruckLock, () -> {
            if(!trucks.isEmpty()){
                String url = Util.getBaseUriComponentsBuilder(leaderContact.getCruisePort(), PathRegister.SPEEDUP).toUriString();
                REST_TEMPLATE.put(url, input.getPace());
            }
            model.addAttribute("trucks", trucks);
            return "monitoring";
        });
    }

    /**
     * Diese Methode wird beim betätigen des "Bremse"-Buttons auf der Monitoring-Website ausgeführt.
     * Sie sendet die geforderte Geschwindigkeit an den Leader Truck, welcher dann beginnt die Geschwindigkeit
     * dementsprechend zu verringern.
     * @param input Input Objekt der Monitoring-Website
     * @param model das Model der Monitoring-Website
     * @return aktuelle Website monitoring.html
     */
    @RequestMapping(value="/", method=RequestMethod.POST, params="action=slowdown")
    private String brake(@ModelAttribute Input input, Model model) {
        return enqueueTask(controlTruckLock, () -> {
            if(!trucks.isEmpty()){
                String url = Util.getBaseUriComponentsBuilder(leaderContact.getCruisePort(), PathRegister.SLOW_DOWN).toUriString();
                REST_TEMPLATE.put(url, input.getPace());
            }
            model.addAttribute("trucks", trucks);
            return "monitoring";
        });
    }

    /**
     * Diese Methode wird beim betätigen des "Stop"-Buttons auf der Monitoring website ausgeführt.
     * Sie sendet den Befehl des Stoppens an den Leader Truck, welcher dann sofort stehen bleibt.
     * @param input Input Objekt der Monitoring-Website
     * @param model das Model der Monitoring-Website
     * @return aktuelle Website monitoring.html
     */
    @RequestMapping(value="/", method=RequestMethod.POST, params="action=stop")
    private String stop(@ModelAttribute Input input, Model model) {
        return enqueueTask(controlTruckLock, () -> {
            if(!trucks.isEmpty()){
                String url = Util.getBaseUriComponentsBuilder(leaderContact.getCruisePort(), PathRegister.STOP).toUriString();
                REST_TEMPLATE.put(url, null);
            }
            model.addAttribute("trucks", trucks);
            return "monitoring";
        });
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

    /**
     * Diese innere Klasse definiert die Methode, um die Informationen eines Trucks anzeigen zu lassen. Die Methode ist nicht
     * in der Hauptklasse, da die Hauptklass wegen Thymelaf immer den Return der Website erwartet
     */
    @RestController
    private class PublicApi {

        /**
         * Diese Methode zeigt die hinterlegten Informationen für einen gewünschten Truck an
         * @param truckPos die Position des Trucks im Platoon (0...x)
         * @return Informationen des Trucks
         */
        @GetMapping(PathRegister.GET_TRUCK_DATA)
        private MonitoringModel getTruckData(@PathVariable int truckPos) {
            return enqueueTask(truckListLock, () -> {
                if((truckPos >= 0) && (truckPos < trucks.size())) {
                    return trucks.get(truckPos);
                }
                return null;
            });
        }

    }

}