// noinspection JSUnresolvedReference

// The preview's pages and the VM kiosk reach the dev server on 8081, leaving 8080 to the broker. The kiosk asks for
// Host: 10.0.2.2:8081, which webpack-dev-server rejects unless allowedHosts says otherwise. With a host set, the client
// would reconnect to that host, which is the guest's own loopback in the VM, so it takes the address the page came from.
// The page asks for stats.json next to itself, which only a board's lighttpd serves: the device flavor names that board.
;(function (config) {
  'use strict'
  config.devServer = Object.assign(config.devServer || {}, {
    host: '127.0.0.1',
    port: 8081,
    allowedHosts: 'all',
    client: { webSocketURL: 'auto://0.0.0.0:0/ws' },
  })
  var statsProxy = process.env.NETMON_STATS_PROXY
  if (statsProxy) {
    config.devServer.proxy = [{ context: ['/stats.json'], target: statsProxy, changeOrigin: true }]
  }
})(config)
