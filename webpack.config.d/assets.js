// noinspection JSUnresolvedReference

;(function (config) {
  'use strict'
  const hash = config.mode === 'production' ? '.[contenthash:8]' : ''

  config.module.rules.push(
    {
      test: /\.(jpe?g|png|gif|svg)$/i,
      type: 'asset/resource',
      generator: {
        filename: `images/[name]${hash}[ext]`,
      },
    },
    {
      test: /\.(woff|woff2|eot|ttf|otf)$/,
      type: 'asset/resource',
      generator: {
        filename: `fonts/[name]${hash}[ext]`,
      },
    },
  )
})(config)
