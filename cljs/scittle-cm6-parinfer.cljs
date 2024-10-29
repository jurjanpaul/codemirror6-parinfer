(require '[clojure.string :as str])

;; Supports:
;; - Parinfer smartmode
;;
;; Does not (yet) support:
;; - error highlighting
;;   - well, it does now, but it's not very good (not triggered by undo/redo + when does 'extra' come into play?)
;; - importing as library
;; - turning off parinfer or switching to other modes

(def diff-engine
  (js/diff_match_patch.))

(defn cm-pos->parinfer-yx
  [doc pos]
  (let [line (.lineAt doc pos)
        y (dec (.-number line))
        x (- pos (.-from line))]
    [y x]))

(defn parinfer-yx->cm-pos
  [doc y x]
  (+ (.-from (.line doc (inc y))) x))

(defn cm-changeset->parinfer-changes
  "Converts a CodeMirror 6 changeset into a Parinfer changes array."
  [old-doc codemirror-changes]
  (let [changes #js[]]
    (.iterChanges
     codemirror-changes
     (fn [from-a to-a _from-b _to-b inserted]
       (let [[from-a-y from-a-x] (cm-pos->parinfer-yx old-doc from-a)
             old-text (.sliceString old-doc from-a to-a)]
         (.push changes
                #js{:lineNo from-a-y
                    :x from-a-x
                    :oldText old-text
                    :newText (.toString inserted)}))))
    changes))

(defn- parinfer-result->cm-changes
  [result input]
  (let [parinferred-text (.-text result)
        diffs (.diff_main diff-engine input parinferred-text)]
    (.diff_cleanupSemantic diff-engine diffs)
    (->> (js->clj diffs)
         (reduce (fn [{:keys [pos] :as acc} diff]
                   (let [op (aget diff 0)
                         text (aget diff 1)]
                     (case op
                       0 (update acc :pos + (count text))
                       -1 (let [new-pos (+ pos (count text))]
                            (-> acc
                                (update :changes conj {:from pos
                                                       :to new-pos})
                                (assoc :pos new-pos)))
                       1 (update acc :changes conj {:from pos
                                                    :to pos
                                                    :insert text}))))
                 {:changes []
                  :pos 0})
         :changes)))

(defn error->diagnostic
  [doc {:strs [x lineNo _extra message] :as _error}]
  ;; (println "extra" (pr-str extra))
  (let [pos (parinfer-yx->cm-pos doc lineNo x)]
    #js{:severity "error"
        :from pos
        :to (inc pos)
        :message message}))

(defn apply-parinfer-smart-with-diff
  [transaction]
  (let [start-state (.-startState transaction)
        old-cursor (-> start-state .-selection .-main .-head)
        old-doc (.-doc start-state)
        [old-y old-x] (cm-pos->parinfer-yx old-doc old-cursor)
        new-state (.-state transaction) ; strongly discouraged because expensive, but can't be helped
        new-selection (-> new-state .-selection .-main)
        new-selection-start (.-from new-selection)
        new-state-doc (.-doc new-state)
        [new-selection-y _new-selection-x]
        (cm-pos->parinfer-yx new-state-doc new-selection-start)
        new-cursor (.-head new-selection)
        new-doc (.-doc new-state)
        [new-y new-x] (cm-pos->parinfer-yx new-state-doc new-cursor)
        changes (cm-changeset->parinfer-changes old-doc
                                                (.-changes transaction))
        new-text (.toString new-doc)
        result (.smartMode js/parinfer
                           new-text
                           #js {:prevCursorX old-x
                                :prevCursorLine old-y
                                :cursorX new-x
                                :cursorLine new-y
                                :changes changes})]
                                ;; :selectionStartLine new-selection-y})] ; turned off as it is undermining smart (paren) mode somehow?!?
    (if (not (.-success result))
      (let [transaction
            (js/cm_lint.setDiagnostics new-state
                                       #js[(error->diagnostic new-doc
                                                              (js->clj (.-error result)))])]
        {:effects (get (js->clj transaction) "effects")})
      (let [cursorX (.-cursorX result)
            cursorLine (.-cursorLine result)
            changes (parinfer-result->cm-changes result new-text)
            new-transaction (.update new-state
                                     (clj->js {:changes changes
                                               :filter false}))
            new-pos (parinfer-yx->cm-pos (.-doc (.-state new-transaction)) ; expensive again
                                         cursorLine
                                         cursorX)]
        {:changes changes
         :selection (.cursor js/cm_state.EditorSelection new-pos)
         :sequential true
         :effects (get (js->clj (js/cm_lint.setDiagnostics new-state #js[]))
                       "effects")})))) ; reset diagnostics (maybe to regourous?) Not triggered in case of undo!

(defn parinfer-plugin []
  (.of (.-transactionFilter js/cm_state.EditorState)
       (fn [transaction]
         (if (.-docChanged transaction)
           (let [{:keys [effects changes] :as parinfer-changes}
                 (apply-parinfer-smart-with-diff transaction)]
             (if (or effects (seq (js->clj changes)))
               #js[transaction
                   (clj->js parinfer-changes)]
               transaction))
           transaction))))

(defn create-editor [doc]
  (let [start-state
        (.create
         js/cm_state.EditorState
         #js {:doc doc
              :extensions #js [(.of js/cm_language.indentUnit " ")
                               js/codemirror.basicSetup
                               (parinfer-plugin)
                               (js/lang_clojure.clojure)]})]
    (js/cm_view.EditorView. #js {:state start-state
                                         :parent (.getElementById js/document "editor")})))

(def initial-doc
  (str/trim "
(defn foo []
  [:bar \"baz\"])
"))

(def cm-editor
  (create-editor initial-doc))
