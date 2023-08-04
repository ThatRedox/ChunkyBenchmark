import logging
import os

import yaml

from java_benchmark import BenchmarkTool
import schema


def main():
    # Output folder
    os.makedirs("out/", exist_ok=True)

    # Setup logging
    logging.getLogger().setLevel(logging.DEBUG)
    logging.getLogger().addHandler(logging.FileHandler("out/py_benchmark.log"))

    # Load the config
    with open("config.yaml", "r") as config:
        config = yaml.safe_load(config)
        config = schema.PythonConfig(**config)

    # Create the runner
    benchmarker = BenchmarkTool(config.runner)

    results = benchmarker.run(config.jvm, config.chunky, config.java_config())
    print("|Total Samples|Run Samples|Samples Per Second|Render Time (ms)|")
    print("|-------------|-----------|------------------|----------------|")
    for result in results:
        print(f"|{result.totalSamples}|{result.runSamples}|"
              f"{result.samplesPerSecond}|{result.renderTime}|")


if __name__ == '__main__':
    main()
