// noinspection JSUnresolvedReference

;(function (config) {
  'use strict'

  config.module.rules.push(
    {
      test: /\.(jpe?g|png|gif|svg)$/i,
      type: 'asset/resource',
      generator: {
        filename: 'images/[name][ext][query]',
      },
    },
    {
      test: /\.(woff|woff2|eot|ttf|otf)$/,
      type: 'asset/resource',
      generator: {
        filename: 'fonts/[name][ext][query]',
      },
    },
  )
})(config)
