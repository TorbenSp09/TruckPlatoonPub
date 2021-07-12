package org.g1ga.truckplatooning;

/**
 * Diese Klasse ist ein Pfad Register in dem alle Pfade der Controller gespeichert sind.
 */
public final class PathRegister {

    public static final String HEALTH_CHECK = "/healthcheck";
    public static final String CHECK_BACK_TRUCK_PORT = "/checkBackTruckPort";
    public static final String CLOSE_GAP = "/closegap";
    public static final String UPDATE_FRONT_TRUCK_PORT = "/updatefronttruck";
    public static final String UPDATE_BACK_TRUCK = "/updatebacktruck";
    public static final String ADD_CRUISE_CONTROL = "/addcruisecontrol";
    public static final String RECEIVE_CONTINUE_ELECTION_PATH = "/rce";
    public static final String RECEIVE_NEW_LEADER_PATH = "/rnl";
    public static final String SHUTDOWN = "/actuator/shutdown";
    public static final String NOTIFY_BACK_TRUCK_LEAVE_PLATOON = "/notifyBackTruckLeavePlatoon";

    //CruiseService
    public static final String CLOSE_GAP_LEADER = "/closegapleader";
    public static final String SET_CRUISE_PORTS = "/setcruiseports";
    public static final String SET_LEADER = "/setleader";
    public static final String GET_SPEED = "/getspeed";
    public static final String SPEEDUP = "/speedup";
    public static final String SLOW_DOWN = "/slowdown";
    public static final String STOP = "/stop";
    public static final String IS_LEADER = "/isleader";
    public static final String GET_INITIAL_SPEED = "/getinitialspeed";
    public static final String NEW_TRUCK_SIGN_IN = "/newTruckSignIn";

    //Monitoring
    public static final String SET_LIST = "/setList";
    public static final String REMOVE_TRUCK_BY_PLATOON = "/removeTruckByPlatoon/{platooningPort}";
    public static final String SET_SPEED = "/setSpeed/{cruisePort}";
    public static final String GET_TRUCK_DATA = "/getTruckData/{truckPos}";

    //Registration
    public static final String SET_LEADER_PORT = "/setleaderport";
    public static final String REGISTER_PLATOON = "/registerplatoon/{port}";
    public static final String REGISTER_CRUISE = "/registercruise/{port}";
    public static final String RESET = "/reset";
    public static final String UPDATE_ELECTION_STATUS = "/updateElectionStatus";

    private PathRegister() {}

}
