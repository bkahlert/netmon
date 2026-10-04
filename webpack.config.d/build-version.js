// noinspection JSUnresolvedReference

// Bakes the version into the bundle: NETMON_VERSION, which the display shows next to its title, is replaced by the
// output of `git describe`, e.g. v2.2.0-4-g54adb6b (-dirty with uncommitted changes). The build may set
// NETMON_VERSION itself, e.g. where no .git is at hand.
;(function (config) {
  'use strict'
  const webpack = require('webpack')
  const { execFileSync } = require('child_process')

  function describe() {
    try {
      return execFileSync('git', ['describe', '--tags', '--always', '--dirty'], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim()
    } catch (e) {
      return 'unknown'
    }
  }

  config.plugins.push(new webpack.DefinePlugin({
    NETMON_VERSION: JSON.stringify(process.env.NETMON_VERSION || describe()),
  }))
})(config)
