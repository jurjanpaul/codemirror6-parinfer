import typescript from '@rollup/plugin-typescript';
import terser from '@rollup/plugin-terser';
import dts from 'rollup-plugin-dts';

export default [
  {
    input: 'ts/cm6-parinfer.ts',
    output: [
      {
        file: 'dist/cm6-parinfer.js',
        format: 'esm',
        sourcemap: true,
      },
      {
        file: 'dist/cm6-parinfer.min.js',
        format: 'esm',
        plugins: [terser()],
        sourcemap: true,
      },
    ],
    plugins: [typescript()],
  },
  {
    input: 'dist/cm6-parinfer.d.ts',
    output: {
      file: 'dist/cm6-parinfer.d.ts',
      format: 'esm',
    },
    plugins: [dts()],
  },
];
