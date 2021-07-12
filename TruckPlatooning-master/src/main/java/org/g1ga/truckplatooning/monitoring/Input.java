package org.g1ga.truckplatooning.monitoring;

/**
 * Diese Klasse wird benötigt, um die vom Nutzer eingegebene Geschwindigkeit auf der website monitoring.html auszulesen.
 */
public class Input {

    //Geschwindigkeit in km/h
    private double pace;

    /**
     * Die Methode gibt die Geschwindigkeit aus dem Formular zurück.
     * @return die aktuelle Geschwindigkeit
     */
    public double getPace() {
        return pace;
    }

    /**
     * Methode wird im monitoring.html genutzt, um die Geschwindigkeit zu setzen.
     * @param pace die zu setzende Geschwindigkeit
     */
    public void setPace(double pace) {
        this.pace=pace;
    }

}
