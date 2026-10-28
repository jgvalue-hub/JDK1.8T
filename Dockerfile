# Demo image only; never supplies or embeds a login secret.
ARG BUILD_IMAGE=tomcat:9.0.123-jdk8-temurin-jammy@sha256:6ad419488b7b390e428a5bfa8635bb110be6c97714b7276ae535745a00f5c5f0
ARG RUNTIME_IMAGE=tomcat:9.0.123-jdk8-temurin-jammy@sha256:6ad419488b7b390e428a5bfa8635bb110be6c97714b7276ae535745a00f5c5f0
FROM ${BUILD_IMAGE} AS build
WORKDIR /build
COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
RUN chmod 0755 gradlew
COPY src ./src
RUN ./gradlew --no-daemon -Pdev clean test verifyVersions war

FROM ${RUNTIME_IMAGE}
RUN rm -rf /usr/local/tomcat/webapps/* /usr/local/tomcat/webapps.dist \
    && groupadd --gid 10001 workhub \
    && useradd --uid 10001 --gid 10001 --no-create-home workhub \
    && mkdir -p /var/lib/workhub \
    && chown -R 10001:10001 /var/lib/workhub /usr/local/tomcat
COPY --from=build --chown=10001:10001 /build/build/libs/workhub-dev.war /usr/local/tomcat/webapps/workhub.war
COPY --chown=10001:10001 deploy/server.xml /usr/local/tomcat/conf/server.xml
COPY --chown=10001:10001 deploy/context.xml /usr/local/tomcat/conf/context.xml
COPY deploy/container-entrypoint.sh /usr/local/bin/workhub-entrypoint
RUN chmod 0555 /usr/local/bin/workhub-entrypoint
USER 10001:10001
ENV WORKHUB_DATA_DIR=/var/lib/workhub
# Internal port only. No host port is published by this Dockerfile.
EXPOSE 10000
ENTRYPOINT ["/usr/local/bin/workhub-entrypoint"]
