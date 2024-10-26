# Parinfer in CodeMirror 6 demo

Combines [Parinfer](https://shaunlebron.github.io/parinfer/) with [CodeMirror 6](https://codemirror.net/) using [Scittle](https://babashka.org/scittle/), building on [Scittle's CodeMirror example](https://babashka.org/scittle/codemirror.html).

Getting there, but this is still very much a work in progress!

(Nowhere near a published library yet though.)

Try it at https://jurjanpaul.github.io/codemirror6-parinfer/ (which also uses [Nextjournal's Clojure syntax support](https://github.com/nextjournal/lang-clojure)).


## Motivation
Ever since I started coding in Clojure (coming from years of programming in Python), I never got rid of my addiction to Parinfer. I understand, in theory, why Clojure programmers are encouraged to work with the particular flavour of Paredit that is available for their editor of choice: editing on the exact  level of the forms that make up the program. Even so, I never managed, nor tried hard enough probably, to get those keybindings into finger memory and have my editing mind 'feel' the code in front of me as different than just lines that need to be manipulated (added, deleted, split, combined, indented, deindented).

So, Parinfer is a lifesaver, keeping those forms in order. (And yes: sometimes I mess up so badly, deleting or pasting half-forms, that I need to help out a little to get the parens in the right places again. That's a cost I'm willing to pay in order to enjoy my simple keystroke editing freedom.)

A couple of years ago I got it in my head that I wanted to be able to explore small, Advent of Code-style algorithm challenges in Clojure on my mobile phone, just to make use of 'lost moments' to familiarise myself further with Clojure idioms for approaching typical problems. Using Paredit on an iPhone keyboard, without Ctrl and Alt keys that should somehow be combined with other keys, would not only be unattractive, but also completely impossible. So, Parinfer was the obvious and only solution to provide any kind of structural editing. Using Scittle and [Shaun le Bron's parinfer-codemirror library](https://github.com/shaunlebron/parinfer-codemirror), I was able to get the ['Away from Preferred Editor ClojureScript Playground'](https://jurjanpaul.github.io/ape-cljs-playground/) working to my satisfaction.  Note: there is nothing polished about it, but it works (and I eventually used it to solve all the 2023 Advent of Code challenges on my phone).

All this time I have been well aware that CodeMirror 5, which I am using there, had already been superseded by CodeMirror 6, a complete rewrite, for which no ready-made Parinfer plugin exists. In the short term I didn't mind not being able to upgrade, but in the long run it is typically undesirable to be stuck on an old software version. Also, I was starting to worry that Parinfer support in editors was going to be a thing of the past. I had already seen the demise of the Atom editor, which had great Parinfer integration, being replaced by VS Code, which is really limited - on purpose - in how well Parinfer can be integrated. A dark scenario seemed already unfolding: because of limited editor support Parinfer's reputation was getting worse, and as a result Editor (plugin) builders would care even less about building or maintaining support for it. That's why I finally decided that I should make the integration of Parinfer into CodeMirror 6 my next side project, just to learn what it would take to get it done or to inspire others with some example code to do a better job.

Honestly though: Parinfer has a simple API, so this should not be rocket science. CodeMirror 6 though seems/seemed rather complex compared to the CodeMirror 5 API...

## Experience so far
...

## TODO
- [x] `smartMode` Parinfer
- [ ] Highlight any errors (step 4 in [Adding Parinfer to an Editor](https://github.com/parinfer/parinfer.js/blob/master/doc/integrating.md))
- [ ] Extend README
- [ ] Maybe mark parentrail
- [ ] Examine interaction with other CodeMirror extensions
- [ ] Make into a published JS library
  - [ ] Options to configure/toggle
- [ ] Optimise
