import commonjs from "@rollup/plugin-commonjs"
import {nodeResolve} from "@rollup/plugin-node-resolve"
import dts from 'rollup-plugin-dts';
import sourcemaps from 'rollup-plugin-sourcemaps';
import terser from '@rollup/plugin-terser';
import typescript from '@rollup/plugin-typescript';
import { assert } from "console";

export default [
  {
    input: 'ts/cm6-parinfer.ts',
    external: ['assert',
               '@codemirror/state',
               '@codemirror/view',
               '@codemirror/lint',
               '@codemirror/commands',
               '@codemirror/merge'],
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
      }
    ],
    plugins: [
      typescript({
        sourceMap: false,
        inlineSources: true,
      }),
      nodeResolve(),
      commonjs({
        ignore: ['assert'],
        transformMixedEsModules: true, // inline Parinfer
      })
    ]
  },
  {
    input: 'dist/cm6-parinfer.d.ts',
    output: {
      file: 'dist/cm6-parinfer.d.ts',
      format: 'esm',
    },
    plugins: [dts()],
  }
];
