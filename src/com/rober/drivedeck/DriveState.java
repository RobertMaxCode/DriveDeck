package com.rober.drivedeck;

final class DriveState {
    private static final long WAZE_TTL_MS = 90000;

    private static volatile String navPackage = "";
    private static volatile String navLine = "";
    private static volatile String wazeLine = "";
    private static volatile long wazeAt = 0;

    private DriveState() {
    }

    static synchronized void setNav(String pkg, String line) {
        navPackage = pkg;
        navLine = line;
    }

    static synchronized void clearNav(String pkg) {
        if (pkg == null || pkg.equals(navPackage)) {
            navPackage = "";
            navLine = "";
        }
    }

    static synchronized void clearAll() {
        navPackage = "";
        navLine = "";
    }

    static synchronized void setWazeLine(String line) {
        wazeLine = line;
        wazeAt = System.currentTimeMillis();
    }

    static synchronized void clearWaze() {
        wazeLine = "";
        wazeAt = 0;
    }

    static synchronized String hudLine() {
        String waze = wazeLine;
        if (!waze.isEmpty() && System.currentTimeMillis() - wazeAt < WAZE_TTL_MS) {
            return waze;
        }
        return navLine;
    }
}
