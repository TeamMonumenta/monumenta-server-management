"""Shared throwaway-Paper-server plumbing for the world-copy end-to-end entrypoints.

run_java_test.py (the copy stage) and run_load_test.py (the load stage) both need the same
things: a cached Paper jar, a cache for the other jars they download, a disposable server
directory, and a way to run the server unattended with a watchdog. All of that lives here so
the two entrypoints only carry what is actually different between them.

Nothing here is fixture-aware; see _runner.py for the generate -> copy -> validate flow.
"""
import hashlib
import http.client
import json
import os
import shutil
import subprocess
import sys
import tempfile
import threading
import urllib.request
import xml.etree.ElementTree as ElementTree

import _runner

CACHE_DIR = os.path.join(_runner.HERE, ".paper-cache")
SERVER_FILES = os.path.join(_runner.HERE, "server_files")

# Pinned server dependencies. The Paper jar is resolved through the PaperMC v3 API so the readable
# version/build pin below stays the source of truth; the sha256 is verified after every download.
PAPER_VERSION = "1.20.4"
PAPER_BUILD = 499
PAPER_SHA256 = "cabed3ae77cf55deba7c7d8722bc9cfd5e991201c211665f9265616d9fe5c77b"
PAPER_BUILDS_API = f"https://fill.papermc.io/v3/projects/paper/versions/{PAPER_VERSION}/builds"
# Direct object URL for the pinned build, used when the lookup API is unreachable.
PAPER_FALLBACK_URL = (
    f"https://fill-data.papermc.io/v1/objects/{PAPER_SHA256}/paper-{PAPER_VERSION}-{PAPER_BUILD}.jar"
)

# paper-api, the compile-time API jar. Only run_load_test.py needs it (to javac the verifier
# plugin). It is a Maven snapshot, so the concrete filename has to be read out of the
# directory's maven-metadata.xml rather than pinned by hand.
PAPER_API_VERSION = f"{PAPER_VERSION}-R0.1-SNAPSHOT"
PAPER_API_DIR = (
    "https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/"
    f"{PAPER_API_VERSION}/"
)

# javac has to resolve paper-api's whole supertype closure, which reaches outside paper-api:
# Server implements adventure's ForwardingAudience, World implements adventure's Keyed, and
# adventure's own types implement Examinable. Versions come from paper-api's pom (adventure-bom).
ADVENTURE_VERSION = "4.16.0"
EXAMINATION_VERSION = "1.3.0"
MAVEN_CENTRAL = "https://repo1.maven.org/maven2"
PAPER_API_DEPENDENCY_JARS = {
    f"adventure-api-{ADVENTURE_VERSION}.jar":
        f"{MAVEN_CENTRAL}/net/kyori/adventure-api/{ADVENTURE_VERSION}/adventure-api-{ADVENTURE_VERSION}.jar",
    f"adventure-key-{ADVENTURE_VERSION}.jar":
        f"{MAVEN_CENTRAL}/net/kyori/adventure-key/{ADVENTURE_VERSION}/adventure-key-{ADVENTURE_VERSION}.jar",
    f"examination-api-{EXAMINATION_VERSION}.jar":
        f"{MAVEN_CENTRAL}/net/kyori/examination-api/{EXAMINATION_VERSION}/"
        f"examination-api-{EXAMINATION_VERSION}.jar",
}

# Both the PaperMC CDN and repo.codemc.io reject requests carrying urllib's default User-Agent.
USER_AGENT = "monumenta-world-copy-test/1.0"


def cache_dir() -> str:
    """Create the download cache, keeping it out of git without touching the shared .gitignore."""
    os.makedirs(CACHE_DIR, exist_ok=True)
    marker = os.path.join(CACHE_DIR, ".gitignore")
    if not os.path.exists(marker):
        with open(marker, "w", encoding="utf-8") as handle:
            handle.write("*\n")
    return CACHE_DIR


