module.exports = {
  // These settings are duplicated in .editorconfig:
  semi: false, // default: true
  singleQuote: true, // default: false
  trailingComma: 'all',
  bracketSpacing: true,
  overrides: [
    {
      files: '*.js',
      options: {
        parser: 'flow',
      },
    },
  ],
}
