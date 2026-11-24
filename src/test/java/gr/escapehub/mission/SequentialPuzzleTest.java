package gr.escapehub.mission;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SequentialPuzzleTest {
    private static final List<String> STEPS = List.of("open-valve", "prime-pump", "start-generator");
    private final MissionProgress mission = new MissionProgress(List.of("archive", "engine-room", "radio"));

    private SequentialPuzzle engineRoom() {
        return new SequentialPuzzle(mission, "engine-room", STEPS, "power:240");
    }

    @Test
    void onlyTheCompleteSequenceRevealsPowerAndSolvesThePath() {
        SequentialPuzzle puzzle = engineRoom();

        assertEquals(Optional.empty(), puzzle.act("open-valve"));
        assertEquals(Optional.empty(), puzzle.act("prime-pump"));
        assertEquals(2, puzzle.completedSteps());
        assertEquals(Set.of(), mission.solvedPaths());
        assertEquals(Optional.of("power:240"), puzzle.act("start-generator"));
        assertEquals(Set.of("engine-room"), mission.solvedPaths());
        assertFalse(mission.finalPuzzleAvailable());
    }

    @Test
    void wrongActionResetsTheAttemptAndAllowsAFullRetry() {
        SequentialPuzzle puzzle = engineRoom();
        puzzle.act("open-valve");

        assertEquals(Optional.empty(), puzzle.act("start-generator"));
        assertEquals(0, puzzle.completedSteps());
        assertEquals(Optional.empty(), puzzle.act("prime-pump"));
        assertEquals(Set.of(), mission.solvedPaths());

        puzzle.act("open-valve");
        puzzle.act("prime-pump");
        assertEquals(Optional.of("power:240"), puzzle.act("start-generator"));
    }

    @ParameterizedTest
    @MethodSource("wrongOrders")
    void alternateOrdersCannotBypassTheSequence(List<String> order) {
        SequentialPuzzle puzzle = engineRoom();

        for (String action : order) {
            assertEquals(Optional.empty(), puzzle.act(action));
        }

        assertEquals(Set.of(), mission.solvedPaths());
    }

    static Stream<List<String>> wrongOrders() {
        return Stream.of(
                List.of("open-valve", "start-generator", "prime-pump"),
                List.of("prime-pump", "open-valve", "start-generator"),
                List.of("prime-pump", "start-generator", "open-valve"),
                List.of("start-generator", "open-valve", "prime-pump"),
                List.of("start-generator", "prime-pump", "open-valve"));
    }

    @Test
    void repeatedOrUnknownActionsCannotSkipSteps() {
        SequentialPuzzle puzzle = engineRoom();
        puzzle.act("open-valve");

        assertEquals(Optional.empty(), puzzle.act("open-valve"));
        assertEquals(0, puzzle.completedSteps());
        assertEquals(Optional.empty(), puzzle.act("power:240"));
        assertEquals(Set.of(), mission.solvedPaths());
    }

    @Test
    void completedPuzzleKeepsItsFindingWhenAnActionIsRetried() {
        SequentialPuzzle puzzle = engineRoom();
        STEPS.forEach(puzzle::act);

        assertEquals(Optional.of("power:240"), puzzle.act("start-generator"));
        assertEquals(Optional.of("power:240"), puzzle.act("open-valve"));
        assertEquals(3, puzzle.completedSteps());
        assertEquals(Set.of("engine-room"), mission.solvedPaths());
    }

    @Test
    void separateRoomsCannotCombineTheirPartialSequences() {
        SequentialPuzzle first = engineRoom();
        MissionProgress other = new MissionProgress(List.of("engine-room"));
        SequentialPuzzle second = new SequentialPuzzle(other, "engine-room", STEPS, "power:240");

        first.act("open-valve");
        second.act("prime-pump");
        first.act("start-generator");

        assertEquals(Set.of(), mission.solvedPaths());
        assertEquals(Set.of(), other.solvedPaths());
    }

    @Test
    void differentMissionDataSupportsRepeatedStepsWithoutCoreChanges() {
        MissionProgress other = new MissionProgress(List.of("lens"));
        SequentialPuzzle puzzle = new SequentialPuzzle(other, "lens", List.of("turn", "turn"), "lens:aligned");

        assertEquals(Optional.empty(), puzzle.act("turn"));
        assertEquals(Optional.of("lens:aligned"), puzzle.act("turn"));
        assertEquals(Set.of("lens"), other.solvedPaths());
    }

    @Test
    void unknownPathIsRejectedBeforeAnyActions() {
        assertThrows(IllegalArgumentException.class,
                () -> new SequentialPuzzle(mission, "unknown", STEPS, "power:240"));
        assertEquals(Set.of(), mission.solvedPaths());
    }

    @ParameterizedTest
    @MethodSource("invalidSteps")
    void emptyOrBlankSequenceIsRejected(List<String> steps) {
        assertThrows(IllegalArgumentException.class,
                () -> new SequentialPuzzle(mission, "engine-room", steps, "power:240"));
    }

    static Stream<List<String>> invalidSteps() {
        return Stream.of(List.of(), List.of("open-valve", " "));
    }

    @Test
    void blankFindingIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new SequentialPuzzle(mission, "engine-room", STEPS, " "));
    }

    @Test
    void changingInputCannotChangeTheRequiredSequence() {
        List<String> steps = new ArrayList<>(STEPS);
        SequentialPuzzle puzzle = new SequentialPuzzle(mission, "engine-room", steps, "power:240");
        steps.clear();

        STEPS.forEach(puzzle::act);

        assertEquals(Set.of("engine-room"), mission.solvedPaths());
    }

    @Test
    void nullActionCannotCorruptAnAttempt() {
        SequentialPuzzle puzzle = engineRoom();
        puzzle.act("open-valve");

        assertThrows(NullPointerException.class, () -> puzzle.act(null));
        assertEquals(1, puzzle.completedSteps());
        assertEquals(Set.of(), mission.solvedPaths());
    }
}
