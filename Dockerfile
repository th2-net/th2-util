FROM openjdk:12-alpine
ENV GRPC_PORT=8080
WORKDIR /home
COPY ./ .
ENTRYPOINT ["/home/utility-service/bin/utility-service", "/home/utility-service/etc/config.yml"]