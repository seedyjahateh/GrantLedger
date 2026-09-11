FROM maven:3.9.9-eclipse-temurin-21@sha256:3a4ab3276a087bf276f79cae96b1af04f53731bec53fb2e651aca79e4b10211e AS build
WORKDIR /build
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline
COPY src src
COPY config config
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:25-jre-jammy@sha256:20a695e74d47fb29cda1cbad5d9ee6cfad4ac6e88a8e048ed6265cede1e71f5e
RUN groupadd --gid 10001 grantledger && useradd --uid 10001 --gid 10001 --no-create-home grantledger
WORKDIR /app
COPY --from=build --chown=10001:10001 /build/target/grantledger-1.0.0-SNAPSHOT.jar /app/app.jar
USER 10001:10001
EXPOSE 8080 9090
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","/app/app.jar"]
