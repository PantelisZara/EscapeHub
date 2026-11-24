package gr.escapehub.mission;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class MissionProgress {
    private final List<String> paths;
    private final Set<String> solvedPaths = new HashSet<>();

    public MissionProgress(List<String> pathIds) {
        paths = List.copyOf(pathIds);
        if (paths.isEmpty() || paths.stream().anyMatch(String::isBlank)
                || new HashSet<>(paths).size() != paths.size()) {
            throw new IllegalArgumentException("Paths must be nonempty, unique and nonblank");
        }
    }

    public List<String> availablePaths() {
        return paths;
    }

    public synchronized Set<String> solvedPaths() {
        return Set.copyOf(solvedPaths);
    }

    synchronized void recordSolved(String path) {
        Objects.requireNonNull(path, "path");
        if (!paths.contains(path)) {
            throw new IllegalArgumentException("Unknown investigation path");
        }
        solvedPaths.add(path);
    }

    public synchronized boolean finalPuzzleAvailable() {
        return solvedPaths.containsAll(paths);
    }
}
