// Does not (yet) support:
// - styling parentrails

import type { Parinfer, ParinferChange, ParinferOptions, ParinferResult, ParinferError } from "parinfer"

declare global {
  interface Window {
    parinfer?: Parinfer;
  }
}

const getParinfer = async (): Promise<Parinfer> => {
  if (window.parinfer) {
    return window.parinfer;
  }
  try {
    const module = await import('parinfer');
    return (module.default || module) as Parinfer;
  } catch (error) {
    throw new Error('Failed to load parinfer library: ' + error);
  }
};
const parinfer_lib = await getParinfer();

import type { ChangeSpec, Text, Transaction, TransactionSpec } from "@codemirror/state"
import { ChangeSet, EditorSelection, EditorState, StateEffect, StateEffectType, StateField } from "@codemirror/state"
import { EditorView, ViewUpdate } from "@codemirror/view"
import type { Diagnostic } from "@codemirror/lint"
import { setDiagnostics, forEachDiagnostic } from "@codemirror/lint"
import { invertedEffects } from "@codemirror/commands"
import { presentableDiff } from "@codemirror/merge"

function filterTransactionEffects<T> (effectType: StateEffectType<T>, transaction: Transaction): StateEffect<T>[] {
  return transaction.effects.filter(e => e.is(effectType))
}

export const setEnabledEffect = StateEffect.define<boolean>()

const enabledField = StateField.define<boolean>({
 create: () => true,
 update: (value, tr) => {
   const effect = filterTransactionEffects(setEnabledEffect, tr).at(-1)
   return effect ? effect.value : value
 }
})

export type ParinferMode = "smart" | "indent" | "paren"

export const switchModeEffect = StateEffect.define<ParinferMode>()

const modeField = StateField.define<ParinferMode>({
  create: () => "smart",
  update: (value, tr) => {
    const effect = filterTransactionEffects(switchModeEffect, tr).at(-1)
    return effect ? effect.value : value
  }
})

let effectIdCounter = 0

interface InvertibleFieldValue<T> {
  id: number // for debugging only
  invertsId?: number // for debugging only
  previous: T | null
  current: T | null
}

function invertInvertibleEffects(effectType: StateEffectType<InvertibleFieldValue<any>>, tr: Transaction):  StateEffect<InvertibleFieldValue<any>>[] {
  const inverted: StateEffect<InvertibleFieldValue<any>>[] = []
  const effects = tr.effects.filter(e => e.is(effectType))
  effects.forEach(effect => {
    const { id, previous, current } = effect.value
    inverted.push(effectType.of({
      id: ++effectIdCounter,
      invertsId: id,
      previous: current,
      current: previous
    }))
  })
  return inverted
}

const parinferErrorEffect = StateEffect.define<InvertibleFieldValue<ParinferError>>()

const parinferErrorField = StateField.define<InvertibleFieldValue<ParinferError>>({
  create: () => ({
    id: ++effectIdCounter,
    previous: null,
    current: null
  }),
  update: (value, tr) => {
    const effect = tr.effects.filter(e => e.is(parinferErrorEffect)).at(-1)
    return effect ? effect.value : value
  }
})

const invertParinferError = invertedEffects.of((tr) => {
  return invertInvertibleEffects(parinferErrorEffect, tr);
})

function cmPosToParinferYx(doc: Text, pos: number): [number, number] {
  const line = doc.lineAt(pos)
  const y = line.number - 1
  const x = pos - line.from
  return [y, x]
}

function parinferYxToCmPos(doc: Text, y: number, x: number): number {
  return doc.line(y + 1).from + x
}

function cmChangesetToParinferChanges(oldDoc: Text, cmChanges: ChangeSet): ParinferChange[] {
  const parinferChanges: ParinferChange[] = []
  cmChanges.iterChanges((fromA: number, toA: number, _fromB: number, _toB: number, inserted: Text) => {
    const [fromAy, fromAx] = cmPosToParinferYx(oldDoc, fromA)
    const oldText = oldDoc.sliceString(fromA, toA)
    parinferChanges.push({
      lineNo: fromAy,
      x: fromAx,
      oldText: oldText,
      newText: inserted.toString()
    })
  })
  return parinferChanges
}

function parinferResultToCmChanges(result: ParinferResult, input: string): ChangeSpec[] {
  const parinferredText = result.text
  const diffs = presentableDiff(input, parinferredText)
  return diffs.map(change => {
    const { fromA, toA, fromB, toB } = change
    return {
      from: fromA,
      to: toA,
      ...(fromB !== toB && { insert: parinferredText.slice(fromB, toB) })
    }
  })
}

function maybeErrorEffect(startState: EditorState, parinferError: ParinferError | null): StateEffect<InvertibleFieldValue<ParinferError>> | null {
  const existing = startState.field(parinferErrorField, false)?.current
  if (JSON.stringify(existing) !== JSON.stringify(parinferError)) {
    return parinferErrorEffect.of({
      id: ++effectIdCounter,
      previous: existing || null,
      current: parinferError
    })
  }
  return null
}

