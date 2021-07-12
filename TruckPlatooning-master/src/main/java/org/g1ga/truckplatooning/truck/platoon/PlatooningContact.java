package org.g1ga.truckplatooning.truck.platoon;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Diese Klasse beinhaltet die wichtigste Informationen eines Trucks und ist unter anderem für den Wahlalgorithmus und die Monitoring Website wichtig,
 * da diese mit Instanzen dieser Klasse arbeiten.
 */
public class PlatooningContact implements Comparable<PlatooningContact> {

    private final int PLATOONING_PORT;
    private int cruisePort;
    private final long PLATOONING_PID;

    /**
     * Konstruktor der eine neue Instanz dieser Klasse erstellt, hier mit allen Attributen als Parameter,
     * ist unter anderem für das Serialisieren in und Deserialisieren vom JSON-Format relevant.
     * @param platooningPort der Port des Platoonong Services
     * @param cruisePort der Port des Cruise-Services
     * @param platooningPid die Prozess-ID des Platooning Services
     */
    @JsonCreator
    public PlatooningContact(@JsonProperty("platooningPort") int platooningPort, @JsonProperty("cruisePort") int cruisePort, @JsonProperty("platooningPid") long platooningPid) {
        PLATOONING_PID = platooningPid;
        PLATOONING_PORT = platooningPort;
        this.cruisePort = cruisePort;
    }

    /**
     * Konstruktor, der eine neue Instanz dieser Klasse erstellt.
     * @param platooningPort der Port des Platoonong Services
     * @param platooningPid die Prozess-ID des Platooning Services
     */
    public PlatooningContact(int platooningPort, long platooningPid) {
        PLATOONING_PID = platooningPid;
        PLATOONING_PORT = platooningPort;
        this.cruisePort = 0;
    }

    /**
     * Überschreibt die compareTo Methode, sodass die Prozess-ID das Vergleichskriterium ist.
     * @param platooningContact der PlatooningContact, dessen PID mit der von diesem Objekt verglichen werden soll
     * @return den Wert {@code 0} wenn {@code PLATOONING_PID == platooningContact.getPlatooningPid()};
     * einen Wert kleiner {@code 0} wenn {@code PLATOONING_PID < platooningContact.getPlatooningPid()}; und
     * einen Wert größer {@code 0} wenn {@code PLATOONING_PID > platooningContact.getPlatooningPid()}
     */
    @Override
    public synchronized int compareTo(PlatooningContact platooningContact) {
        return Long.compare(PLATOONING_PID, platooningContact.getPlatooningPid());
    }

    /**
     * Überschreibt die equals Methode, sodass sie true zurückgibt, wenn das übergebene Objekt eine Referenz auf das
     * selbe Objekt im Speicher hat wie dieses Platooning Contact Objekt. Außerdem wird true zurückgegeben, wenn die drei
     * Attribute der zu vergleichenden Platooning Contacts überinstimmen. Wenn das übergebene Objekt null oder eine Instanz
     * einer anderen Klasse ist, wird false zurückgegeben.
     * @param o das zu vergleichende Objekt
     * @return true, wenn das zu vergleichende Objekt das aktuelle Objekt referenziert,
     *         false, wenn das zu vergleichende Objekt null ist oder die Klassen nicht übereinstimmen
     *                oder wenn der PLATOONING_PORT, der CRUISE_PORT und die PLATOONING_PID mit diesem und dem zu vergleichenden Objekt nicht übereinstimmen.
     *
     */
    @Override
    public synchronized boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlatooningContact that = (PlatooningContact) o;
        return PLATOONING_PORT == that.PLATOONING_PORT && cruisePort == that.cruisePort && PLATOONING_PID == that.PLATOONING_PID;
    }

    /**
     * Überschreibt die hashCode Methode, sodass die dei Attribute dieser Klasse gehasht werden.
     * @return den Hash generiert durch die Objects.hash-Methode mit Angabe des PLATOONING_PORTS, des cruisePorts und der PLATOONING_PID
     */
    @Override
    public synchronized int hashCode() {
        return Objects.hash(PLATOONING_PORT, cruisePort, PLATOONING_PID);
    }

    /**
     * Überschreibt die toString Methode, sodass sie die Informationen dies Platooning Contacts als String zurückgibt.
     * @return einen String, der die Informationen dieses PlatooningContacts enthält.
     */
    @Override
    public synchronized String toString() {
        return "PlatooningContact - PlatooningPort: " + PLATOONING_PORT + ", CruisePort: " + cruisePort + ", PID: " + PLATOONING_PID;
    }

    /**
     * Getter Methode, die den den Port des Platooning Services zurückgibt
     * @return der Port des Platooning Services
     */
    public synchronized int getPlatooningPort() {
        return PLATOONING_PORT;
    }

    /**
     * Getter Methode, die den Port des Cruise Services zurückgibt
     * @return der zugehörige CruisePort dieses PlatooningServices
     */
    public synchronized int getCruisePort() {
        return cruisePort;
    }

    /**
     * Getter Methode, die die Prozess-ID des Platooning Services dieses Platooning-Contacts Objektes zurückgibt
     * @return die Prozess-ID des Platooning Services
     */
    public synchronized long getPlatooningPid() {
        return PLATOONING_PID;
    }

    /**
     * Setzt den Wert des Cruise Service Port
     * @param cruisePort der Port des Cruise Services
     */
    public synchronized void setCruisePort(int cruisePort) {
        this.cruisePort = cruisePort;
    }

}
