package org.g1ga.truckplatooning;

import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Diese Klasse ist eine Hilfsklasse um an benötigte Informationen zu kommen.
 */
public final class Util {

    private final static RestTemplate restTemplate = new RestTemplate();
    private final static String HOST = "localhost";

    private Util() {}

    /**
     * Stellt die Verbindung zwsichen startenden Services zum Registration Server her.
     * @param urlParameter der auszuführende Pfad
     * @param port der port des Senders
     * @return Antwort des Registration Servers
     */
    public static int[] getRegistrationData(String urlParameter, int port) {
        String url = getBaseUriComponentsBuilder(1111, urlParameter).buildAndExpand(port).toUriString();
        try {
            ResponseEntity<int[]> response = restTemplate.getForEntity(url, int[].class);
            return response.getBody();
        } catch (ResourceAccessException e) {
            System.err.println("Der Registration-Server ist offline, bitte starte ihn zuerst und versuche es erneut!");
            return null;
        }
    }

    /**
     * Methode, mit der schnell eine URI gebaut werden kann
     * @param port der Port des Empfängers
     * @param path der Pfad der nachricht die man beim Empfänger ausführen möchte
     * @return die gebaute URI
     */
    public static UriComponentsBuilder getBaseUriComponentsBuilder(int port, String path) {
        return UriComponentsBuilder.newInstance().scheme("http")
                .host(HOST).port(port)
                .path(path);
    }

    /**
     * Übernimmt das ständige (ent-)locken von kritischen Bereichen. Der Bereich, der von dieser Methode gelockt wird,
     * gibt einen Wert zurück, deswegen wird Callable verwendet.
     * @param reentrantLock der Lock, der für den Bereich verwendet werden soll
     * @param callable das, was in dem kritischen Bereich ausgeführt werden soll in einem Objekt einer Klasse, dass das Callable-Interface implementiert.
     * @param <T> Typ des Rückgabewertes
     * @return Rückgabe von den in dem in callable ausgeführten Code,
     *         null, falls eine Exception auftritt
     */
    public static <T> T enqueueTask(ReentrantLock reentrantLock, Callable<T> callable) {
        reentrantLock.lock();
        try {
            return callable.call();
        } catch (Exception ex) {
            System.err.println("Caught exception: " + ex);
        } finally {
            reentrantLock.unlock();
        }
        return null;
    }

    /**
     * Übernimmt das ständige (ent-)locken von kritischen Bereichen. Der Bereich, der von dieser Methode gelockt wird,
     * gibt keinen Wert zurück, deswegen wird Runnable verwendet.
     * @param reentrantLock der Lock, der für den Bereich verwendet werden soll
     * @param runnable das, was in dem kritischen Bereich ausgeführt werden soll in einem Objekt einer Klasse, dass das Runnable-Interface implementiert.
     */
    public static void enqueueTask(ReentrantLock reentrantLock, Runnable runnable) {
        reentrantLock.lock();
        try {
            runnable.run();
        } catch (Exception ex) {
            System.err.println("Caught exception: " + ex);
        } finally {
            reentrantLock.unlock();
        }
    }



}