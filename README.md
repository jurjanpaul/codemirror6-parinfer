# Parinfer in CodeMirror 6 demo

Combines [Parinfer](https://shaunlebron.github.io/parinfer/) with [CodeMirror 6](https://codemirror.net/) using [Scittle](https://babashka.org/scittle/), building on [Scittle's CodeMirror example](https://babashka.org/scittle/codemirror.html).

Getting there, but this is still very much a work in progress, nowhere near a published library.

Try it at [the demo page](https://jurjanpaul.github.io/codemirror6-parinfer/), which also uses [Nextjournal's Clojure syntax support for CodeMirror 6](https://github.com/nextjournal/lang-clojure).

## TODO
- [x] `smartMode` Parinfer
- [ ] Highlight any errors (step 4 in [Adding Parinfer to an Editor](https://github.com/parinfer/parinfer.js/blob/master/doc/integrating.md))
  - [x] Fix undo/redo
  - [ ] Leave other effects alone
- [ ] Refactor (constantly of course)
- [ ] ? Take special care of selections
- [ ] Maybe mark `parenTrails`
- [ ] Examine interaction with other CodeMirror extensions
- [ ] Make into a published JS library
  - [ ] Options to configure/toggle
- [ ] Optimise?
  - [ ] Use [CodeMirror's diff function](https://github.com/codemirror/merge?tab=readme-ov-file#user-content-diff) instead of [diff-match-patch](https://github.com/google/diff-match-patch)

## Motivation
If I ever hope to upgrade the [Away from Preferred Editor ClojureScript Playground](https://github.com/jurjanpaul/ape-cljs-playground) from CodeMirror 5 to CodeMirror 6, I need Parinfer integration, which nobody seems to have made available for CodeMirror 6 yet. (This is noteworthy because Parinfer was originally developed on CodeMirror and version 6, a complete rewrite, has been out for a number of years now.)

But even if that upgrade never happens, I hope that this may be a small contribution/inspiration to keeping Parinfer a viable option for editing Clojure/Lisp across as many different editor(component)s as possible, because I heavily depend on its integration in other editors. 🙂

(Clojure developers are encouraged to do structural editing with Paredit. By all means, go for it! I know I’m in a tiny minority, but making edits with these elaborate key combinations does not work for me, so far. Also, a simple iPhone keyboard does not come with the Ctrl and Alt keys that Paredit requires.)

Honestly though: Parinfer has a simple API, so this should not be rocket science. CodeMirror 6 though seems/seemed rather complex compared to the CodeMirror 5 API...

## Experience so far

 * Looked at Shaun Lebron's original [parinfer-codemirror.js](https://github.com/shaunlebron/parinfer-codemirror) code and will study it more, as well as other Parinfer integrations, but decided that CodeMirror 6 is different enough that a fresh start makes sense.
 * Interesting that [`transactionFilter`](https://codemirror.net/docs/ref/#state.EditorState^transactionFilter) is the hook needed to 'add' synchronous Parinfer modifications to a user triggered state transaction. (I overlooked it at first, because filtering means something else in the contexts that I am used to.)
 * The documentation for [`transactionFilter`](https://codemirror.net/docs/ref/#state.EditorState^transactionFilter) clearly states that it is recommended to avoid accessing `Transaction.state` in a filter, but it seems unavoidable when creating a new transaction for the Parinfer changes and to apply diagnostics in case of error.
 * I had learned from [this CodeMirror discussion thread](https://discuss.codemirror.net/t/implement-parinfer-with-snippets/3549/2) that it would probably be a good idea to diff the Parinfer output with its input. I am now indeed doing that, using the [diff-match-patch](https://github.com/google/diff-match-patch) library, if only to keep the edit history's memory usage down. **Update**: CodeMirror's [diff function](https://github.com/codemirror/merge?tab=readme-ov-file#user-content-diff) might work as well, keeping external dependencies down.
 * Marking Parinfer errors in such a way that they can be undone and redone proved trickier than I had expected. I got it to work with a StateField, StateEffect and invertedEffects, but perhaps I am still simply missing something obvious. *Oops, it's still buggy...*
 * All in all, a lot of expensive transformations need to happen for each key press... Even so, the result feels fast enough, even with a large code base on a phone.
 * I wonder if the same result may after all be achieved with less expensive steps, but I'll take (relatively) 'slow' over asynchronous postprocessing any day, having experienced how poorly that works out when an editor provides no alternative.
