package io.github.thatredox.chunkybenchmark;

import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import org.apache.commons.cli.*;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

public class Main {
    private final static Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public final static Options options = new Options();
    static {
        options.addOption("t", "textures", true, "Path to the texture pack to use.");
        options.addRequiredOption("i", "input", true, "Path to the benchmark scene.");
        options.addOption("o", "output", true, "File to output the benchmark results to.");
        options.addOption(null, "threads", true, "Number of threads to use while rendering.");
        options.addOption("s", "samples", true, "Sample batch size.");
        options.addOption("b", "batches", true, "Number of sample batches.");
        options.addRequiredOption("l", "libs", true, "Path to folder of Chunky libraries to test.");
        options.addOption("r", "runs", true, "Number of runs through all the libraries.");
        options.addOption("m", "mix", false, "Randomize the test order.");
        options.addOption(Option.builder("jvm").hasArgs().build());

        options.addOption(null, "self", true, "Path to own jar.");
        options.addOption(null, "tempOut", true, "Leave empty.");
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;
        boolean help = false;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            help = true;
        }
        if (help || cmd == null || cmd.hasOption("tempOut")) {
            new HelpFormatter().printHelp("java -jar ChunkyBenchmark.jar", options);
            System.exit(128);
            return;
        }
        LOGGER.info("Run with arguments: {}", String.join("\t", args));

        String[] jvms = null;
        if (cmd.hasOption("jvm")) {
            jvms = cmd.getOptionValues("jvm");
            LOGGER.info("JVMs: {}", String.join(",", jvms));
        }
        if (jvms == null) {
            jvms = new String[] {System.getProperty("java.home") + File.separator + "bin" + File.separator + "java"};
            LOGGER.info("JVM determined to be: {}", jvms[0]);
        }

        PrintStream outputStream;
        if (cmd.hasOption("o")) {
            outputStream = new PrintStream(new BufferedOutputStream(new FileOutputStream(cmd.getOptionValue("o"))));
        } else {
            outputStream = System.out;
        }

        try (PrintStream out = outputStream) {

            int runs = 1;
            if (cmd.hasOption("r")) {
                runs = Integer.parseInt(cmd.getOptionValue("r"));
            }

            String self;
            if (cmd.hasOption("self")) {
                self = cmd.getOptionValue("self");
            } else {
                self = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getPath();
            }
            LOGGER.info("Self determined to be: {}", self);

            ArrayList<Pair<String, Benchmark.Results>> resultsArray = new ArrayList<>();
            HashMap<String, ArrayList<Benchmark.Results>> resultsMap = new HashMap<>();

            File libs = new File(cmd.getOptionValue("l"));
            File[] files = libs.listFiles((dir, name) -> name.endsWith(".jar"));
            if (files == null) {
                LOGGER.warn("No libraries found.");
                return;
            }

            String runInfoString = String.format("Testing %d versions %d times each on %s JVM(s)%s.",
                    files.length, runs, jvms.length, cmd.hasOption("m") ? " with mixing" : "");
            LOGGER.info(runInfoString);
            System.out.println(runInfoString);
            for (int i = 0; i < runs; i++) {
                if (cmd.hasOption("m")) {
                    Collections.shuffle(Arrays.asList(files));
                }
                int fCount = 0;
                for (File f : files) {
                    for (String jvm : jvms) {
                        System.out.printf("\nRendering %s \t (%d / %d, %d / %d)\n", f.getName(), ++fCount, files.length * jvms.length, i + 1, runs);

                        File tempOut = File.createTempFile("ChunkyBenchmark", "bin");
                        tempOut.deleteOnExit();

                        ArrayList<String> launchArgs = new ArrayList<>();
                        launchArgs.add(jvm);
                        launchArgs.add("-cp");
                        launchArgs.add(String.format("%s%s%s", self, System.getProperty("path.separator"), f.getPath()));
                        launchArgs.add("io.github.thatredox.chunkybenchmark.Benchmark");
                        launchArgs.addAll(Arrays.asList(args));
                        launchArgs.add("-tempOut");
                        launchArgs.add(tempOut.getAbsolutePath());

                        LOGGER.debug("Running with command: {}", String.join(" ", launchArgs));

                        new ProcessBuilder().command(launchArgs).inheritIO().start().waitFor();

                        String key = f.getName() + " - " + jvm;

                        if (!resultsMap.containsKey(key)) {
                            resultsMap.put(key, new ArrayList<>());
                        }

                        try (DataInputStream tempRead = new DataInputStream(new BufferedInputStream(
                                new FileInputStream(tempOut)))) {
                            Benchmark.Results results = Benchmark.Results.load(tempRead);
                            LOGGER.info("Render {} results: {}", f.getName(), results);

                            resultsArray.add(new ObjectObjectImmutablePair<>(key, results));
                            resultsMap.get(key).add(results);
                        }

                        if (!tempOut.delete()) {
                            LOGGER.error("Failed to delete temporary file: {}", tempOut);
                        }
                    }
                }
            }

            out.print("Library,Mean SPS,STD Dev,95% CI\n");
            resultsMap.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEachOrdered(e -> {
                double[] medians = e.getValue().stream().mapToDouble(Benchmark.Results::getMedianSps).toArray();
                double mean = new Mean().evaluate(medians);
                double standardDeviation = new StandardDeviation(false).evaluate(medians, mean);

                double ci = 1.960 * (standardDeviation / Math.sqrt(e.getValue().size()));

                out.printf("%s,%f,%f,%f,%d +/- %.2f%%\n", e.getKey(),
                        mean, standardDeviation, ci,
                        Math.round(mean), (ci / mean) * 100);
            });
            out.print("\n");

            resultsMap.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEachOrdered(e -> {
                out.printf("%s,", e.getKey());
                e.getValue().forEach(r -> out.printf("%d,", (int) r.getMedianSps()));
                out.print("\n");
            });

            out.print("\n\n,Total Samples,Run Samples,SPS,Time");
            resultsArray.forEach(e -> {
                boolean first = true;
                for (Benchmark.Run run : e.right().runs) {
                    out.printf("\n%s,%d,%d,%d,%d", first ? e.left() : "",
                            run.totalSamples, run.runSamples, run.samplesPerSecond, run.renderTime);
                    first = false;
                }
                out.print("\n");
            });
        }
    }
}
