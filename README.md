# Chunky Benchmark

## How to run
1. Build the jar
2. Run the jar with `--input "esspooltest" --threads 8 --libs "libs/" --self "build/libs/ChunkyBenchmark.jar" -o "test.csv" --runs 100 --mix`
where input is the input scene, libs is the path to the chunky core libraries to benchmark, self points at the current jar, or can be left out, o is the output file, runs specifies the number of runs, mix specifies if the run order should be randomized.
3. Wait ⌛
4. Analyze the results.

## Licensing Stuff
This project is Copyright 2022, @ThatRedox.

Permission to modify and redistribute is granted under the terms of the GPLv3 license. See the file LICENSE for the full license.

This repository bundles `chunky-core.jar` from [Chunky](https://github.com/chunky-dev/chunky) for benchmark reference purposes. Chunky is licensed under GPLv3.
