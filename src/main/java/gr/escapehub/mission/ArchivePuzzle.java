package gr.escapehub.mission;

public final class ArchivePuzzle {
    private static final String PATH = "archive";
    private final MissionProgress mission;
    private final String correctMarker;

    public ArchivePuzzle(MissionProgress mission, String correctMarker) {
        this.mission = mission;
        this.correctMarker = correctMarker;
    }

    public boolean confirm(String marker) {
        if (!correctMarker.equals(marker)) {
            return false;
        }
        mission.recordSolved(PATH);
        return true;
    }

    public boolean solved() {
        return mission.solvedPaths().contains(PATH);
    }
}
