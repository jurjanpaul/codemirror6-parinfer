# Parinfer in CodeMirror 6

<span><a href="https://www.npmjs.com/package/@jurjanpaul/codemirror6-parinfer" title="NPM version badge"><img src="https://img.shields.io/npm/v/@jurjanpaul/codemirror6-parinfer?color=blue" alt="NPM version badge" /></a></span>

A [CodeMirror 6](https://codemirror.net/) extension that integrates [Parinfer](https://shaunlebron.github.io/parinfer/), which facilitates structural editing of Lisp code (Clojure code in particular) by indentation.

By default [`smartMode`](https://github.com/parinfer/parinfer.js/tree/master#status-update-2019-smart-mode) is applied, but both `indentMode` and `parenMode` can be selected as well. (For now the extension does not support styling the [Paren Trail](https://github.com/parinfer/parinfer.js/blob/master/doc/code.md#paren-trail).)

initially I used ClojureScript on [Scittle](https://babashka.org/scittle/) to explore what was needed to get the integration to work, but the actual extension is written in TypeScript and available from [npmjs.com](https://www.npmjs.com/package/@jurjanpaul/codemirror6-parinfer).

Try it at [the demo page](https://jurjanpaul.github.io/codemirror6-parinfer/)!

Please let me know if you have a use for this or have any feedback!

## Basic usage
```
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

## Motivation
I had previously used CodeMirror 5 with Parinfer in the [Away from Preferred Editor ClojureScript Playground](https://github.com/jurjanpaul/ape-cljs-playground) and hoped to upgrade to CodeMirror 6 as soon as somebody would make a Parinfer extension available. 

For quite a while, as a Clojure programmer I was simply too intimidated by the statically typed CodeMirror 6 module architecture to even consider taking on that task myself next to a few other side projects. But still not finding one some years after the CodeMirror 6 release, I finally found the time and motivation to take up the challenge, starting with studying TypeScript, etc.


## Some experiences and observations

 * Looked at Shaun Lebron's original [parinfer-codemirror.js](https://github.com/shaunlebron/parinfer-codemirror) code and will study it more, as well as other Parinfer integrations, but decided that CodeMirror 6 is different enough that a fresh start makes sense.
 * Interesting that [`transactionFilter`](https://codemirror.net/docs/ref/#state.EditorState^transactionFilter) is the hook needed to 'add' synchronous Parinfer modifications to a user triggered state transaction. (I overlooked it at first, because filtering means something else in the contexts that I am used to.)
 * The documentation for [`transactionFilter`](https://codemirror.net/docs/ref/#state.EditorState^transactionFilter) clearly states that it is recommended to avoid accessing `Transaction.state` in a filter, but it seems unavoidable when creating a new transaction for the Parinfer changes and to apply diagnostics in case of error.
 * I had learned from [this CodeMirror discussion thread](https://discuss.codemirror.net/t/implement-parinfer-with-snippets/3549/2) that it would probably be a good idea to diff the Parinfer output with its input, if only to keep the edit history's memory usage down. I started using the [diff-match-patch](https://github.com/google/diff-match-patch) library for this, but later found CodeMirror's own [diff function](https://github.com/codemirror/merge?tab=readme-ov-file#user-content-diff) which I am now using (needs less work converting diffs and keeps the number of external dependencies down).
 * Marking Parinfer errors in such a way that they can be undone and redone proved trickier than I had expected. I got it to work with a StateField, StateEffect and invertedEffects, but perhaps I am still missing something simple and obvious.
 * All in all, a lot of expensive transformations need to happen for each key press... Even so, the result feels fast enough, even with a large code base on a phone.
 * I wonder if the same result may after all be achieved with less expensive steps, but I'll take (relatively) 'slow' over asynchronous postprocessing any day, having experienced how poorly that works out when an editor provides no alternative.
