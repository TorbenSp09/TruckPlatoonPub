package org.g1ga.truckplatooning.monitoring;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Diese Klasse ist das Model für die Anzeige der Trucks auf der Monitoring-Website. Das Model beinhaltet alle Informationen,
 * die zu den Trucks gezeigt werden sollen.
 */
public class MonitoringModel {

    public long pid;
    public int platoonPort;
    public int cruisePort;
    public int speed;

    /**
     * Konstruktor welcher neue Instanzen dieser Klasse erzeugt. Es müssen stets alle Attribute angegeben werden.
     * @param pid Prozess-ID des Trucks
     * @param platoonPort Port des Platooning-Services
     * @param cruisePort Port des Cruise-Services
     * @param speed Aktuelle Geschwindigkeit
     */
    @JsonCreator
    public MonitoringModel(@JsonProperty("pid") long pid, @JsonProperty("platoonPort") int platoonPort, @JsonProperty("cruisePort") int cruisePort, @JsonProperty("speed") int speed) {
        this.pid = pid;
        this.platoonPort = platoonPort;
        this.cruisePort = cruisePort;
        this.speed = speed;
    }

    /**
     * Gibt den Platooning Port des Monitoring-Trucks zurück
     * @return der Port des Platooning Services
     */
    public int getPlatoonPort() {
        return platoonPort;
    }

    /**
     * Gibt den Cruise Port des Monitoring-Trucks zurück
     * @return der Port des Cruise Services
     */
    public int getCruisePort() {
        return cruisePort;
    }

    /**
     * Setzt die Geschwindigkeit zur Anzeige des Monitoring-Trucks
     * @param speed aktuelle Geschwindigkeit des Trucks
     */
    public void setSpeed(int speed) {
        this.speed = speed;
    }

    /**
     * Gibt die Prozess-ID des Monitoring-Trucks zurück
     * @return die Prozess ID
     */
    public long getPid() {
        return pid;
    }

    /**
     * Überschreibt die toString Methode, um beim printen hilfreiche Informationen anzuzeigen
     * @return String-Repräsentation eines Objektes dieser Klasse
     */
    @Override
    public String toString() {
        return "Monitoring Model: " + getPid() + ", PlatoonPort: " + getPlatoonPort() + ", cruisePort " + getCruisePort();
    }



}
