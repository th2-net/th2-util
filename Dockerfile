FROM gradle:6.6-jdk11 AS build
ARG release_version
COPY ./ .
RUN gradle dockerPrepare -Prelease_version=${release_version}

FROM openjdk:12-alpine
ENV GRPC_PORT=8080
WORKDIR /home
COPY --from=build /home/gradle/build/docker .
ENTRYPOINT ["/home/service/bin/service"]
