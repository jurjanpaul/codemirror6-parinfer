// Does not (yet) support:
// - styling parentrails

import type { Parinfer, ParinferChange, ParinferOptions, ParinferResult, ParinferError } from "parinfer"
import parinfer from "parinfer"
const parinferLib = parinfer as Parinfer
import type { ChangeSet, ChangeSpec, Extension, StateEffectType, Text, Transaction, TransactionSpec } from "@codemirror/state"
import { EditorSelection, EditorState, StateEffect, StateField } from "@codemirror/state"
import type { ViewUpdate } from "@codemirror/view"
import { EditorView } from "@codemirror/view"
import type { Diagnostic } from "@codemirror/lint"
import { setDiagnostics, forEachDiagnostic } from "@codemirror/lint"
import { invertedEffects } from "@codemirror/commands"
import { presentableDiff } from "@codemirror/merge"

function filterTransactionEffects<T> (effectType: StateEffectType<T>, transaction: Transaction): StateEffect<T>[] {
  return transaction.effects.filter(e => e.is(effectType))
}

/**
 * Type representing a Parinfer mode.
 */
export type ParinferMode = "smart" | "indent" | "paren"

export type ParinferExtensionConfig = {
  enabled?: boolean
  mode?: ParinferMode
}

export const setConfigEffect = StateEffect.define<ParinferExtensionConfig>()

const defaultConfig: ParinferExtensionConfig = {
  enabled: true,
  mode: "smart"
}

export const configField = StateField.define<ParinferExtensionConfig | null>({
  create: () => null,
  update: (value, tr) => {
    const effect = filterTransactionEffects(setConfigEffect, tr).at(-1)
    return effect ? { ...value, ...effect.value } : value
  }
})

function enabled(state: EditorState): boolean {
  return { ...defaultConfig, ...state.field(configField, false) }.enabled!
}

function mode(state: EditorState): ParinferMode {
  return { ...defaultConfig, ...state.field(configField, false) }.mode!
}

interface InvertibleFieldValue<T> {
  previous: T | null
  current: T | null
}

function invertInvertibleEffects(effectType: StateEffectType<InvertibleFieldValue<any>>, tr: Transaction):  StateEffect<InvertibleFieldValue<any>>[] {
  const inverted: StateEffect<InvertibleFieldValue<any>>[] = []
  const effects = tr.effects.filter(e => e.is(effectType))
  effects.forEach(effect => {
    const { previous, current } = effect.value
    inverted.push(effectType.of({
      previous: current,
      current: previous
    }))
  })
  return inverted
}

const parinferErrorEffect = StateEffect.define<InvertibleFieldValue<ParinferError>>()

const parinferErrorField = StateField.define<InvertibleFieldValue<ParinferError>>({
  create: () => ({
    previous: null,
    current: null
  }),
  update: (value, tr) => {
    const effect = tr.effects.filter(e => e.is(parinferErrorEffect)).at(-1)
    return effect ? effect.value : value
  }
})

const invertParinferError = invertedEffects.of((tr) => {
  return invertInvertibleEffects(parinferErrorEffect, tr)
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

function cmChangeSetToParinferChanges(oldDoc: Text, cmChanges: ChangeSet): ParinferChange[] {
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
      previous: existing || null,
      current: parinferError
    })
  }
  return null
}

function invokeParinfer(mode: ParinferMode, text: string, opts: ParinferOptions): ParinferResult {
  switch(mode) {
    case "smart":
      return parinferLib.smartMode(text, opts)
    case "indent":
      return parinferLib.indentMode(text, opts)
    case "paren":
      return parinferLib.parenMode(text, opts)
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
  const parinferChanges = cmChangeSetToParinferChanges(oldDoc, transaction.changes)
  const selectionStartLine =
    !newSelection.empty ? cmPosToParinferYx(newDoc, newSelection.from)[0] : undefined
  const result = invokeParinfer(mode(startState),
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

function maybeInitialize(transaction: Transaction, initialConfig: ParinferExtensionConfig): TransactionSpec | readonly TransactionSpec[] {
  if (!(transaction.startState.field(configField, false))) {
    return [
      transaction,
      {effects: setConfigEffect.of({ ...defaultConfig, ...initialConfig})}
    ]
  }
  return transaction
}

function parinferTransactionFilter(initialConfig?: ParinferExtensionConfig) {
  return EditorState.transactionFilter.of(tr => {
    const aSetConfigEffect = filterTransactionEffects(setConfigEffect, tr).at(-1)
    if ((enabled(tr.startState) && tr.docChanged) ||
        (aSetConfigEffect && aSetConfigEffect.value.enabled)) {
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
  const { x, lineNo, extra, message } = error
  const pos = parinferYxToCmPos(doc, lineNo, x)
  const extraDiagnostics: Diagnostic[] = extra ? errorToDiagnostics(doc, extra) : []
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
  })
  return others
}

function hasEffectOfType(effectType: StateEffectType<any>, update: ViewUpdate): boolean {
 return update.transactions.some(tr => {
   return tr.effects.some(e => e.is(effectType))
 })
}

function parinferViewUpdateListener() {
  return EditorView.updateListener.of(update => {
    if (hasEffectOfType(setConfigEffect, update) || update.docChanged) {
      const state = update.state
      const parinferError = state.field(parinferErrorField)
      const parinferDiagnostics: Diagnostic[] =
        (enabled(state) && parinferError.current) ? errorToDiagnostics(state.doc, parinferError.current)
                                           : []
      const diagnosticTr = setDiagnostics(state,
                                          [...parinferDiagnostics,
                                           ...otherDiagnostics(state)])
      update.view.dispatch(diagnosticTr)
    }
  })
}

/**
 * Updates the editor's extension configuration.
 * @param view the editor view
 * @param config new configuration for the Parinfer extension
 */
export function configureParinfer(view: EditorView, config: ParinferExtensionConfig): void {
   view.dispatch({effects: setConfigEffect.of(config)})
}

/**
 * Switches the Parinfer mode for the provided editor view.
 * @param view the editor view
 * @param mode the Parinfer mode to switch to
 */
export function switchMode(view: EditorView, mode: ParinferMode): void {
  configureParinfer(view, {mode: mode})
}

/**
 * Disables Parinfer for the provided editor view.
 * @param view the editor view
 */
export function disableParinfer(view: EditorView): void {
  configureParinfer(view, {enabled: false})
}

/**
 * Enables Parinfer for the provided editor view.
 * @param view the editor view
 */
export function enableParinfer(view: EditorView): void {
  configureParinfer(view, {enabled: true})
}

/**
 * Initialises the Parinfer extension for CodeMirror6.
 * @param initialConfig (optional) the initial configuration for the Parinfer extension
 * @returns the CodeMirror6 Parinfer extension in the form of an array of extensions
 */
export function parinferExtension(initialConfig?: ParinferExtensionConfig): Extension {
  return [
    configField,
    parinferErrorField,
    invertParinferError,
    parinferTransactionFilter(initialConfig),
    parinferViewUpdateListener()
  ]
}
