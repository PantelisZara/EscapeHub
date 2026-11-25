package gr.escapehub.mission;

import java.util.List;

public final class RadioPuzzle {
    private static final String PATH = "radio";
    private static final String MESSAGE = "Τρεις σύντομες αναλαμπές";
    private final MissionProgress mission;
    private final List<String> signals;
    private final boolean[] confirmed = new boolean[2];

    public RadioPuzzle(MissionProgress mission, String firstSignal, String secondSignal) {
        this.mission = mission;
        signals = List.of(firstSignal, secondSignal);
    }

    public synchronized boolean confirm(int seat, String partnerSignal) {
        if (!signals.get(1 - seat).equals(partnerSignal)) {
            return false;
        }
        confirmed[seat] = true;
        if (confirmed[0] && confirmed[1]) {
            mission.recordSolved(PATH);
        }
        return true;
    }

    public synchronized View view(int seat) {
        return new View(signals.get(seat), confirmed[seat], confirmed[1 - seat], message());
    }

    public synchronized String message() {
        return confirmed[0] && confirmed[1] ? MESSAGE : null;
    }

    public record View(String signal, boolean confirmed, boolean partnerConfirmed, String message) { }
}
