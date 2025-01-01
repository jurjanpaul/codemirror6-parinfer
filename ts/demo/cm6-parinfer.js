// Supports:
// - Parinfer smartmode
//
// Does not (yet) support:
// - styling parentrails
// - importing as library
// - turning off parinfer or switching to other modes
const getParinfer = async () => {
    if (window.parinfer) {
        return window.parinfer;
    }
    try {
        const module = await import('parinfer');
        return (module.default || module);
    }
    catch (error) {
        throw new Error('Failed to load parinfer library: ' + error);
    }
};
const parinfer = await getParinfer();
import { ChangeSet, EditorSelection, EditorState, StateEffect, StateField } from "@codemirror/state";
import { EditorView } from "@codemirror/view";
import { setDiagnostics, forEachDiagnostic } from "@codemirror/lint";
import { invertedEffects } from "@codemirror/commands";
import { presentableDiff } from "@codemirror/merge";
let effectIdCounter = 0;
const parinferErrorEffect = StateEffect.define();
const parinferErrorField = StateField.define({
    create: () => ({
        id: ++effectIdCounter,
        previous: null,
        current: null
    }),
    update: (value, tr) => {
        const effect = tr.effects.filter(e => e.is(parinferErrorEffect)).at(-1);
        return effect ? effect.value : value;
    }
});
const invertParinferError = invertedEffects.of((tr) => {
    const inverted = [];
    const parinferErrors = tr.effects.filter(e => e.is(parinferErrorEffect));
    parinferErrors.forEach(effect => {
        const { id, previous, current } = effect.value;
        inverted.push(parinferErrorEffect.of({
            id: ++effectIdCounter,
            invertsId: id,
            previous: current,
            current: previous
        }));
    });
    return inverted;
});
function cmPosToParinferYx(doc, pos) {
    const line = doc.lineAt(pos);
    const y = line.number - 1;
    const x = pos - line.from;
    return [y, x];
}
function parinferYxToCmPos(doc, y, x) {
    return doc.line(y + 1).from + x;
}
function cmChangesetToParinferChanges(oldDoc, cmChanges) {
    const parinferChanges = [];
    cmChanges.iterChanges((fromA, toA, _fromB, _toB, inserted) => {
        const [fromAy, fromAx] = cmPosToParinferYx(oldDoc, fromA);
        const oldText = oldDoc.sliceString(fromA, toA);
        parinferChanges.push({
            lineNo: fromAy,
            x: fromAx,
            oldText: oldText,
            newText: inserted.toString()
        });
    });
    return parinferChanges;
}
function parinferResultToCmChanges(result, input) {
    const parinferredText = result.text;
    const diffs = presentableDiff(input, parinferredText);
    return diffs.map(change => {
        const { fromA, toA, fromB, toB } = change;
        return Object.assign({ from: fromA, to: toA }, (fromB !== toB && { insert: parinferredText.slice(fromB, toB) }));
    });
}
function maybeErrorEffect(startState, parinferError) {
    var _a;
    const existing = (_a = startState.field(parinferErrorField, false)) === null || _a === void 0 ? void 0 : _a.current;
    if (JSON.stringify(existing) !== JSON.stringify(parinferError)) {
        return parinferErrorEffect.of({
            id: ++effectIdCounter,
            previous: existing || null,
            current: parinferError
        });
    }
    return null;
}
function applyParinferSmartWithDiff(transaction) {
    const startState = transaction.startState;
    const oldCursor = startState.selection.main.head;
    const oldDoc = startState.doc;
    const [oldY, oldX] = cmPosToParinferYx(oldDoc, oldCursor);
    const newDoc = transaction.newDoc;
    const newText = newDoc.toString();
    const newSelection = transaction.newSelection.main;
    const newCursor = newSelection.head;
    const [newY, newX] = cmPosToParinferYx(newDoc, newCursor);
    const parinferChanges = cmChangesetToParinferChanges(oldDoc, transaction.changes);
    const selectionStartLine = !newSelection.empty ?
        cmPosToParinferYx(newDoc, newSelection.from)[0] : undefined;
    const result = parinfer.smartMode(newText, {
        prevCursorX: oldX,
        prevCursorLine: oldY,
        cursorX: newX,
        cursorLine: newY,
        changes: parinferChanges,
        selectionStartLine
    });
    if (!result.success) {
        const effect = maybeErrorEffect(startState, result.error);
        return effect ? { effects: [effect] } : null;
    }
    const cmChanges = parinferResultToCmChanges(result, newText);
    const newTransaction = transaction.state.update({ changes: cmChanges, filter: false });
    const newPos = parinferYxToCmPos(newTransaction.newDoc, result.cursorLine, result.cursorX);
    const effect = maybeErrorEffect(startState, null);
    return Object.assign({ changes: cmChanges, selection: EditorSelection.cursor(newPos), sequential: true }, (effect ? { effects: [effect] } : null));
}
function parinferTransactionFilter() {
    return EditorState.transactionFilter.of(tr => {
        if (tr.docChanged) {
            const parinferChanges = applyParinferSmartWithDiff(tr);
            if (parinferChanges) {
                if (parinferChanges.effects || parinferChanges.changes) {
                    return [tr, parinferChanges];
                }
            }
        }
        return tr;
    });
}
function errorToDiagnostics(doc, error) {
    const { x, lineNo, extra, message } = error;
    const pos = parinferYxToCmPos(doc, lineNo, x);
    const extraDiagnostics = extra ? errorToDiagnostics(doc, extra) : [];
    return [{
            severity: "error",
            source: "parinfer",
            from: pos,
            to: pos + 1,
            message: message
        }, ...extraDiagnostics];
}
function otherDiagnostics(state) {
    const others = [];
    forEachDiagnostic(state, (d, _from, _to) => {
        if (d.source !== "parinfer") {
            others.push(d);
        }
    });
    return others;
}
function parinferViewUpdateListener() {
    return EditorView.updateListener.of(update => {
        if (update.docChanged) {
            const state = update.state;
            const parinferError = state.field(parinferErrorField);
            const parinferDiagnostics = parinferError.current ?
                errorToDiagnostics(state.doc, parinferError.current) : [];
            const diagnosticTr = setDiagnostics(state, [
                ...parinferDiagnostics,
                ...otherDiagnostics(state)
            ]);
            update.view.dispatch(diagnosticTr);
        }
    });
}
export function parinferExtension() {
    return [
        parinferErrorField,
        invertParinferError,
        parinferTransactionFilter(),
        parinferViewUpdateListener()
    ];
}
//# sourceMappingURL=cm6-parinfer.js.map