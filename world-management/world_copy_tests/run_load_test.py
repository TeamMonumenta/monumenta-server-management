#!/usr/bin/env python3
"""End-to-end load test of the Java WorldCopier's output.

validate.py checks the copier's output by re-reading it with quarry and asserting NBT-level
properties. That is circular: it only proves the output matches this project's own idea of
correct. This test instead hands both the input and the output world to Minecraft's own world
loader and chunk deserializer, and asserts they deserialize to the same thing.

Flow:
  1. generate the fixtures and copy them with the real Java WorldCopier (run_java_test.py's stage)
  2. copy each input world and output world into a throwaway server dir as in_<f> / out_<f>
     (copies, not symlinks: loading a world mutates it - session.lock, level.dat, poi/)
  3. run one Paper server carrying only the verifier plugin (verifier/) over all of them
  4. assert in_<f> and out_<f> report identical chunk / entity / block entity counts, with no
     chunk failing to deserialize

Only fixtures a real server can load at all are meaningful here; see UNLOADABLE_FIXTURES.
"""
import argparse
import os
import re
import shutil
import subprocess
import sys

import _runner
import _server
import run_java_test

VERIFIER_DIR = os.path.join(_runner.HERE, "verifier")
VERIFIER_SOURCES = os.path.join(VERIFIER_DIR, "src")
VERIFIER_RESOURCES = os.path.join(VERIFIER_DIR, "resources")

# Loading and deserializing every chunk of a real world takes a while; this is only a backstop
# against a genuine hang, since the verifier halts the JVM as soon as it is done.
SERVER_TIMEOUT_SECONDS = 900

# Fixtures W2-W8 are synthetic stubs assembled by generate.py, not worlds a Minecraft server ever
# wrote, and a real server cannot open them: their level.dat carries only DataVersion / LevelName /
# Spawn* / Version, and CraftServer.createWorld dies on the missing WorldGenSettings with
# "No key dimensions in MapLike[{}]; No key seed in MapLike[{}]" before a single chunk is read.
#
# Handing them a borrowed real level.dat does not rescue the test either: their chunks have no
# block sections, so vanilla drops every block entity ("Skipping BlockEntity with id
# minecraft:spawner") and W4/W5/W6 report blockEntities=0 - the very content they exist to cover.
# W3 has no region/ at all, so there is nothing to enumerate. The assertions would be 0 == 0.
#
# So this is a fixture-realism limitation, not a copier defect: validate.py covers W2-W8 at the NBT
# level, and this test covers the fixture that is a genuine world. Do not add a fixture here to
# silence a real load failure. Re-check the assumption with --all-fixtures.
UNLOADABLE_FIXTURES = {
    "02_baseline", "03_entities_basic", "04_block_entities", "05_item_recursion",
    "06_compression_variants", "07_external_mcc", "08_scores_world_uuid",
}
UNLOADABLE_REASON = (
    "synthetic fixture; its stub level.dat has no WorldGenSettings, so a real server "
    "cannot open the world at all"
)

# Extra server.properties for the load stage. level-name points at a scratch world so the
# auto-generated default world can never collide with a world under test, and the distances keep
# that throwaway world cheap.
LOAD_PROPERTIES = """
# Appended by run_load_test.py.
level-name=zz_scratch_world
view-distance=2
simulation-distance=2
"""

VERIFY_RE = re.compile(
    r"LOADVERIFY (\S+) chunks=(\d+) entities=(\d+) blockEntities=(\d+)\s*$")
FAIL_RE = re.compile(r"LOADFAIL (\S+) (chunk=\S+ .*)$")
DONE_RE = re.compile(r"LOADVERIFY-DONE\s*$")


def build_verifier(classpath: str) -> str:
    """Compile verifier/ into a jar with plain javac and return its path."""
    build_dir = os.path.join(VERIFIER_DIR, "build")
    classes_dir = os.path.join(build_dir, "classes")
    shutil.rmtree(build_dir, ignore_errors=True)
    os.makedirs(classes_dir)
    sources = [
        os.path.join(root, name)
        for root, _, names in os.walk(VERIFIER_SOURCES)
        for name in names if name.endswith(".java")
    ]
    javac = [
        shutil.which("javac") or "javac", "-Xlint:all,-classfile", "-classpath", classpath,
        "-d", classes_dir,
    ] + sorted(sources)
    print("+ " + " ".join(javac), flush=True)
    subprocess.run(javac, check=True)
    for name in os.listdir(VERIFIER_RESOURCES):
        shutil.copyfile(os.path.join(VERIFIER_RESOURCES, name), os.path.join(classes_dir, name))
    jar_path = os.path.join(build_dir, "WorldLoadVerifier.jar")
    jar = [shutil.which("jar") or "jar", "--create", "--file", jar_path, "-C", classes_dir, "."]
    print("+ " + " ".join(jar), flush=True)
    subprocess.run(jar, check=True)
    return jar_path


def is_world(path: str) -> bool:
    return os.path.isfile(os.path.join(path, "level.dat"))


def stage_worlds(server_dir: str, fixtures: list[str]) -> list[str]:
    """Copy each fixture's input and output world into the server dir; return the world names.

    Copies rather than symlinks: loading a world rewrites session.lock and level.dat and
    regenerates poi/, which must not touch inputs/ or outputs/.
    """
    names: list[str] = []
    for fixture in fixtures:
        for prefix, source_root in (("in", _runner.INPUTS), ("out", _runner.OUTPUTS)):
            name = f"{prefix}_{fixture}"
            shutil.copytree(os.path.join(source_root, fixture), os.path.join(server_dir, name))
            names.append(name)
    return names


