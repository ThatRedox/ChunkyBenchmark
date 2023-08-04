package dev.thatredox.chunky.benchmark;

import com.google.gson.Gson;
import dev.thatredox.chunky.benchmark.schema.Config;
import dev.thatredox.chunky.benchmark.schema.RunResult;
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
    private final static Gson GSON = new Gson();

    public static class Slf4jLogReceiver extends Receiver {
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
        PrintStream out = System.out;
        System.setOut(new PrintStream(new NullOutputStream()));

        if (args.length != 1) {
            System.err.println("Usage: java -jar ChunkyBenchmark.jar <config json>");
            System.exit(128);
            return;
        }
        Config config = GSON.fromJson(args[0], Config.class);
        LOGGER.debug("Loaded config: {}", config);

        se.llbit.log.Log.setReceiver(new Slf4jLogReceiver(), Level.ERROR, Level.WARNING, Level.INFO);

        ChunkyOptions chunkyOptions = ChunkyOptions.getDefaults();
        chunkyOptions.renderThreads = config.threads;
        chunkyOptions.sppPerPass = config.samples;

        if (config.textures != null) {
            LOGGER.info("Loading texture pack: {}", config.textures);
            TexturePackLoader.loadTexturePacks(config.textures, false);
        } else {
            Chunky.loadDefaultTextures();
        }

        Chunky chunky = new Chunky(chunkyOptions);

        Field chunkyHeadless = chunky.getClass().getDeclaredField("headless");
        chunkyHeadless.setAccessible(true);
        chunkyHeadless.set(chunky, true);

        chunky.getSceneManager().loadScene(config.scene);
        Scene scene = chunky.getSceneManager().getScene();
        scene.setTargetSpp(config.samples);

        AtomicLong atomicRenderTime = new AtomicLong();
        AtomicInteger atomicSps = new AtomicInteger();

        ArrayList<RunResult> results = new ArrayList<>();

        for (int b = 0; b < config.batches; b++) {
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

            RunResult run = new RunResult(config.samples * (b + 1), config.samples, atomicSps.get(), atomicRenderTime.get());
            LOGGER.debug("{}", run);
            results.add(run);
        }

        out.println(GSON.toJson(results));

        if (config.save != null) {
            scene.saveSnapshot(new File(config.save), TaskTracker.NONE, config.threads);
        }

        System.exit(0);
    }
}
