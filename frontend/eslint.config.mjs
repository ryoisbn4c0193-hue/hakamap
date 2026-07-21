import js from '@eslint/js';
import prettierConfig from 'eslint-config-prettier/flat';
import importX from 'eslint-plugin-import-x';
import jsxA11y from 'eslint-plugin-jsx-a11y';
import react from 'eslint-plugin-react';
import reactHooks from 'eslint-plugin-react-hooks';
import globals from 'globals';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  {
    ignores: ['coverage/**', 'dist/**', 'node_modules/**'],
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ['**/*.{js,jsx,ts,tsx}'],
    languageOptions: {
      ecmaVersion: 'latest',
      globals: {
        ...globals.browser,
        ...globals.node,
      },
      parserOptions: {
        ecmaFeatures: {
          jsx: true,
        },
      },
    },
    plugins: {
      'import-x': importX,
      'jsx-a11y': jsxA11y,
      react,
      'react-hooks': reactHooks,
    },
    settings: {
      react: {
        version: 'detect',
      },
      'import-x/resolver': {
        typescript: true,
      },
    },
    rules: {
      ...react.configs.recommended.rules,
      ...react.configs['jsx-runtime'].rules,
      ...reactHooks.configs.flat.recommended.rules,
      ...jsxA11y.configs.recommended.rules,
      ...importX.flatConfigs.recommended.rules,
      'array-callback-return': 'error',
      'arrow-body-style': ['error', 'as-needed'],
      camelcase: ['error', { properties: 'never' }],
      'comma-dangle': ['error', 'always-multiline'],
      curly: ['error', 'all'],
      eqeqeq: ['error', 'always'],
      'import-x/first': 'error',
      'import-x/newline-after-import': 'error',
      'import-x/no-absolute-path': 'error',
      'import-x/no-cycle': 'error',
      'import-x/no-duplicates': 'error',
      'import-x/no-unresolved': 'error',
      'import-x/order': [
        'error',
        {
          alphabetize: { caseInsensitive: true, order: 'asc' },
          groups: ['builtin', 'external', 'internal', 'parent', 'sibling', 'index', 'type'],
          'newlines-between': 'always',
        },
      ],
      'no-console': 'warn',
      'no-param-reassign': 'error',
      'no-use-before-define': 'off',
      'object-shorthand': ['error', 'always'],
      'prefer-const': 'error',
      'prefer-template': 'error',
      quotes: ['error', 'single', { avoidEscape: true }],
      'react/function-component-definition': ['error', { namedComponents: 'function-declaration' }],
      'react/jsx-boolean-value': ['error', 'never'],
      'react/jsx-no-useless-fragment': 'error',
      'react/self-closing-comp': 'error',
      semi: ['error', 'always'],
    },
  },
  prettierConfig,
);
