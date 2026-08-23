# Throwaway Paper server that runs the Java WorldCopier over the test fixtures and exits.
# The world-management plugin's onLoad hook (gated by MONUMENTA_WORLD_COPY_TEST) does the work and
# halts the JVM before any world loads, so this never becomes a real running server.
FROM eclipse-temurin:21-jre

# Dependency versions to download and test against
ARG PAPER_VERSION=1.20.4
ARG PAPER_BUILD=499
ARG COMMANDAPI_VERSION=9.4.1
ARG NBTAPI_VERSION=2.15.2

RUN set -eux; \
	mkdir -p /server/plugins; \
	curl -fSL -o /server/paper.jar \
		"https://api.papermc.io/v2/projects/paper/versions/${PAPER_VERSION}/builds/${PAPER_BUILD}/downloads/paper-${PAPER_VERSION}-${PAPER_BUILD}.jar"; \
	curl -fSL -o /server/plugins/CommandAPI.jar \
		"https://repo1.maven.org/maven2/dev/jorel/commandapi-bukkit-plugin/${COMMANDAPI_VERSION}/commandapi-bukkit-plugin-${COMMANDAPI_VERSION}.jar"; \
	curl -fSL -o /server/plugins/NBTAPI.jar \
		"https://repo.codemc.io/repository/maven-public/de/tr7zw/item-nbt-api-plugin/${NBTAPI_VERSION}/item-nbt-api-plugin-${NBTAPI_VERSION}.jar"

RUN printf 'eula=true\n' > /server/eula.txt
COPY server.properties /server/server.properties
COPY log4j2.xml /server/log4j2.xml
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# Run as the host user/group (passed at build time) so files written to the mounted outputs/ have
# the correct ownership without any chown step.
ARG UID=1000
ARG GID=1000
RUN chown -R ${UID}:${GID} /server && mkdir -p /work && chown ${UID}:${GID} /work
USER ${UID}:${GID}
ENV HOME=/server
WORKDIR /server

ENTRYPOINT ["/entrypoint.sh"]
