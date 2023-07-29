function consoleLog(...args) {
  const entry = document.createElement('pre')
  entry.innerHTML = args.join(' ')
  document.getElementById('target').prepend(entry)
}

const clientId = 'mqttjs_' + Math.random().toString(16).substr(2, 8)

// This sample should be run in tandem with the aedes_server.js file.
// Simply run it:
// $ node aedes_server.js
//
// Then run this file in a separate console:
// $ node websocket_sample.js
//
const host = 'ws://test.mosquitto.org:8081'

const options = {
  keepalive: 30,
  clientId,
  protocolId: 'MQTT',
  protocolVersion: 5,
  clean: true,
  reconnectPeriod: 1000,
  connectTimeout: 30 * 1000,
  will: {
    topic: 'WillMsg',
    payload: 'Connection Closed abnormally..!',
    qos: 0,
    retain: false,
  },
  rejectUnauthorized: false,
}

consoleLog('connecting mqtt client')
const client = mqtt.connect(host, options)

client.on('error', (err) => {
  consoleLog('Failed to connect', err)
  client.end()
})

client.on('connect', () => {
  consoleLog('client connected:' + clientId)
  client.subscribe('dt/netmon/home/scan', { qos: 1 })
})

client.on('message', (topic, message, packet) => {
  consoleLog('Received Message:= ' + message.toString() + '\nOn topic:= ' + topic)
})

client.on('close', () => {
  consoleLog(clientId + ' disconnected')
})
