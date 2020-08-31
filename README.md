## How it works

The Utility component contains a methods to execute general TH2 work.
The box has embedded gRPC server that helps reuse logic implemented on Java language in components written in other languages via gRPC call.

You can see provided gRPC calls with description in [gRPC scheme](grpc-utility/src/main/proto/th2/message_comparator.proto)

## Environment variables

- GRPC_PORT=8080