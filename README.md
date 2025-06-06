# CodeMirror 6 Parinfer extension

<span><a href="https://www.npmjs.com/package/@jurjanpaul/codemirror6-parinfer" title="NPM version badge"><img src="https://img.shields.io/npm/v/@jurjanpaul/codemirror6-parinfer?color=blue" alt="NPM version badge" /></a></span>

A [CodeMirror 6](https://codemirror.net/) extension that integrates [Parinfer](https://shaunlebron.github.io/parinfer/), which facilitates structural editing of Lisp code (Clojure code in particular) by indentation.

By default [`smartMode`](https://github.com/parinfer/parinfer.js/tree/master#status-update-2019-smart-mode) is applied, but both `indentMode` and `parenMode` can be selected as well.

Initially I used ClojureScript on [Scittle](https://babashka.org/scittle/) to explore what was needed to get the integration to work, but the actual extension is written in TypeScript and available as ES module (with the `parinfer.js` dependency inlined for more convenient downstream bundling) from [npmjs.com](https://www.npmjs.com/package/@jurjanpaul/codemirror6-parinfer).

Try it at [the demo page](https://jurjanpaul.github.io/codemirror6-parinfer/)!

Please let me know if you have a use for this or have any feedback!

## Basic usage
```typescript
import { basicSetup } from 'codemirror';
import { EditorState } from '@codemirror/state';
import { EditorView } from '@codemirror/view';
import { parinferExtension } from '@jurjanpaul/codemirror6-parinfer';

const doc = `
(defn foo []
  [:bar \"baz\"])`

new EditorView({
  state: EditorState.create({
    doc,
    extensions: [basicSetup, parinferExtension()],
  }),
  parent: document.getElementById("editor");
});
```

## API

### `@jurjanpaul/codemirror6-parinfer` TypeScript module
```typescript
parinferExtension(initialConfig?: ParinferExtensionConfig): Extension
```
Main entry point: initialises the extension with optional configuration.<br>
By default Parinfer is enabled in <code>"smart"</code> mode.<br><br>


```typescript
type ParinferExtensionConfig = {
  enabled?: boolean
  mode?: ParinferMode
}
```

```typescript
type ParinferMode = "smart" | "indent" | "paren"
```

```typescript
configField: StateField<ParinferExtensionConfig>
```
CodeMirror state field with the current extension configuration.

<br>

```typescript
setConfigEffect: StateEffectType<ParinferExtensionConfig>
```
CodeMirror state effect that can be used in a transaction to update the extension's configuration.

<br>

```typescript
configureParinfer(view: EditorView, config: ParinferExtensionConfig)
```
Convenience wrapper around the `setConfigEffect` state effect.<br><br>

```typescript
switchMode(view: EditorView, mode: ParinferMode)
```
Speaks for itself. Convenience wrapper for `configureParinfer`.<br><br>

```typescript
disableParinfer(view: EditorView)
```
Speaks for itself. Convenience wrapper for `configureParinfer`.<br><br>

```typescript
enableParinfer(view: EditorView)
```
Speaks for itself. Convenience wrapper for `configureParinfer`.<br><br>

## Out of scope (at least for the foreseeable future)
 - visualising/styling the [Paren Trail](https://github.com/parinfer/parinfer.js/blob/master/doc/code.md#paren-trail) that parinfer includes in its result;
 - visualising the `tabStops` that parinfer includes in its result;
 - smart indentation (does not really belong to Parinfer, but missed anyway, because outside the legacy Clojure mode there is no support for CodeMirror 6 yet).

## Motivation
I had previously used CodeMirror 5 with Parinfer in the [Away from Preferred Editor ClojureScript Playground](https://github.com/jurjanpaul/ape-cljs-playground) and hoped to upgrade to CodeMirror 6 as soon as somebody would make a Parinfer extension available.

For quite a while, as a Clojure programmer I was simply too intimidated by the statically typed CodeMirror 6 module architecture to even consider taking on that task myself next to a few other side projects. But still not finding one some years after the CodeMirror 6 release, I finally found the time and motivation to take up the challenge, starting with studying TypeScript, etc.

## Implementation
Some observations w.r.t. the implementation can be found [here](docs/implementation.md).