function parinfer(mode: ParinferMode, text: string, opts: ParinferOptions): ParinferResult {
  switch(mode) {
    case "smart":
      return parinfer_lib.smartMode(text, opts)
    case "indent":
      return parinfer_lib.indentMode(text, opts)
    case "paren":
      return parinfer_lib.parenMode(text, opts)
  }
}

function applyParinferSmartWithDiff(transaction: Transaction): TransactionSpec | null {
  const startState = transaction.startState
  const oldCursor = startState.selection.main.head
  const oldDoc = startState.doc
  const [oldY, oldX] = cmPosToParinferYx(oldDoc, oldCursor)
  const newDoc = transaction.newDoc
  const newText = newDoc.toString()
  const newSelection = transaction.newSelection.main
  const newCursor = newSelection.head
  const [newY, newX] = cmPosToParinferYx(newDoc, newCursor)
  const parinferChanges = cmChangesetToParinferChanges(oldDoc, transaction.changes)
  const selectionStartLine =
    !newSelection.empty ? cmPosToParinferYx(newDoc, newSelection.from)[0] : undefined
  const result = parinfer(startState.field(modeField, false) || "smart",
                          newText, {
                            prevCursorX: oldX,
                            prevCursorLine: oldY,
                            cursorX: newX,
                            cursorLine: newY,
                            changes: parinferChanges,
                            selectionStartLine
                          })
  if (!result.success) {
    const effect = maybeErrorEffect(startState, result.error!)
    return effect ? { effects: [effect] } : null
  }
  const cmChanges = parinferResultToCmChanges(result, newText)
  const newTransaction = transaction.state.update({ changes: cmChanges, filter: false })
  const newPos = parinferYxToCmPos(newTransaction.newDoc, result.cursorLine, result.cursorX)
  const effect = maybeErrorEffect(startState, null)
  return {
    changes: cmChanges,
    selection: EditorSelection.cursor(newPos),
    sequential: true,
    ... (effect ? {effects: [effect]} : null)
  }
}

function parinferTransactionFilter() {
  return EditorState.transactionFilter.of(tr => {
    const aSetEnabledEffect = filterTransactionEffects(setEnabledEffect, tr).at(-1)
    if ((tr.startState.field(enabledField, false) && tr.docChanged) ||
        (aSetEnabledEffect && aSetEnabledEffect.value)) {
      const parinferChanges = applyParinferSmartWithDiff(tr)
      if (parinferChanges) {
        if (parinferChanges.effects || parinferChanges.changes) {
          return [tr, parinferChanges]
        }
      }
    }
    return tr
})}

function errorToDiagnostics(doc: Text, error: ParinferError): Diagnostic[] {
  const { x, lineNo, extra, message } = error;
  const pos = parinferYxToCmPos(doc, lineNo, x);
  const extraDiagnostics: Diagnostic[] = extra ? errorToDiagnostics(doc, extra) : [];
  return [{
    severity: "error",
    source: "parinfer",
    from: pos,
    to: pos + 1,
    message: message
  }, ...extraDiagnostics]
}

function otherDiagnostics(state: EditorState): Diagnostic[] {
  const others: Diagnostic[] = []
  forEachDiagnostic(state, (d, _from, _to) => {
    if (d.source !== "parinfer") {
      others.push(d)
    }
  });
  return others
}

function hasEffectOfType(effectType: StateEffectType<any>, update: ViewUpdate): boolean {
 return update.transactions.some(tr => {
   return tr.effects.some(e => e.is(effectType))
 })
}

function parinferViewUpdateListener() {
  return EditorView.updateListener.of(update => {
    if (hasEffectOfType(setEnabledEffect, update) || update.docChanged) {
      const state = update.state
      const enabled = state.field(enabledField, false)
      const parinferError = state.field(parinferErrorField)
      const parinferDiagnostics: Diagnostic[] =
        (enabled && parinferError.current) ? errorToDiagnostics(state.doc, parinferError.current)
                                           : []
      const diagnosticTr = setDiagnostics(state,
                                          [...parinferDiagnostics,
                                           ...otherDiagnostics(state)])
      update.view.dispatch(diagnosticTr)
    }
  })
}

export function switchMode(view: EditorView, mode: ParinferMode): void {
  view.dispatch({effects: switchModeEffect.of(mode)})
}

export function disableParinfer(view: EditorView): void {
  view.dispatch({effects: setEnabledEffect.of(false)})
}

export function enableParinfer(view: EditorView): void {
  view.dispatch({effects: setEnabledEffect.of(true)})
}

export function parinferExtension() {
  return [
    enabledField,
    modeField,
    parinferErrorField,
    invertParinferError,
    parinferTransactionFilter(),
    parinferViewUpdateListener()
  ]
}
