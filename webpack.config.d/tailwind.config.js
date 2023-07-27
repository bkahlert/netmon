// noinspection JSUnresolvedReference

// must be in the jsMain/resource folder
const mainCssFile = 'styles.css'

// Tailwind CSS configuration
// - documentation: https://tailwindcss.com/docs/configuration
// - default: https://github.com/tailwindlabs/tailwindcss/blob/master/stubs/config.full.js
/** @type {import('tailwindcss').Config} */
const tailwindConfig = {
    content: {
      files: [
        '*.{js,html,css}',
        './kotlin/**/*.{js,html,css}',
      ],
      transform: {
        'js': (content) => content.replaceAll(/\\r|\\n|\\r\\n/g, ' '),
      },
    },
    theme: {
      extend: {
        textColor: {
          default: require('tailwindcss/colors').slate['900'],
          invert: require('tailwindcss/colors').slate['100'],
        },
        backgroundColor: {
          default: require('tailwindcss/colors').slate['50'],
          invert: require('tailwindcss/colors').slate['800'],
        },
        // CSS device and capability media queries, https://css-tricks.com/touch-devices-not-judged-size/
        // Usage: `pointer-coarse:` to target touch devices, `hover-hover:` to target devices with hover capability
        screens: {
          'pointer-coarse': { raw: '(pointer: coarse)' },
          'pointer-fine': { raw: '(pointer: fine)' },
          'pointer-none': { raw: '(pointer: none)' },
          'hover-hover': { raw: '(hover: hover)' },
          'hover-none': { raw: '(hover: none)' },
          'any-pointer-coarse': { raw: '(any-pointer: coarse)' },
          'any-pointer-fine': { raw: '(any-pointer: fine)' },
          'any-pointer-none': { raw: '(any-pointer: none)' },
          'any-hover-hover': { raw: '(any-hover: hover)' },
          'any-hover-on-demand': { raw: '(any-hover: on-demand)' },
          'any-hover-none': { raw: '(any-hover: none)' },
        },
      },
    },
    plugins: [
      require('@tailwindcss/typography'),
      // require('@tailwindcss/forms'),
      require('tailwind-heropatterns')({
        // see https://github.com/AndreaMinato/tailwind-heropatterns
        colors: { default: require('tailwindcss/colors').gray['500'] },
        opacity: { default: 0.4 },
      }),
    ],
  }


// Tailwind CSS settings for WebPack
;(function (config) {
  'use strict'
  const entry = config && config.output && config.output.path
    ? config.output.path + '/../../../processedResources/js/main/' + mainCssFile
    : './kotlin/' + mainCssFile
  config.entry.main.push(entry)
  config.module.rules.push({
    test: /\.css$/,
    use: [
      { loader: 'style-loader' },
      { loader: 'css-loader' },
      {
        loader: 'postcss-loader',
        options: {
          postcssOptions: {
            plugins: [
              require('postcss-import'),
              require('tailwindcss')({ config: tailwindConfig }),
              require('autoprefixer')({
                overrideBrowserslist: [
                  'defaults',
                  'chrome >= 86',
                ],
              }),
              require('cssnano'),
            ],
          },
        },
      },
    ],
  })
})(config)
