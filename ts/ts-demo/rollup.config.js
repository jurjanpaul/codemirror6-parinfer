// Note: I hardly know what I'm doing here!
// I just want to prove to myself that the TypeScript ESM module I'm publishing
// can actually be used from TypeScript. According to CodeMirror, rollup is the way to go...

import commonjs from "@rollup/plugin-commonjs"
import {nodeResolve} from "@rollup/plugin-node-resolve"
import terser from '@rollup/plugin-terser';
import typescript from '@rollup/plugin-typescript';

export default [
  {
    input: 'demo_editor.ts',
    plugins: [typescript(),
              nodeResolve({browser: true}),
              commonjs({
                transformMixedEsModules: true // to inline Parinfer
             })],
    output: [
      {
        file: 'dist/demo_editor.bundle.min.js',
        name: 'DemoEditor',
        format: 'iife',
        inlineDynamicImports: true,
        plugins: [terser()],
        sourcemap: true,
      }
    ]
  }
];
