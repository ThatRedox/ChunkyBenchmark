from typing import *

import collections
import itertools
import logging
import glob
import csv

import java_benchmark


def main():
    # Setup logging
    logging.getLogger().setLevel(logging.DEBUG)
    logging.getLogger().addHandler(logging.FileHandler("py_benchmark.log"))
    logger = logging.getLogger("Benchmark Bootstrap")

    jvms = [
        r"C:\Program Files\Eclipse Foundation\jdk-17.0.0.35-hotspot\bin\java.exe",
        r"C:\Program Files\Java\jdk1.8.0_271\bin\java.exe"
    ]
    libs = glob.glob("../java/libs/*.jar")
    threads = [8]

    scene = "esspooltest"
    samples = 8
    batches = 4
    runs = 2

    tool = java_benchmark.BenchmarkTool("../java/build/libs/ChunkyBenchmark.jar")

    run_combinations: DefaultDict[Tuple[str, str, int], List[java_benchmark.BenchmarkResults]] = \
        collections.defaultdict(list)

    with open("raw_results.csv", "w", newline="") as raw_csv:
        writer = csv.writer(raw_csv)

        for run in range(runs):
            logger.info(f"Running run number {run}")
            for jvm, lib, thread in itertools.product(jvms, libs, threads):
                logger.info(f"Running {batches} batches of {samples} on {jvm}, {lib}, {thread} threads.")
                result = tool.launch(jvms[0], libs[0], scene, thread, samples, batches)
                run_combinations[(jvm, lib, thread)].append(result)
                print("\n")

                writer.writerow([jvm, lib, thread])
                for r in result.runs:
                    writer.writerow([r.total_samples, r.run_samples, r.samples_per_second, r.render_time])

    with open("raw_sps.csv", "w", newline="") as raw_csv:
        writer = csv.writer(raw_csv)
        writer.writerow(["JVM", "Chunky Version", "Threads", "Samples per second"])

        for (jvm, lib, thread), results in run_combinations.items():
            row = [jvm, lib, thread]
            row += [r.median_sps() for r in results]
            writer.writerow(row)

    print(run_combinations)


if __name__ == '__main__':
    main()
