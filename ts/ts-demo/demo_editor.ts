import { basicSetup } from 'codemirror';
import { indentUnit, indentService } from '@codemirror/language';
import { EditorState } from '@codemirror/state';
import { EditorView } from '@codemirror/view';
import type { ParinferMode } from '@jurjanpaul/codemirror6-parinfer';
import { parinferExtension, switchMode, disableParinfer, enableParinfer } from '@jurjanpaul/codemirror6-parinfer';
import { clojureSmartIndentExtension } from '@jurjanpaul/codemirror6-clojure-smart-indent';
import { clojure } from '@nextjournal/lang-clojure';

export function init(parent: HTMLElement, doc: string) {
  return new EditorView({
    state: EditorState.create({
      doc,
      extensions: [indentUnit.of(" "),
                   basicSetup,
                   clojure(),
                   parinferExtension(),
                   clojureSmartIndentExtension(indentService)],
    }),
    parent: parent
  })
}

export function switchParinferMode(view: EditorView, value: string) {
  switchMode(view, value as ParinferMode);
}

export function setParinferEnabled(view: EditorView, v: boolean) {
  if (v) {
    enableParinfer(view);
  } else {
    disableParinfer(view);
  }
}
