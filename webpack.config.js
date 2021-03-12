"use strict";

let path = require('path');
const VueLoaderPlugin = require('vue-loader/lib/plugin');

module.exports = (env, argv) => {
  return {
    plugins: [new VueLoaderPlugin()],
    stats: 'minimal',
    devtool: 'source-map',
    module: {
      rules: [
        {
          test: /\.vue$/,
          loader: 'vue-loader',
        },
        {
          test: /\.css$/i,
          use: [
            'style-loader',
            'css-loader'
          ],
        },
        {
          test: /\.scss$/,
          exclude: /node_modules/,
          use: [
            'vue-style-loader',
            'css-loader',
            'sass-loader'
          ],
        },
        {
          test: /\.ts$/,
          loader: 'ts-loader',
          options: {
            appendTsSuffixTo: [/\.vue$/],
          }
        }
      ]
    },
    resolve: {
      extensions: ['.ts', '.js', '.vue', '.css']
    }
  }
};
