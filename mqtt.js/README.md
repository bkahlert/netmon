# Using MQTT.js

This project uses [MQTT.js](https://github.com/mqttjs/MQTT.js).

Adding it as any other npm dependency (for example, `implementation(npm("mqtt", "5.0.0"))`) does not seem to work.

Instead, it's included using `@JsModule("./mqtt")`.

The module is located in this directory and symlinked to the resources
directory of Kotlin/JS.

- To build/download a new version, run `./build.sh`.
- To test the current version, run `./test.sh`.