def parse_report(lines: list[str]) -> tuple[dict[str, tuple[int, int, int]], list[str], bool]:
    """Extract (counts by world, failure lines, saw-done) from the captured server output."""
    counts: dict[str, tuple[int, int, int]] = {}
    failures: list[str] = []
    done = False
    for line in lines:
        match = VERIFY_RE.search(line)
        if match:
            counts[match.group(1)] = (int(match.group(2)), int(match.group(3)), int(match.group(4)))
            continue
        match = FAIL_RE.search(line)
        if match:
            failures.append(f"{match.group(1)} {match.group(2)}")
            continue
        if DONE_RE.search(line):
            done = True
    return counts, failures, done


def report(fixtures: list[str], skipped: list[tuple[str, str]],
           counts: dict[str, tuple[int, int, int]], failures: list[str], done: bool) -> int:
    """Print the per-fixture PASS/FAIL/SKIP table and return the process exit code."""
    passed = failed = 0
    results: list[tuple[str, str, str]] = []
    for fixture in fixtures:
        problems = [f for f in failures if f.startswith(f"in_{fixture} ") or f.startswith(f"out_{fixture} ")]
        source = counts.get(f"in_{fixture}")
        copied = counts.get(f"out_{fixture}")
        if problems:
            results.append(("FAIL", fixture, "; ".join(problems)))
        elif source is None or copied is None:
            missing = "in_" if source is None else "out_"
            results.append(("FAIL", fixture, f"no LOADVERIFY line for {missing}{fixture}"))
        elif source != copied:
            results.append((
                "FAIL", fixture,
                f"counts differ: input chunks={source[0]} entities={source[1]} "
                f"blockEntities={source[2]}, output chunks={copied[0]} entities={copied[1]} "
                f"blockEntities={copied[2]}"))
        else:
            results.append((
                "PASS", fixture,
                f"chunks={source[0]} entities={source[1]} blockEntities={source[2]}"))
    for fixture, reason in skipped:
        results.append(("SKIP", fixture, reason))

    # Anything the verifier reported against a world no fixture claims (a bad
    # MONUMENTA_WORLD_LOAD_VERIFY, say) must not vanish from the tally.
    claimed = {f"{prefix}_{fixture}" for fixture in fixtures for prefix in ("in", "out")}
    for failure in failures:
        if failure.split(" ", 1)[0] not in claimed:
            results.append(("FAIL", "(unattributed)", failure))

    for status, fixture, detail in sorted(results, key=lambda row: row[1]):
        print(f"  {status}  {fixture}: {detail}")
        if status == "PASS":
            passed += 1
        elif status == "FAIL":
            failed += 1

    if not done:
        print("  FAIL  the verifier never printed LOADVERIFY-DONE (the server died early)")
        failed += 1
    print(f"\n{passed} passed, {failed} failed, {len(skipped)} skipped")
    return 0 if failed == 0 else 1


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--no-build", action="store_true", help="skip the gradle jar build")
    parser.add_argument("--refresh", action="store_true",
                        help="re-download the cached Paper / paper-api / plugin dependency jars")
    parser.add_argument("--keep", action="store_true",
                        help="keep the throwaway server directories even when the run succeeds")
    parser.add_argument("--verbose", action="store_true",
                        help="raise the WorldManagement log level to TRACE during the copy stage")
    parser.add_argument("--all-fixtures", action="store_true",
                        help="also load the synthetic fixtures normally reported SKIP "
                             "(see UNLOADABLE_FIXTURES), to re-check that assumption")
    args = parser.parse_args()

    _runner.check_submodules()
    _runner.generate_inputs()
    _runner.clean_outputs()
    code = run_java_test.copy_fixtures(args.no_build, args.refresh, args.keep, args.verbose)
    if code != 0:
        raise SystemExit(f"run_load_test.py: the copy stage failed (exit {code}); nothing to load.")

    skipped: list[tuple[str, str]] = []
    fixtures: list[str] = []
    for fixture in _runner.fixture_names():
        if fixture in UNLOADABLE_FIXTURES and not args.all_fixtures:
            skipped.append((fixture, UNLOADABLE_REASON))
        elif not is_world(os.path.join(_runner.INPUTS, fixture)):
            skipped.append((fixture, "fixture input has no level.dat (empty placeholder)"))
        elif not is_world(os.path.join(_runner.OUTPUTS, fixture)):
            skipped.append((fixture, "fixture output has no level.dat (nothing was copied)"))
        else:
            fixtures.append(fixture)

    paper_jar = _server.ensure_paper(args.refresh)
    verifier_jar = build_verifier(_server.ensure_paper_api_classpath(args.refresh))

    server_dir = _server.build_server_dir(
        paper_jar, {"WorldLoadVerifier.jar": verifier_jar},
        prefix="world-load-test-", extra_properties=LOAD_PROPERTIES)
    world_names = stage_worlds(server_dir, fixtures)
    print(f"+ staged {len(world_names)} worlds under {server_dir}", flush=True)

    code, lines = _server.run_server(
        server_dir, {"MONUMENTA_WORLD_LOAD_VERIFY": ",".join(world_names)},
        SERVER_TIMEOUT_SECONDS, "run_load_test.py", heap="2G")
    print(f"\nLoad verification server exited with code {code}.", flush=True)

    counts, failures, done = parse_report(lines)
    exit_code = report(fixtures, skipped, counts, failures, done)

    if args.keep or exit_code != 0:
        print(f"\nServer directory kept at {server_dir} (log: server.log)", flush=True)
    else:
        shutil.rmtree(server_dir, ignore_errors=True)
    sys.exit(exit_code)


if __name__ == "__main__":
    main()
