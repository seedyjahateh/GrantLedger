FROM maven:3.9.15-eclipse-temurin-26@sha256:029a8e2838ae68238ffb8be407cddbb3f07d4d839c60c6f26c619a69fd184531 AS build
WORKDIR /build
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline
COPY src src
COPY config config
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-jammy@sha256:bce52ea7da1f72e6bf5bec505e63b6eb55ba79ad1226903579f77eab1a80139a
RUN groupadd --gid 10001 grantledger && useradd --uid 10001 --gid 10001 --no-create-home grantledger
WORKDIR /app
COPY --from=build --chown=10001:10001 /build/target/grantledger-1.0.0-SNAPSHOT.jar /app/app.jar
USER 10001:10001
EXPOSE 8080 9090
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","/app/app.jar"]
