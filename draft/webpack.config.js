/* eslint-disable no-unused-vars */
const ImageMinimizerPlugin = require('image-minimizer-webpack-plugin')
const HtmlWebpackPlugin = require('html-webpack-plugin')
const path = require('path')

const distDir = path.resolve(__dirname, 'dist')
const filename = (name, ext) => `${name}${ext}`

// noinspection JSUnusedGlobalSymbols
module.exports = () => ({
  mode: 'development',
  entry: {
    index: './src/index.js',
  },
  output: {
    path: distDir,
    filename: filename('[name]', '.js'),
    clean: true,
    globalObject: 'this',
    library: { name: 'fritz2-tailwind-draft', type: 'umd' },
    pathinfo: true, // add comments stating contained modules and tree-shaking info
  },
  devtool: 'inline-source-map',
  devServer: {
    static: './dist',
    open: false,
    hot: true,
    watchFiles: ['./src/*.html'],
  },
  module: {
    rules: [
      {
        test: /\.css$/,
        use: ['style-loader', 'css-loader', 'postcss-loader'],
      },
      {
        test: /\.(?:jpe?g|png|gif|svg)$/i,
        type: 'asset/resource',
        generator: { filename: 'images/' + filename('[name]', '[ext][query]') },
      },
      {
        test: /\.(?:woff|woff2|eot|ttf|otf)$/,
        type: 'asset/resource',
        generator: { filename: 'fonts/' + filename('[name]', '[ext][query]') },
      },
    ],
  },
  plugins: [
    new HtmlWebpackPlugin({
      title: 'Fritz2 Tailwind CSS Draft',
      filename: 'index.html',
      template: './src/index.html',
    }),
  ],
  optimization: {
    minimizer: [
      `...`,
      new ImageMinimizerPlugin({
        minimizer: {
          implementation: ImageMinimizerPlugin.imageminMinify,
          options: {
            plugins: [
              ['gifsicle', { interlaced: true }],
              ['jpegtran', { progressive: true }],
              ['optipng', { optimizationLevel: 5 }],
              [
                'svgo',
                {
                  // see https://github.com/svg/svgo#configuration
                  multipass: true,
                  plugins: [
                    {
                      name: 'preset-default',
                      params: { overrides: { removeViewBox: false } },
                    },
                  ],
                },
              ],
            ],
          },
        },
      }),
    ],
  },
  stats: {
    errorDetails: true,
  },
})
