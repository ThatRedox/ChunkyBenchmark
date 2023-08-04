from typing import Optional

import pydantic


class RunResult(pydantic.BaseModel):
    totalSamples: int
    runSamples: int
    samplesPerSecond: int
    renderTime: int

    def __str__(self) -> str:
        return repr(self)


class Config(pydantic.BaseModel):
    textures: Optional[str]
    scene: str
    save: Optional[str]
    threads: int
    samples: int
    batches: int


class PythonConfig(pydantic.BaseModel):
    jvm: str
    chunky: str
    runner: str
    textures: Optional[str] = None
    scene: str
    save: Optional[str] = None
    threads: int
    samples: int
    batches: int

    def java_config(self) -> Config:
        return Config(textures=self.textures, scene=self.scene, threads=self.threads,
                      save=self.save, samples=self.samples, batches=self.batches)
