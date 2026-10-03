// noinspection JSUnresolvedReference

// The production page names its bundle after its content, so lighttpd can let browsers keep it for good.
// index.html names the files with their plain names; jsBrowserDistribution fills in the hashed ones.
;(function (config) {
  'use strict'
  if (config.mode === 'production') {
    config.output.filename = 'netmon.[contenthash:8].js'
  }
})(config)
