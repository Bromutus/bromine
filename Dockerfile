FROM gradle:8-alpine AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle shadowJar --no-daemon

FROM eclipse-temurin:17-alpine

RUN mkdir /app

COPY --from=build /home/gradle/src/build/libs/ /app/
COPY application-docker.yaml /application.yaml

ENTRYPOINT ["java","-jar","/app/at.bromutus.bromine-0.0.1-all.jar"]