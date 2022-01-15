package io.github.thatredox.chunkybenchmark;

import org.apache.commons.cli.*;
import org.apache.commons.math3.stat.descriptive.rank.Median;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.llbit.chunky.main.Chunky;
import se.llbit.chunky.main.ChunkyOptions;
import se.llbit.chunky.renderer.*;
import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.chunky.resources.TexturePackLoader;
import se.llbit.log.Level;
import se.llbit.log.Receiver;
import se.llbit.util.TaskTracker;

import java.io.*;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class Benchmark {
    private final static Logger LOGGER = LoggerFactory.getLogger(Benchmark.class);
    private final static Logger CHUNKY_LOGGER = LoggerFactory.getLogger(Chunky.class);

    public class Slf4jLogReceiver extends Receiver {
        @Override
        public void logEvent(Level level, String s) {
            switch (level) {
                case ERROR:
                    CHUNKY_LOGGER.error(s);
                    return;
                case WARNING:
                    CHUNKY_LOGGER.warn(s);
                    return;
                case INFO:
                default:
                    CHUNKY_LOGGER.info(s);
            }
        }

        @Override
        public void logEvent(Level level, String message, Throwable thrown) {
            switch (level) {
                case ERROR:
                    CHUNKY_LOGGER.error(message, thrown);
                    return;
                case WARNING:
                    CHUNKY_LOGGER.warn(message, thrown);
                    return;
                case INFO:
                default:
                    CHUNKY_LOGGER.info(message, thrown);
            }
        }

        @Override
        public void logEvent(Level level, Throwable thrown) {
            switch (level) {
                case ERROR:
                    CHUNKY_LOGGER.error(thrown.getMessage(), thrown);
                    return;
                case WARNING:
                    CHUNKY_LOGGER.warn(thrown.getMessage(), thrown);
                    return;
                case INFO:
                default:
                    CHUNKY_LOGGER.info(thrown.getMessage(), thrown);
            }
        }
    }

    public static void main(String[] args) throws IOException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd;
        try {
            cmd = parser.parse(Main.options, args);
        } catch (ParseException e) {
            new HelpFormatter().printHelp("java -jar ChunkyBenchmark.jar", Main.options);
            System.exit(128);
            return;
        }

        int samples = 64;
        if (cmd.hasOption("s")) {
            samples = Integer.parseInt(cmd.getOptionValue("s"));
        }
        LOGGER.debug("Rendering with {} samples.", samples);

        int batches = 4;
        if (cmd.hasOption("b")) {
            batches = Integer.parseInt(cmd.getOptionValue("b"));
        }
        LOGGER.debug("Rendering {} batches.", batches);

        if (cmd.hasOption("t")) {
            String texturePack = cmd.getOptionValue("t");
            LOGGER.info("Loading texture pack: {}", texturePack);
            TexturePackLoader.loadTexturePacks(texturePack, false);
        } else {
            LOGGER.debug("Loading default textures.");
            Chunky.loadDefaultTextures();
        }

        ChunkyOptions chunkyOptions = ChunkyOptions.getDefaults();
        if (cmd.hasOption("threads")) {
            chunkyOptions.renderThreads = Integer.parseInt(cmd.getOptionValue("threads"));
        }
        LOGGER.debug("Rendering with {} threads.", chunkyOptions.renderThreads);

        chunkyOptions.sppPerPass = samples;
        LOGGER.debug("Rendering with {} spp per pass.", samples);

        Chunky chunky = new Chunky(chunkyOptions);

        Field chunkyHeadless = chunky.getClass().getDeclaredField("headless");
        chunkyHeadless.setAccessible(true);
        chunkyHeadless.set(chunky, true);

        chunky.getSceneManager().loadScene(cmd.getOptionValue("i"));
        Scene scene = chunky.getSceneManager().getScene();
        scene.setTargetSpp(samples);

        AtomicLong atomicRenderTime = new AtomicLong();
        AtomicInteger atomicSps = new AtomicInteger();

        Results results = new Results();

        for (int b = 0; b < batches; b++) {
            DefaultRenderManager renderer = new DefaultRenderManager(chunky.getRenderContext(), true);
            renderer.setSceneProvider((SceneProvider) chunky.getSceneManager());
            renderer.setSnapshotControl(new SnapshotControl() {
                @Override
                public boolean saveSnapshot(Scene scene, int nextSpp) {
                    return false;
                }

                @Override
                public boolean saveRenderDump(Scene scene, int nextSpp) {
                    return false;
                }
            });
            renderer.setRenderTask(TaskTracker.Task.NONE);
            renderer.addRenderListener(new RenderStatusListener() {
                @Override
                public void setRenderTime(long time) {
                    atomicRenderTime.set(time);
                }

                @Override
                public void setSamplesPerSecond(int sps) {
                    atomicSps.set(sps);
                }

                @Override
                public void setSpp(int spp) {

                }

                @Override
                public void renderStateChanged(RenderMode state) {

                }
            });

            scene.haltRender();
            scene.startHeadlessRender();

            renderer.start();
            renderer.join();
            renderer.shutdown();

            Run run = new Run(samples * (b + 1), samples, atomicSps.get(), atomicRenderTime.get());
            LOGGER.debug("{}", run);
            results.runs.add(run);
            System.out.println(run);
        }

        if (cmd.hasOption("tempOut")) {
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                    new FileOutputStream(cmd.getOptionValue("tempOut"))))) {
                results.store(out);
            }
        } else {
            LOGGER.error("Output file not specified.");
        }

        System.exit(0);
    }

    public static class Run {
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

        public Run(int totalSamples, int runSamples, int samplesPerSecond, long renderTime) {
            this.totalSamples = totalSamples;
            this.runSamples = runSamples;
            this.samplesPerSecond = samplesPerSecond;
            this.renderTime = renderTime;
        }

        public static Run load(DataInputStream in) throws IOException {
            int totalSamples = in.readInt();
            int runSamples = in.readInt();
            int samplesPerSecond = in.readInt();
            long renderTime = in.readLong();
            return new Run(totalSamples, runSamples, samplesPerSecond, renderTime);
        }

        public void store(DataOutputStream out) throws IOException {
            out.writeInt(totalSamples);
            out.writeInt(runSamples);
            out.writeInt(samplesPerSecond);
            out.writeLong(renderTime);
        }

        @Override
        public String toString() {
            return String.format("Run: %4d total samples. %4d run samples. %8d samples per second. %d milliseconds.",
                    totalSamples, runSamples, samplesPerSecond, renderTime);
        }
    }

    public static class Results {
        public final ArrayList<Run> runs = new ArrayList<>();

        public double getMedianSps() {
            Median median = new Median();
            median.setData(runs.stream().mapToDouble(run -> run.samplesPerSecond).toArray());
            return median.evaluate();
        }

        public void store(DataOutputStream out) throws IOException {
            out.writeInt(runs.size());
            for (Run run : runs) {
                run.store(out);
            }
        }

        public static Results load(DataInputStream in) throws IOException {
            Results res = new Results();
            int size = in.readInt();
            res.runs.ensureCapacity(size);
            for (int i = 0; i < size; i++) {
                res.runs.add(Run.load(in));
            }
            return res;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append(String.format("%d runs:\n", runs.size()));
            runs.forEach(run -> {
                builder.append('\t');
                builder.append(run.toString());
                builder.append('\n');
            });
            return builder.toString();
        }
    }
}
