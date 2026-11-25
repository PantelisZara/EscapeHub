FROM maven:3.9.16-eclipse-temurin-21@sha256:99e61abcff91a9b1333463bd8451fb18495d6eba9250ac66a338b518f8278320 AS build
WORKDIR /build
COPY pom.xml checkstyle.xml ./
COPY .mvn/maven.config .mvn/maven.config
COPY src/ src/
RUN mkdir -p .cache/tmp && mvn -B verify

FROM eclipse-temurin:21-jre-noble@sha256:54999a956a8bf8be9af0052790a6a832bf91a62fad3199fd194c3cc28057fc62
WORKDIR /opt/escapehub
COPY --from=build /build/target/escapehub-*.jar escapehub.jar
RUN install -d -m 0700 -o 10001 -g 10001 /var/lib/escapehub
ENV ESCAPEHUB_BIND_ADDRESS=0.0.0.0 \
    ESCAPEHUB_ACCOUNTS_FILE=/var/lib/escapehub/accounts.tsv
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-cp", "/opt/escapehub/escapehub.jar", "gr.escapehub.web.DemoServer"]
CMD ["8080"]
