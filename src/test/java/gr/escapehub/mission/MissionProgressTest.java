package gr.escapehub.mission;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissionProgressTest {
    private static final List<String> LIGHTHOUSE_PATHS = List.of("archive", "engine-room", "radio");

    @Test
    void newMissionOffersThreePathsAndLocksTheFinalPuzzle() {
        MissionProgress mission = new MissionProgress(LIGHTHOUSE_PATHS);

        assertEquals(LIGHTHOUSE_PATHS, mission.availablePaths());
        assertTrue(mission.solvedPaths().isEmpty());
        assertFalse(mission.finalPuzzleAvailable());
    }

    @ParameterizedTest
    @ValueSource(strings = {"archive", "engine-room", "radio"})
    void anyPathCanBeSolvedFirst(String firstPath) {
        MissionProgress mission = new MissionProgress(LIGHTHOUSE_PATHS);

        mission.recordSolved(firstPath);

        assertEquals(Set.of(firstPath), mission.solvedPaths());
        assertEquals(LIGHTHOUSE_PATHS, mission.availablePaths());
        assertFalse(mission.finalPuzzleAvailable());
    }

    @ParameterizedTest
    @MethodSource("pathOrders")
    void finalPuzzleUnlocksOnlyAfterAllThreePaths(List<String> order) {
        MissionProgress mission = new MissionProgress(LIGHTHOUSE_PATHS);

        mission.recordSolved(order.get(0));
        assertFalse(mission.finalPuzzleAvailable());
        mission.recordSolved(order.get(1));
        assertFalse(mission.finalPuzzleAvailable());
        mission.recordSolved(order.get(2));

        assertTrue(mission.finalPuzzleAvailable());
        assertEquals(Set.copyOf(LIGHTHOUSE_PATHS), mission.solvedPaths());
    }

    static Stream<List<String>> pathOrders() {
        return Stream.of(LIGHTHOUSE_PATHS, List.of("radio", "archive", "engine-room"));
    }

    @Test
    void repeatedSolutionDoesNotUnlockOtherPaths() {
        MissionProgress mission = new MissionProgress(LIGHTHOUSE_PATHS);

        mission.recordSolved("archive");
        mission.recordSolved("archive");
        mission.recordSolved("archive");

        assertEquals(Set.of("archive"), mission.solvedPaths());
        assertFalse(mission.finalPuzzleAvailable());
    }

    @Test
    void returnedProgressCannotChangeMissionState() {
        MissionProgress mission = new MissionProgress(LIGHTHOUSE_PATHS);
        Set<String> earlierSnapshot = mission.solvedPaths();
        mission.recordSolved("archive");
        Set<String> currentSnapshot = mission.solvedPaths();

        assertTrue(earlierSnapshot.isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> currentSnapshot.add("radio"));
        assertEquals(Set.of("archive"), mission.solvedPaths());
    }

    @Test
    void nullPathDoesNotChangeProgress() {
        MissionProgress mission = new MissionProgress(LIGHTHOUSE_PATHS);

        assertThrows(NullPointerException.class, () -> mission.recordSolved(null));
        assertTrue(mission.solvedPaths().isEmpty());
        assertFalse(mission.finalPuzzleAvailable());
    }

    @Test
    void separateMissionsDoNotShareProgress() {
        MissionProgress first = new MissionProgress(LIGHTHOUSE_PATHS);
        MissionProgress second = new MissionProgress(LIGHTHOUSE_PATHS);

        first.recordSolved("radio");

        assertTrue(second.solvedPaths().isEmpty());
        assertFalse(second.finalPuzzleAvailable());
    }

    @Test
    void concurrentPlayersKeepBothSolvedPaths() throws Exception {
        MissionProgress mission = new MissionProgress(LIGHTHOUSE_PATHS);
        mission.recordSolved("archive");
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> {
                start.await();
                mission.recordSolved("engine-room");
                return null;
            });
            var second = executor.submit(() -> {
                start.await();
                mission.recordSolved("radio");
                return null;
            });
            start.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        }

        assertEquals(Set.copyOf(LIGHTHOUSE_PATHS), mission.solvedPaths());
        assertTrue(mission.finalPuzzleAvailable());
    }

    @Test
    void smallerMissionUsesDifferentPathsWithoutChangingTheCore() {
        MissionProgress mission = new MissionProgress(List.of("lens", "logbook"));

        mission.recordSolved("logbook");
        assertFalse(mission.finalPuzzleAvailable());
        mission.recordSolved("lens");

        assertTrue(mission.finalPuzzleAvailable());
        assertEquals(List.of("lens", "logbook"), mission.availablePaths());
    }

    @Test
    void changingInputDataCannotChangeAnExistingMission() {
        List<String> paths = new ArrayList<>(LIGHTHOUSE_PATHS);
        MissionProgress mission = new MissionProgress(paths);

        paths.clear();

        assertEquals(LIGHTHOUSE_PATHS, mission.availablePaths());
        assertFalse(mission.finalPuzzleAvailable());
        assertThrows(UnsupportedOperationException.class, () -> mission.availablePaths().clear());
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", ""})
    void unknownPathCannotAdvanceProgress(String path) {
        MissionProgress mission = new MissionProgress(LIGHTHOUSE_PATHS);

        assertThrows(IllegalArgumentException.class, () -> mission.recordSolved(path));
        assertTrue(mission.solvedPaths().isEmpty());
        assertFalse(mission.finalPuzzleAvailable());
    }

    @ParameterizedTest
    @MethodSource("invalidPaths")
    void invalidMissionDataIsRejected(List<String> paths) {
        assertThrows(IllegalArgumentException.class, () -> new MissionProgress(paths));
    }

    static Stream<List<String>> invalidPaths() {
        return Stream.of(List.of(), List.of("archive", "archive"), List.of(" "));
    }
}
