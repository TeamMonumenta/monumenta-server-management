#!/usr/bin/env python3
"""End-to-end test of the Java WorldCopier.

Builds the required plugin jars, runs the fixtures through WorldCopier inside a throwaway native
Paper server (started with MONUMENTA_WORLD_COPY_TEST set, which makes WorldCopyTestHarness copy
every fixture in onLoad and halt the JVM), then validates the outputs with validate.py.

No container runtime is involved: the server jars are downloaded once into .paper-cache/ and each
run assembles a disposable server directory under the system temp dir (see _server.py). See
DOCKER.md for the Docker-based arrangement this replaced.

run_load_test.py reuses the copy stage here, then loads the results with a real world loader.
"""
import argparse
import glob
import os
import shutil
import subprocess
import sys

import _runner
import _server

REPO_ROOT = os.path.normpath(os.path.join(_runner.HERE, "..", ".."))

COMMANDAPI_VERSION = "9.4.1"
NBTAPI_VERSION = "2.15.2"
DEPENDENCY_JARS = {
    "CommandAPI.jar": (
        "https://repo1.maven.org/maven2/dev/jorel/commandapi-bukkit-plugin/"
        f"{COMMANDAPI_VERSION}/commandapi-bukkit-plugin-{COMMANDAPI_VERSION}.jar"
    ),
    "NBTAPI.jar": (
        "https://repo.codemc.io/repository/maven-public/de/tr7zw/item-nbt-api-plugin/"
        f"{NBTAPI_VERSION}/item-nbt-api-plugin-{NBTAPI_VERSION}.jar"
    ),
}

# Plugin jars Paper needs to load world-management, mapped to a glob (relative to the repo root)
# for the built artifact. These are the shadowJar outputs ("-all.jar"): world-management's version
# adapter (adapter_api plus the reobfuscated adapter_v1_20_R3) is only present in the shaded jar,
# and MonumentaRedisSync likewise only carries its Lettuce dependency there.
# Hard-depend chain: world-management -> MonumentaRedisSync -> MonumentaNetworkRelay -> MonumentaCommon.
PLUGIN_JARS = {
    "MonumentaWorldManagement.jar": "world-management/build/libs/MonumentaWorldManagement-*-all.jar",
    "MonumentaCommon.jar": "monumenta-common/build/libs/MonumentaCommon-*-all.jar",
    "MonumentaNetworkRelay.jar": "network-relay/build/libs/MonumentaNetworkRelay-*-all.jar",
    "MonumentaRedisSync.jar": "redis-sync/plugin/build/libs/MonumentaRedisSync-*-all.jar",
}

GRADLE_TASKS = [
    ":world-management:shadowJar",
    ":monumenta-common:shadowJar",
    ":network-relay:shadowJar",
    ":redis-sync:redissync:shadowJar",
]

# The harness calls System.exit from onLoad, so the server normally exits within seconds. This is
# only a backstop against a hang (a stuck copy, or a plugin that reaches the world-load stage).
SERVER_TIMEOUT_SECONDS = 300


def build_jars() -> None:
    cmd = [os.path.join(REPO_ROOT, "gradlew")] + GRADLE_TASKS
    print("+ " + " ".join(cmd), flush=True)
    subprocess.run(cmd, check=True, cwd=REPO_ROOT, env=_runner.automation_env())


def stage_jars() -> dict[str, str]:
    """Resolve each required plugin jar to a concrete path (newest match)."""
    staged: dict[str, str] = {}
    for plugin_name, pattern in PLUGIN_JARS.items():
        matches = glob.glob(os.path.join(REPO_ROOT, pattern))
        if not matches:
            raise SystemExit(
                f"run_java_test.py: no jar matching '{pattern}' under {REPO_ROOT}.\n"
                "Build the jars first (omit --no-build)."
            )
        staged[plugin_name] = max(matches, key=os.path.getmtime)
    return staged


def copy_fixtures(no_build: bool, refresh: bool, keep: bool, verbose: bool) -> int:
    """Run the copy stage: build the jars, then copy every fixture with the Java WorldCopier.

    Leaves the results in _runner.OUTPUTS and returns the server's exit code. Callers are
    expected to have already run _runner.generate_inputs() and _runner.clean_outputs().
    """
    if not no_build:
        build_jars()
    plugin_jars = _server.ensure_jars(DEPENDENCY_JARS, refresh)
    plugin_jars.update(stage_jars())
    paper_jar = _server.ensure_paper(refresh)

    server_dir = _server.build_server_dir(paper_jar, plugin_jars, prefix="world-copy-test-")
    env = {
        "MONUMENTA_WORLD_COPY_TEST": "1",
        "MONUMENTA_WORLD_COPY_TEST_INPUTS": _runner.INPUTS,
        "MONUMENTA_WORLD_COPY_TEST_OUTPUTS": _runner.OUTPUTS,
    }
    if verbose:
        # Raises the WorldManagement log level so WorldCopier's per-chunk MMLog.trace output prints.
        env["MONUMENTA_WORLD_COPY_TEST_LOG_LEVEL"] = "TRACE"

    code = 1
    try:
        code, _ = _server.run_server(
            server_dir, env, SERVER_TIMEOUT_SECONDS, "run_java_test.py")
    finally:
        # Keep the directory on failure so its server.log and leftover state can be inspected.
        if keep or code != 0:
            print(f"\nServer directory kept at {server_dir} (log: server.log)", flush=True)
        else:
            shutil.rmtree(server_dir, ignore_errors=True)
    print(f"\nJava copy server exited with code {code}.", flush=True)
    return code


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--no-build", action="store_true", help="skip the gradle jar build")
    parser.add_argument("--refresh", action="store_true",
                        help="re-download the cached Paper / CommandAPI / NBT-API jars")
    parser.add_argument("--keep", action="store_true",
                        help="keep the throwaway server directory even when the run succeeds")
    parser.add_argument("--verbose", action="store_true",
                        help="raise the WorldManagement log level to TRACE")
    args = parser.parse_args()

    _runner.check_submodules()
    _runner.generate_inputs()
    _runner.clean_outputs()
    copy_fixtures(args.no_build, args.refresh, args.keep, args.verbose)

    # Surface validate.py's per-fixture report without a Python traceback on server failure.
    validate_cmd = [
        sys.executable, os.path.join(_runner.HERE, "validate.py"), _runner.INPUTS, _runner.OUTPUTS
    ]
    print("+ " + " ".join(validate_cmd), flush=True)
    raise SystemExit(subprocess.run(validate_cmd, check=False, env=_runner.automation_env()).returncode)


if __name__ == "__main__":
    main()
