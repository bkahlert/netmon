// noinspection JSUnresolvedReference

;(function (config) {
  'use strict'

  config.module.rules.push(
    {
      test: /\.(json)$/i,
      type: 'asset/resource',
      generator: {
        filename: 'assets/[name][ext][query]',
      },
    },
  )
})(config)
