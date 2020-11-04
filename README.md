## How it works

The Utility component contains a methods to execute general TH2 work.
The box has embedded gRPC server that helps reuse logic implemented on Java language in components written in other languages via gRPC call.

You can see provided gRPC calls with description in [gRPC scheme](grpc-utility/src/main/proto/th2/message_comparator.proto)

## Requried pins
Util component has got only one inbound gRPC pin . It may be used to link other components with 'util'. 
```yaml
apiVersion: th2.exactpro.com/v1
kind: Th2GenericBox
metadata:
  name: util
spec:
  pins:
    - name: server
      connection-type: grpc
```