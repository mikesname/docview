"use strict";

let path = require('path');
const VueLoaderPlugin = require('vue-loader/lib/plugin');
const ForkTsCheckerWebpackPlugin = require('fork-ts-checker-webpack-plugin')

module.exports = (env, argv) => {
  return {
    plugins: [new VueLoaderPlugin(), new ForkTsCheckerWebpackPlugin({
      typescript: {
        configFile: '../../../../tsconfig.json'
      }
    })],
    stats: 'minimal',
    devtool: 'source-map',
    cache: true,
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
          exclude: /node_modules/,
          loader: 'ts-loader',
          options: {
            appendTsSuffixTo: [/\.vue$/],
            // Type checking done by forked
            transpileOnly: true,
          }
        }
      ]
    },
    externals: {
      // The below allows Typescript to `import Vue from 'vue'`
      // without including Vue in the bundle.
      vue: 'Vue'
    },
    resolve: {
      extensions: ['.ts', '.js', '.vue', '.css']
    }
  }
};