def sha256_of(path: str) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for block in iter(lambda: handle.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def http_get(url: str, timeout: int) -> http.client.HTTPResponse:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    return urllib.request.urlopen(request, timeout=timeout)


def download(url: str, dest: str) -> None:
    print(f"+ download {url} -> {dest}", flush=True)
    tmp = dest + ".part"
    with http_get(url, timeout=120) as response, open(tmp, "wb") as handle:
        shutil.copyfileobj(response, handle)
    os.replace(tmp, dest)


def paper_download_url() -> str:
    """Look up the pinned build's object URL, falling back to the direct URL if the API is down."""
    try:
        with http_get(PAPER_BUILDS_API, timeout=60) as response:
            builds = json.load(response)
    except (OSError, ValueError) as ex:
        print(f"  (PaperMC build API unavailable: {ex}; using the pinned object URL)", flush=True)
        return PAPER_FALLBACK_URL
    for build in builds:
        if build.get("id") == PAPER_BUILD:
            download_info = build.get("downloads", {}).get("server:default", {})
            url = download_info.get("url")
            if url:
                return url
    print(f"  (build {PAPER_BUILD} not listed by the API; using the pinned object URL)", flush=True)
    return PAPER_FALLBACK_URL


def ensure_paper(refresh: bool) -> str:
    """Return the cached Paper jar path, downloading it if missing, stale, or corrupt."""
    dest = os.path.join(cache_dir(), f"paper-{PAPER_VERSION}-{PAPER_BUILD}.jar")
    if refresh and os.path.exists(dest):
        os.remove(dest)
    if os.path.exists(dest):
        actual = sha256_of(dest)
        if actual == PAPER_SHA256:
            return dest
        print(f"  (cached {os.path.basename(dest)} has sha256 {actual}; re-downloading)", flush=True)
    download(paper_download_url(), dest)
    actual = sha256_of(dest)
    if actual != PAPER_SHA256:
        raise SystemExit(
            f"_server.py: Paper jar sha256 mismatch.\n"
            f"  expected {PAPER_SHA256}\n  actual   {actual}"
        )
    return dest


def ensure_jars(jars: dict[str, str], refresh: bool) -> dict[str, str]:
    """Return cached copies of {filename: url}, downloading any that are missing."""
    cached: dict[str, str] = {}
    for name, url in jars.items():
        dest = os.path.join(cache_dir(), name)
        if refresh and os.path.exists(dest):
            os.remove(dest)
        if not os.path.exists(dest):
            download(url, dest)
        cached[name] = dest
    return cached


def _paper_api_snapshot_version() -> str:
    """Resolve the 1.20.4-R0.1-SNAPSHOT alias to the concrete timestamped snapshot version."""
    with http_get(PAPER_API_DIR + "maven-metadata.xml", timeout=60) as response:
        root = ElementTree.parse(response).getroot()
    for entry in root.iterfind("./versioning/snapshotVersions/snapshotVersion"):
        if entry.findtext("extension") == "jar" and entry.find("classifier") is None:
            value = entry.findtext("value")
            if value:
                return value
    raise SystemExit(f"_server.py: no plain jar snapshot listed at {PAPER_API_DIR}maven-metadata.xml")


def ensure_paper_api_classpath(refresh: bool) -> str:
    """Return the javac classpath for compiling a plugin against paper-api."""
    dest = os.path.join(cache_dir(), f"paper-api-{PAPER_API_VERSION}.jar")
    if refresh and os.path.exists(dest):
        os.remove(dest)
    if not os.path.exists(dest):
        version = _paper_api_snapshot_version()
        download(f"{PAPER_API_DIR}paper-api-{version}.jar", dest)
    jars = [dest] + list(ensure_jars(PAPER_API_DEPENDENCY_JARS, refresh).values())
    return os.pathsep.join(jars)


def build_server_dir(paper_jar: str, plugin_jars: dict[str, str], prefix: str,
                     extra_properties: str = "") -> str:
    """Assemble a disposable Paper server directory and return its path."""
    server_dir = tempfile.mkdtemp(prefix=prefix)
    plugins_dir = os.path.join(server_dir, "plugins")
    os.makedirs(plugins_dir)
    os.symlink(paper_jar, os.path.join(server_dir, "paper.jar"))
    with open(os.path.join(server_dir, "eula.txt"), "w", encoding="utf-8") as handle:
        handle.write("eula=true\n")
    shutil.copyfile(os.path.join(SERVER_FILES, "log4j2.xml"), os.path.join(server_dir, "log4j2.xml"))
    with open(os.path.join(SERVER_FILES, "server.properties"), encoding="utf-8") as handle:
        properties = handle.read()
    with open(os.path.join(server_dir, "server.properties"), "w", encoding="utf-8") as handle:
        handle.write(properties + extra_properties)
    for name, source in plugin_jars.items():
        os.symlink(source, os.path.join(plugins_dir, name))
    return server_dir


def run_server(server_dir: str, env_extra: dict[str, str], timeout_seconds: int,
               label: str, heap: str = "512M") -> tuple[int, list[str]]:
    """Run Paper unattended, streaming its log to stdout and <server_dir>/server.log.

    Returns (exit code, captured lines). Exit code 124 means the watchdog killed the server.
    """
    env = os.environ.copy()
    env.update(env_extra)
    # log4j2.xml drops Paper's sub-INFO console threshold so a runtime log level bump surfaces.
    cmd = [
        shutil.which("java") or "java",
        "-Dlog4j2.configurationFile=log4j2.xml",
        f"-Xmx{heap}",
        "-jar", "paper.jar",
        "nogui",
    ]
    print("+ " + " ".join(cmd) + f"  (cwd {server_dir})", flush=True)

    lines: list[str] = []
    with subprocess.Popen(
        cmd, cwd=server_dir, env=env, stdin=subprocess.DEVNULL,
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1,
    ) as proc:
        timed_out = threading.Event()

        def on_timeout() -> None:
            timed_out.set()
            proc.kill()

        watchdog = threading.Timer(timeout_seconds, on_timeout)
        watchdog.start()
        try:
            with open(os.path.join(server_dir, "server.log"), "w", encoding="utf-8") as log:
                assert proc.stdout is not None
                for line in proc.stdout:
                    sys.stdout.write(line)
                    sys.stdout.flush()
                    log.write(line)
                    lines.append(line.rstrip("\n"))
            code = proc.wait()
        finally:
            watchdog.cancel()

    if timed_out.is_set():
        print(
            f"\n{label}: the Paper server did not exit within {timeout_seconds}s and was killed.",
            file=sys.stderr, flush=True,
        )
        return 124, lines
    return code, lines
