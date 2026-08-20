"""Shared orchestration for the world-copy end-to-end test entrypoints.

Two entrypoints drive these fixtures end-to-end:
  - run_python_test.py: exercises the Python reference copier (copy_world.py).
  - run_java_test.py:    exercises the Java WorldCopier (Dockerized Paper server).
Both reuse the generate -> copy -> validate flow assembled here.
"""
import os
import shutil
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
AUTOMATION = os.path.normpath(os.path.join(HERE, "..", "..", "monumenta-automation", "utility_code"))
QUARRY = os.path.normpath(os.path.join(HERE, "..", "..", "monumenta-automation", "quarry"))
INPUTS = os.path.join(HERE, "inputs")
OUTPUTS = os.path.join(HERE, "outputs")
COPY_WORLD = os.path.join(AUTOMATION, "copy_world.py")


def automation_env() -> dict[str, str]:
    """Environment with the monumenta-automation libs on PYTHONPATH.

    generate.py and validate.py self-insert these paths, but copy_world.py does
    not, so anything shelling out to the automation libs goes through here.
    """
    env = os.environ.copy()
    extra = os.pathsep.join([AUTOMATION, QUARRY])
    existing = env.get("PYTHONPATH")
    env["PYTHONPATH"] = extra + os.pathsep + existing if existing else extra
    return env


def run(cmd: list[str]) -> None:
    print("+ " + " ".join(cmd), flush=True)
    subprocess.run(cmd, check=True, env=automation_env())


def generate_inputs() -> None:
    run([sys.executable, os.path.join(HERE, "generate.py"), INPUTS])


def clean_outputs() -> None:
    if os.path.isdir(OUTPUTS):
        shutil.rmtree(OUTPUTS)
    os.makedirs(OUTPUTS, exist_ok=True)


def validate() -> None:
    run([sys.executable, os.path.join(HERE, "validate.py"), INPUTS, OUTPUTS])


def fixture_names() -> list[str]:
    return sorted(
        name for name in os.listdir(INPUTS)
        if os.path.isdir(os.path.join(INPUTS, name))
    )
