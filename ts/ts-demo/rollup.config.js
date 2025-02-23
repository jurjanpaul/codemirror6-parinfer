// This is a simple Rollup configuration file that will bundle a demo editor,
// serving to prove that the published codemirror6-parinfer ES module
// indeed offers all that is necessary.
// (And according to CodeMirror, Rollup is the way to go.)

import {nodeResolve} from "@rollup/plugin-node-resolve"
import terser from '@rollup/plugin-terser';
import typescript from '@rollup/plugin-typescript';

export default [
  {
    input: 'demo_editor.ts',
    plugins: [typescript(),
              nodeResolve({browser: true})],
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
