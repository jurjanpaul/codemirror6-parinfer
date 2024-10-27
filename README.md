# Parinfer in CodeMirror 6 demo

Combines [Parinfer](https://shaunlebron.github.io/parinfer/) with [CodeMirror 6](https://codemirror.net/) using [Scittle](https://babashka.org/scittle/), building on [Scittle's CodeMirror example](https://babashka.org/scittle/codemirror.html).

Getting there, but this is still very much a work in progress, nowhere near a published library.

Try it at https://jurjanpaul.github.io/codemirror6-parinfer/ (which also uses [Nextjournal's Clojure syntax support for CodeMirror 6](https://github.com/nextjournal/lang-clojure)).

## TODO
- [x] `smartMode` Parinfer
- [ ] Highlight any errors (step 4 in [Adding Parinfer to an Editor](https://github.com/parinfer/parinfer.js/blob/master/doc/integrating.md))
- [ ] Extend README
- [ ] Maybe mark `parenTrails`
- [ ] Examine interaction with other CodeMirror extensions
- [ ] Make into a published JS library
  - [ ] Options to configure/toggle
- [ ] Optimise

## Motivation
If I ever hope to upgrade the [Away from Preferred Editor ClojureScript Playground](https://github.com/jurjanpaul/ape-cljs-playground) from CodeMirror 5 to CodeMirror 6, I need Parinfer integration, which nobody seems to have made available for CodeMirror 6 yet. (This is noteworthy because Parinfer was originally developed on CodeMirror and version 6, a complete rewrite, has been out for a number of years now.)

But even if that upgrade never happens, I hope that this may be a small contribution/inspiration to keeping Parinfer a viable option for editing Clojure/Lisp across as many different editor(component)s as possible, because I heavily depend on its integration in other editors. 🙂

(Clojure developers are encouraged to do structural editing with Paredit. By all means, go for it! I know I’m in a tiny minority, but making edits with these elaborate key combinations does not work for me, so far. Also, a simple iPhone keyboard does not come with the Ctrl and Alt keys that Paredit requires.)

Honestly though: Parinfer has a simple API, so this should not be rocket science. CodeMirror 6 though seems/seemed rather complex compared to the CodeMirror 5 API...

## Experience so far
...

