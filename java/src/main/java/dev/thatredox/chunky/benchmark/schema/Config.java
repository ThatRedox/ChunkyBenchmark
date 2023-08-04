package dev.thatredox.chunky.benchmark.schema;

public class Config {
    /**
     * Path to the texture pack to use.
     */
    public final String textures;
    /**
     * Path to the benchmark scene.
     */
    public final String scene;
    /**
     * File to save the render to.
     */
    public final String save;
    /**
     * Number of threads to use while rendering.
     */
    public final int threads;
    /**
     * Sample batch size.
     */
    public final int samples;
    /**
     * Number of batches.
     */
    public final int batches;

    private Config() {
        this.textures = null;
        this.scene = null;
        this.save = null;
        this.threads = 1;
        this.samples = 64;
        this.batches = 4;
    }

    @Override
    public String toString() {
        return String.format("Config: %s textures. %s scene. %d threads. %d samples per batch. %d batches.",
                textures, scene, threads, samples, batches);
    }
}
