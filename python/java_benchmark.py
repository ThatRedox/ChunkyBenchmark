from typing import *
import subprocess
import logging
import json
import os

import schema


class BenchmarkTool:
    def __init__(self, tool_path: str):
        assert tool_path.endswith(".jar")
        self.tool_path = tool_path

    def run(self, jvm: str, chunky: str, config: schema.Config) -> List[schema.RunResult]:
        logger = logging.getLogger("Benchmark Tool")
        cmd = [
            jvm,
            "-cp", f"{self.tool_path}{os.pathsep}{chunky}",
            "dev.thatredox.chunky.benchmark.Benchmark",
            config.model_dump_json()
        ]

        logger.info("Running: %s", cmd)
        result = subprocess.run(cmd, capture_output=True)
        out = result.stdout.decode("utf-8", "ignore")
        out = json.loads(out)
        out = [schema.RunResult(**data) for data in out]
        logger.info("Result: %s", out)

        return out
