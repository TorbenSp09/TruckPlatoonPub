package org.g1ga.truckplatooning.truck.platoon;

/**
 * Diese Klasse ist das Model für den Platooning Service. Sie beinhaltet alle Attribute und Methoden, die für die
 * Organisation eines Trucks im Platoonong eine Rolle spielen.
 */
public class Platooning {

    private static Platooning instance;
    private boolean isLeader;
    private int leaderPort;
    //for the leading truck this is the reference to the last truck
    private int frontTruckPort;
    private int backTruckPort;
    private int ownCruiseControlPort;

    /**
     * Konstruktor, der ein Platoonoing standartmäßig als nicht-leader setzt
     */
    private Platooning() {
        isLeader = false;
    }

    /**
     * Getter Methode für die Platooning-Instanz
     * @return Platooning Instanz
     */
    static synchronized Platooning getInstance() {
        if (instance == null) {
            instance = new Platooning();
        }
        return instance;
    }

    /**
     * Methode fragt ab, ob der Platooning Service zum Leader-Truck gehört
     * @return Wert von isLeader
     */
    boolean isLeader() {
        return isLeader;
    }

    /**
     * Setzt den Wert von isLeader auf den übergebenen Parameter
     * @param leader der gewünschte boolean Wert
     */
    void setLeader(boolean leader) {
        isLeader = leader;
    }

    /**
     * Getter Methode für den aktuellen Leader-Platooning-Port
     * @return Port des aktuellen Leaders
     */
    int getLeaderPort() {
        return leaderPort;
    }

    /**
     * Setzt den Port des aktuellen Leaders
     * @param leaderPort Port des neuen Leaders
     */
    void setLeaderPort(int leaderPort) {
        System.out.println("Setze Leader-Port: " + leaderPort);
        this.leaderPort = leaderPort;
    }

    /**
     * Gibt den Port des Platooning-Services des Trucks zurück, der gerade vor diesem fährt
     * @return Port des Vordertrucks
     */
    int getFrontTruckPort() {
        return frontTruckPort;
    }

    /**
     * Setzt den Port des Platooning Services des Trucks, der gerade vor diesem fährt
     * @param frontTruckPort Port des vorderen Trucks
     */
    void setFrontTruckPort(int frontTruckPort) {
        System.out.println("Setze FrontTruck-Port: " + frontTruckPort);
        this.frontTruckPort = frontTruckPort;
    }

    /**
     * Gibt den Port des Platooning-Services des Trucks zurück, der gerade hinter diesem fährt
     * @return Port des Hintertrucks
     */
    int getBackTruckPort() {
        return backTruckPort;
    }

    /**
     * Setzt den Port des Platooning Services des Trucks, der gerade hinter diesem fährt
     * @param backTruckPort Port des hinteren Trucks
     */
    void setBackTruckPort(int backTruckPort) {
        System.out.println("Setze BackTruck-Port: " + backTruckPort);
        this.backTruckPort = backTruckPort;
    }

    /**
     * Gibt den Port des zu diesem Platooning Objekt gehörenden Cruise Services zurück
     * @return Port des Cruise Services
     */
    int getOwnCruiseControlPort() {
        return ownCruiseControlPort;
    }

    /**
     * Setzt den Wert des zu diesem Platoonong Objekt gehörenden Cruise Services
     * @param ownCruiseControlPort Port des Cruise Services
     */
    void setOwnCruiseControlPort(int ownCruiseControlPort) {
        System.out.println("Setze CruiseControlPort: " + ownCruiseControlPort);
        this.ownCruiseControlPort = ownCruiseControlPort;
    }

}
