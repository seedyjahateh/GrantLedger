FROM maven:3-eclipse-temurin-24@sha256:a137a467ec89b5713d0be817b55bdba6b4d6ef16e3d05565a79bc08d8e775a1c AS build
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
