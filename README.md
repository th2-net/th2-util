## How it works (3.1.0)

The Utility component contains a methods to execute general TH2 work.
The box has embedded gRPC server that helps reuse logic implemented on Java language in components written in other languages via gRPC call.

You can see provided gRPC calls with description in [gRPC scheme](grpc-utility/src/main/proto/th2/message_comparator.proto)

## Requried pins
Util component has got only one inbound gRPC pin . It may be used to link other components with 'util'. 
```yaml
apiVersion: th2.exactpro.com/v1
kind: Th2Box
metadata:
  name: util
spec:
  pins:
    - name: server
      connection-type: grpc
```

## Custom config

logMessageSize - test string size in character which is printed as info log at the start of component

```yaml
apiVersion: th2.exactpro.com/v1
kind: Th2Box
metadata:
  name: util
spec:
  image-name: ghcr.io/th2-net/th2-util
  image-version: <image version>
  custom-config:
    logMessageSize: 1024
  extended-settings:
    service:
      enabled: true
      nodePort: '<port>'
    envVariables:
      JAVA_TOOL_OPTIONS: "-XX:+ExitOnOutOfMemoryError"
    resources:
      limits:
        memory: 200Mi
        cpu: 200m
      requests:
        memory: 100Mi
        cpu: 50m
```

## Changes

### 3.1.0

+ reads dictionaries from the /var/th2/config/dictionary folder
+ uses mq_router, grpc_router, cradle_manager optional JSON configs from the /var/th2/config folder
+ tries to load log4j.properties files from sources in order: '/var/th2/config', '/home/etc', configured path via cmd, default configuration
+ update Cradle version. Introduce async API for storing events
+ removed gRPC event loop handling
+ fixed dictionary reading

### 3.0.0

+ Migrate to common v3