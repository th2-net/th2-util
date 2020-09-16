FROM gradle:6.6-jdk11 AS build
ARG app_version=0.0.0
COPY ./ .
RUN gradle dockerPrepare -Prelease_version=${app_version}

FROM openjdk:12-alpine
ENV GRPC_PORT=8080
WORKDIR /home
COPY --from=build ./ .
ENTRYPOINT ["/home/utility-service/bin/utility-service", "/home/utility-service/etc/config.yml"]
