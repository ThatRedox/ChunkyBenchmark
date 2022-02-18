from __future__ import annotations
from typing import *
import subprocess
import tempfile
import logging
import struct
import os

__all__ = ["BenchmarkTool", "BenchmarkResults", "BenchmarkRun"]

BENCHMARK_RUN_PACKED_SIZE = 20


class BenchmarkTool:
    def __init__(self, tool_path: str):
        assert tool_path.endswith(".jar")
        self._tool_path = tool_path

    def launch(self, jvm: str, chunky: str, scene: str, threads: int, samples: int, batches: int, textures: Optional[str] = None) -> BenchmarkResults:
        logger = logging.getLogger("Benchmark Tool")

        with tempfile.TemporaryDirectory() as temp_dir:
            results_path = os.path.join(temp_dir, "results.bin")

            cmd = [
                jvm,
                "-cp", f"{self._tool_path}{os.pathsep}{chunky}",
                "dev.thatredox.chunky.benchmark.Benchmark",
                "--input", scene,
                "--threads", str(threads),
                "--samples", str(samples),
                "--batches", str(batches),
                "--tempOut", results_path
            ]
            if textures is not None:
                cmd.append("--textures")
                cmd.append(textures)

            logger.info(f"Running: {cmd}")
            result = subprocess.run(cmd)
            logger.info(f"Benchmark finished with return code: {result.returncode}")

            with open(results_path, "rb") as results:
                r = BenchmarkResults.from_bytes(results.read())
                logger.info(f"Results: {r}")
                return r


class BenchmarkRun:
    def __init__(self, total_samples: int, run_samples: int, samples_per_second: int, render_time: int):
        self._total_samples = total_samples
        self._run_samples = run_samples
        self._samples_per_second = samples_per_second
        self._render_time = render_time

    @classmethod
    def from_bytes(cls, blob: Union[bytes, bytearray]) -> BenchmarkRun:
        return BenchmarkRun(
            struct.unpack_from(">i", blob, 0)[0],  # (int)  Total samples
            struct.unpack_from(">i", blob, 4)[0],  # (int)  Run samples
            struct.unpack_from(">i", blob, 8)[0],  # (int)  Samples per second
            struct.unpack_from(">q", blob, 12)[0]  # (long) Render time
        )

    @property
    def total_samples(self) -> int:
        return self._total_samples

    @property
    def run_samples(self) -> int:
        return self._run_samples

    @property
    def samples_per_second(self) -> int:
        return self._samples_per_second

    @property
    def render_time(self) -> int:
        return self._render_time

    def __str__(self) -> str:
        return f"Run: {self.total_samples} total samples. {self.run_samples} run samples. " \
               f"{self.samples_per_second} samples per second. {self.render_time} milliseconds."


class BenchmarkResults:
    def __init__(self, runs):
        self._runs = tuple(runs)

    @classmethod
    def from_bytes(cls, blob: Union[bytes, bytearray]) -> BenchmarkResults:
        num_runs = struct.unpack_from(">i", blob, 0)[0]
        runs = []
        for i in range(num_runs):
            offset = 4 + i * BENCHMARK_RUN_PACKED_SIZE
            runs.append(BenchmarkRun.from_bytes(blob[offset: offset + BENCHMARK_RUN_PACKED_SIZE]))
        return BenchmarkResults(runs)

    @property
    def runs(self) -> Tuple[BenchmarkRun, ...]:
        return self._runs

    def median_sps(self) -> int:
        items = [run.samples_per_second for run in self.runs]
        return sorted(items)[len(items)//2]

    def __str__(self) -> str:
        run_strs = [f"\t{str(run)}\n" for run in self.runs]
        return f"{len(self.runs)} runs:\n{''.join(run_strs)}"
