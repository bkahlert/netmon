// noinspection JSUnresolvedReference

;(function (config) {
  'use strict'
  const hash = config.mode === 'production' ? '.[contenthash:8]' : ''

  config.module.rules.push(
    {
      test: /\.(json)$/i,
      type: 'asset/resource',
      generator: {
        filename: `assets/[name]${hash}[ext]`,
      },
    },
  )
})(config)
