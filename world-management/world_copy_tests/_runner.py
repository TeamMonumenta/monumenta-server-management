"""Shared orchestration for the world-copy end-to-end test entrypoints.

Two entrypoints drive these fixtures end-to-end:
  - run_python_test.py: exercises the Python reference copier (copy_world.py).
  - run_java_test.py:    exercises the Java WorldCopier (throwaway Paper server).
Both reuse the generate -> copy -> validate flow assembled here.

Interpreter: every child process is launched with sys.executable, so the whole
flow runs under whichever interpreter started the entrypoint. That interpreter
must be the pypy3 venv described in README.md "Environment setup" - copy_world.py
is shebanged `#!/usr/bin/env pypy3` and the automation libs need the venv's
dependencies, so its shebang is deliberately bypassed rather than relied on.
"""
import os
import shutil
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
AUTOMATION = os.path.normpath(os.path.join(HERE, "..", "..", "monumenta-automation", "utility_code"))
QUARRY = os.path.normpath(os.path.join(HERE, "..", "..", "monumenta-automation", "quarry"))
# quarry.types.nbt imports `brigadier.string_reader`, which lives in quarry's own
# "brigadier.py" submodule directory (a directory, not a file).
BRIGADIER = os.path.join(QUARRY, "brigadier.py")
INPUTS = os.path.join(HERE, "inputs")
OUTPUTS = os.path.join(HERE, "outputs")
COPY_WORLD = os.path.join(AUTOMATION, "copy_world.py")


def check_submodules() -> None:
    """Fail early with a clear message when the automation submodules are missing.

    Everything here needs monumenta-automation, its nested quarry submodule, and
    quarry's own nested brigadier.py submodule checked out. See README.md
    "Environment setup".
    """
    for path in (AUTOMATION, QUARRY, BRIGADIER):
        if not os.path.isdir(path) or not os.listdir(path):
            raise SystemExit(
                f"Missing or empty dependency: {path}\n"
                "Run: git submodule update --init --recursive\n"
                "See README.md \"Environment setup\"."
            )


def automation_env() -> dict[str, str]:
    """Environment with the monumenta-automation libs on PYTHONPATH.

    generate.py and validate.py self-insert these paths, but copy_world.py does
    not, so anything shelling out to the automation libs goes through here.
    """
    env = os.environ.copy()
    extra = os.pathsep.join([AUTOMATION, QUARRY, BRIGADIER])
    existing = env.get("PYTHONPATH")
    env["PYTHONPATH"] = extra + os.pathsep + existing if existing else extra
    return env


def run(cmd: list[str]) -> None:
    print("+ " + " ".join(cmd), flush=True)
    subprocess.run(cmd, check=True, env=automation_env())


def generate_inputs() -> None:
    check_submodules()
    run([sys.executable, os.path.join(HERE, "generate.py"), INPUTS])


def clean_outputs() -> None:
    if os.path.isdir(OUTPUTS):
        shutil.rmtree(OUTPUTS)
    os.makedirs(OUTPUTS, exist_ok=True)


def validate(python_reference: bool = False) -> None:
    """Validate outputs against inputs.

    python_reference relaxes the assertions the Python reference copier is known
    not to satisfy (see validate.py PYTHON_REFERENCE); the Java stage stays strict.
    """
    cmd = [sys.executable, os.path.join(HERE, "validate.py")]
    if python_reference:
        cmd.append("--python-reference")
    cmd += [INPUTS, OUTPUTS]
    run(cmd)


def fixture_names() -> list[str]:
    return sorted(
        name for name in os.listdir(INPUTS)
        if os.path.isdir(os.path.join(INPUTS, name))
    )
