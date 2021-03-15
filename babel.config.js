// babel.config.js
module.exports = {
  presets: [
      ['@babel/preset-env', {targets: {node: 'current'}}],
      'babel-preset-typescript-vue',
  ],
  plugins: [
    '@babel/plugin-transform-typescript'
  ]
};
