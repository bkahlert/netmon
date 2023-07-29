// default at https://github.com/tailwindlabs/tailwindcss/blob/master/stubs/config.full.js

/** @type {import('tailwindcss').Config} */
module.exports = {
  content: {
    files: ['./src/**/*.{js,html,css}'],
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
