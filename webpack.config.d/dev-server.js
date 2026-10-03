// noinspection JSUnresolvedReference

// The preview's pages and the VM kiosk reach the dev server on 8081, leaving 8080 to the broker. The kiosk asks for
// Host: 10.0.2.2:8081, which webpack-dev-server rejects unless allowedHosts says otherwise.
;(function (config) {
  'use strict'
  config.devServer = Object.assign(config.devServer || {}, {
    host: '127.0.0.1',
    port: 8081,
    allowedHosts: 'all',
  })
})(config)
