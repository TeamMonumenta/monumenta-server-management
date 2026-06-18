#!/bin/sh
# Starts Paper. The world-management onLoad hook copies the fixtures and calls System.exit, so this
# process exits on its own with the harness's status code; no manual stop/chown is needed.
set -e
# log4j2.xml (bundled in the image, WORKDIR=/server) drops Paper's sub-INFO console threshold so the
# --verbose runtime log-level bump surfaces WorldCopier's MMLog.trace output.
exec java -Dlog4j2.configurationFile=log4j2.xml -Xmx512M -jar paper.jar nogui
