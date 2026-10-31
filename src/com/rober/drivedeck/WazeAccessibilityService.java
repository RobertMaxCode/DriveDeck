package com.rober.drivedeck;

import android.accessibilityservice.AccessibilityService;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

public class WazeAccessibilityService extends AccessibilityService {
    private static final String WAZE = "com.waze";
    private static final String ID_INSTRUCTION = "com.waze:id/navBarStreetLine";
    private static final String ID_DISTANCE = "com.waze:id/navBarDistance";
    private static final String ID_TIME = "com.waze:id/lblTimeToDestination";
    private static final String ID_TOTAL = "com.waze:id/lblDistanceToDestination";
    private static final String ID_ARRIVAL = "com.waze:id/lblArrivalTime";
    private static final long SCAN_GAP_MS = 250;
    private static final int MAX_MISSES = 4;

    private long lastScan;
    private int misses;

    @Override
    public void onServiceConnected() {
        DriveState.clearWaze();
        misses = 0;
        scan();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            return;
        }
        CharSequence pkg = event.getPackageName();
        if (pkg == null || !WAZE.equals(pkg.toString())) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (now - lastScan < SCAN_GAP_MS) {
            return;
        }
        lastScan = now;
        scan();
    }

    private void scan() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            miss();
            return;
        }
        String line = null;
        try {
            CharSequence pkg = root.getPackageName();
            if (pkg != null && WAZE.equals(pkg.toString())) {
                line = extract(root);
            }
        } finally {
            root.recycle();
        }
        if (line == null || line.isEmpty()) {
            miss();
        } else {
            misses = 0;
            DriveState.setWazeLine(line);
        }
    }

    private void miss() {
        if (++misses >= MAX_MISSES) {
            misses = 0;
            DriveState.clearWaze();
        }
    }

    private static String extract(AccessibilityNodeInfo root) {
        String[] values = new String[5];
        collect(root, values);
        String instruction = values[0] == null ? "" : values[0];
        if (instruction.isEmpty()) {
            return null;
        }
        String distance = values[1] == null ? "" : values[1];
        StringBuilder line = new StringBuilder(instruction);
        if (!distance.isEmpty() && !instruction.contains(distance)) {
            line.insert(0, distance + " · ");
        }
        append(line, values[2]);
        append(line, values[3]);
        if (values[4] != null && !values[4].isEmpty() && !line.toString().contains(values[4])) {
            append(line, "Llegada " + values[4]);
        }
        return line.toString();
    }

    private static void append(StringBuilder line, String value) {
        if (value != null && !value.isEmpty() && !line.toString().contains(value)) {
            line.append(" · ").append(value);
        }
    }

    private static void collect(AccessibilityNodeInfo node, String[] out) {
        String id = node.getViewIdResourceName();
        if (id != null) {
            int slot = slot(id);
            if (slot >= 0 && out[slot] == null) {
                CharSequence text = node.getText();
                if (text == null) {
                    text = node.getContentDescription();
                }
                if (text != null) {
                    String value = text.toString().trim();
                    if (!value.isEmpty()) {
                        out[slot] = value;
                    }
                }
            }
        }
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collect(child, out);
                child.recycle();
            }
        }
    }

    private static int slot(String id) {
        if (ID_INSTRUCTION.equals(id)) {
            return 0;
        }
        if (ID_DISTANCE.equals(id)) {
            return 1;
        }
        if (ID_TIME.equals(id)) {
            return 2;
        }
        if (ID_TOTAL.equals(id)) {
            return 3;
        }
        if (ID_ARRIVAL.equals(id)) {
            return 4;
        }
        return -1;
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        DriveState.clearWaze();
        super.onDestroy();
    }
}
