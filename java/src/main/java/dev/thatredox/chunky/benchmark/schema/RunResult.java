package dev.thatredox.chunky.benchmark.schema;

public class RunResult {
    /**
     * Total samples rendered since the JVM was started.
     */
    public final int totalSamples;
    /**
     * Total samples rendered in this run.
     */
    public final int runSamples;
    /**
     * Samples per second of this run.
     */
    public final int samplesPerSecond;
    /**
     * Render time of this run in milliseconds.
     */
    public final long renderTime;

    public RunResult(int totalSamples, int runSamples, int samplesPerSecond, long renderTime) {
        this.totalSamples = totalSamples;
        this.runSamples = runSamples;
        this.samplesPerSecond = samplesPerSecond;
        this.renderTime = renderTime;
    }

    private RunResult() {
        this(0, 0, 0, 0);
    }

    @Override
    public String toString() {
        return String.format("Run: %4d total samples. %4d run samples. %8d samples per second. %d milliseconds.",
                totalSamples, runSamples, samplesPerSecond, renderTime);
    }
}
