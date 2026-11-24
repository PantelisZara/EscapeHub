package gr.escapehub.mission;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class SequentialPuzzle {
    private final MissionProgress mission;
    private final String path;
    private final List<String> steps;
    private final String finding;
    private int completedSteps;

    public SequentialPuzzle(MissionProgress mission, String path, List<String> steps, String finding) {
        this.mission = Objects.requireNonNull(mission, "mission");
        this.path = Objects.requireNonNull(path, "path");
        this.steps = List.copyOf(steps);
        this.finding = Objects.requireNonNull(finding, "finding");
        if (!mission.availablePaths().contains(path) || this.steps.isEmpty()
                || this.steps.stream().anyMatch(String::isBlank) || finding.isBlank()) {
            throw new IllegalArgumentException("A known path, nonblank steps and a finding are required");
        }
    }

    public synchronized Optional<String> act(String action) {
        Objects.requireNonNull(action, "action");
        if (completedSteps == steps.size()) {
            return Optional.of(finding);
        }
        if (!steps.get(completedSteps).equals(action)) {
            completedSteps = 0;
            return Optional.empty();
        }
        completedSteps++;
        if (completedSteps == steps.size()) {
            mission.recordSolved(path);
            return Optional.of(finding);
        }
        return Optional.empty();
    }

    public synchronized int completedSteps() {
        return completedSteps;
    }
}
