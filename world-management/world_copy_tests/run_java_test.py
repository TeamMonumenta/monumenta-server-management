#!/usr/bin/env python3
"""End-to-end test of the Java WorldCopier.

Builds the required plugin jars, runs the fixtures through WorldCopier inside a throwaway Docker
container (Paper server with MONUMENTA_WORLD_COPY_TEST set, which triggers WorldCopyTestHarness
to copy and exit), then validates outputs with validate.py.
"""
import argparse
import glob
import os
import subprocess
import sys

import _runner

REPO_ROOT = os.path.normpath(os.path.join(_runner.HERE, "..", ".."))
DOCKER_DIR = os.path.join(_runner.HERE, "docker")
IMAGE_TAG = "monumenta-world-copy-test"

# Plugin jars Paper needs to load world-management. Each entry maps the container filename to a glob
# (relative to repo root) for the built jar. CommandAPI and NBT-API are baked into the image; these
# Monumenta jars are bind-mounted at run time.
# Hard-depend chain: world-management -> MonumentaRedisSync -> MonumentaNetworkRelay -> MonumentaCommon.
PLUGIN_JARS = {
    "MonumentaWorldManagement.jar": "world-management/build/libs/MonumentaWorldManagement-*.jar",
    "MonumentaCommon.jar": "monumenta-common/build/libs/MonumentaCommon-*.jar",
    "MonumentaNetworkRelay.jar": "network-relay/build/libs/MonumentaNetworkRelay-*.jar",
    "MonumentaRedisSync.jar": "redis-sync/plugin/build/libs/MonumentaRedisSync-*.jar",
}

GRADLE_TASKS = [
    ":world-management:assemble",
    ":monumenta-common:assemble",
    ":network-relay:assemble",
    ":redis-sync:redissync:assemble",
]


def build_jars() -> None:
    cmd = [os.path.join(REPO_ROOT, "gradlew")] + GRADLE_TASKS
    print("+ " + " ".join(cmd), flush=True)
    subprocess.run(cmd, check=True, cwd=REPO_ROOT, env=_runner.automation_env())


def stage_jars() -> dict[str, str]:
    """Resolve each required plugin jar to a concrete path (newest match, no sources/javadoc)."""
    staged: dict[str, str] = {}
    for container_name, pattern in PLUGIN_JARS.items():
        matches = [
            p for p in glob.glob(os.path.join(REPO_ROOT, pattern))
            if not p.endswith(("-sources.jar", "-javadoc.jar"))
        ]
        if not matches:
            raise SystemExit(
                f"run_java_test.py: no jar matching '{pattern}' under {REPO_ROOT}.\n"
                "Build the jars first (omit --no-build)."
            )
        staged[container_name] = max(matches, key=os.path.getmtime)
    return staged


def ensure_image(rebuild: bool) -> None:
    exists = subprocess.run(
        ["docker", "images", "-q", IMAGE_TAG],
        check=True, capture_output=True, text=True,
    ).stdout.strip()
    if exists and not rebuild:
        return
    # Bake the host uid/gid in so files written to the mounted outputs/ are owned by the host user.
    _runner.run([
        "docker", "build",
        "--build-arg", f"UID={os.getuid()}",
        "--build-arg", f"GID={os.getgid()}",
        "-t", IMAGE_TAG, DOCKER_DIR,
    ])


def run_container(staged: dict[str, str], verbose: bool) -> int:
    cmd = [
        "docker", "run", "--rm",
        "-e", "MONUMENTA_WORLD_COPY_TEST=1",
        "-e", "MONUMENTA_WORLD_COPY_TEST_INPUTS=/work/inputs",
        "-e", "MONUMENTA_WORLD_COPY_TEST_OUTPUTS=/work/outputs",
        "-v", f"{_runner.INPUTS}:/work/inputs:ro",
        "-v", f"{_runner.OUTPUTS}:/work/outputs",
    ]
    if verbose:
        # Raises the WorldManagement log level so WorldCopier's per-chunk MMLog.trace output prints.
        cmd += ["-e", "MONUMENTA_WORLD_COPY_TEST_LOG_LEVEL=TRACE"]
    for container_name, host_path in staged.items():
        cmd += ["-v", f"{host_path}:/server/plugins/{container_name}:ro"]
    cmd.append(IMAGE_TAG)
    print("+ " + " ".join(cmd), flush=True)
    # Don't abort on container failure; validate.py reports per-fixture results.
    return subprocess.run(cmd, check=False).returncode


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--no-build", action="store_true", help="skip the gradle jar build")
    parser.add_argument("--rebuild", action="store_true", help="rebuild the Docker image")
    parser.add_argument("--verbose", action="store_true",
                        help="raise the WorldManagement log level to TRACE in the container")
    args = parser.parse_args()

    _runner.generate_inputs()
    _runner.clean_outputs()
    if not args.no_build:
        build_jars()
    staged = stage_jars()
    ensure_image(args.rebuild)
    code = run_container(staged, args.verbose)
    print(f"\nJava copy container exited with code {code}.", flush=True)
    # Surface validate.py's per-fixture report without a Python traceback on container failure.
    validate_cmd = [sys.executable, os.path.join(_runner.HERE, "validate.py"), _runner.INPUTS, _runner.OUTPUTS]
    print("+ " + " ".join(validate_cmd), flush=True)
    raise SystemExit(subprocess.run(validate_cmd, check=False, env=_runner.automation_env()).returncode)


if __name__ == "__main__":
    main()
