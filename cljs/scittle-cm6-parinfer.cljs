(require '[clojure.string :as str])

;; Supports:
;; - Parinfer smartmode
;;
;; Does not (yet) support:
;; - styling parentrails
;; - importing as library
;; - turning off parinfer or switching to other modes

(def parinfer-error-effect
  (.define js/cm_state.StateEffect))

(def parinfer-error-field
  (.define js/cm_state.StateField
           #js{"create"
               (fn []
                 {:previous nil
                  :current nil})
               "update"
               (fn [value, tr]
                 (if-let [parinfer-error-effect
                          (->> (.-effects tr)
                               (filter #(.is % parinfer-error-effect))
                               first)]
                   (.-value parinfer-error-effect)
                   value))}))

(def invert-parinfer-error
  (.of js/cm_commands.invertedEffects
       (fn [tr]
         (let [inverted #js[]
               parinfer-errors
               (->> (.-effects tr)
                    (filter #(.is % parinfer-error-effect)))]
           (doseq [effect parinfer-errors
                   :let [{:keys [previous current]} (.-value effect)]]
             (println "inverting" previous current)
             (.push inverted (.of parinfer-error-effect {:previous current
                                                  :current previous})))
           inverted))))

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

(defn apply-parinfer-smart-with-diff
  [transaction]
  (let [start-state (.-startState transaction)
        old-cursor (-> start-state .-selection .-main .-head)
        old-doc (.-doc start-state)
        [old-y old-x] (cm-pos->parinfer-yx old-doc old-cursor)
        new-selection (-> transaction .-newSelection .-main)
        new-selection-start (.-from new-selection)
        new-doc (.-newDoc transaction)
        [new-selection-y _new-selection-x]
        (cm-pos->parinfer-yx new-doc new-selection-start)
        new-cursor (.-head new-selection)
        [new-y new-x] (cm-pos->parinfer-yx new-doc new-cursor)
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
      {:effects
       (let [existing-parinfer-error
             (:current (.field start-state parinfer-error-field false))]
         (when-not (= existing-parinfer-error (.-error result))
           (.of parinfer-error-effect
                {:previous existing-parinfer-error
                 :current (.-error result)})))}
      ;;  :annotations (.of (.-addToHistory js/cm_state.Transaction) true)}
      ;; (let [transaction
      ;;       (js/cm_lint.setDiagnostics (.-state transaction) ; strongly discouraged because expensive
      ;;                                  #js[(error->diagnostic new-doc
      ;;                                                         (js->clj (.-error result)))])]
      ;;   ;; {:effects (get (js->clj transaction) "effects")}
      ;;   {:effects (.of setParinferError (.-error result))}) ; should become part of undo history; why not?
        ;;  :annotations (.of (.-history js/cm_state.Annotation) "add")})
      (let [cursorX (.-cursorX result)
            cursorLine (.-cursorLine result)
            changes (parinfer-result->cm-changes result new-text)
            new-transaction (.update (.-state transaction) ; strongly discouraged because expensive
                                     (clj->js {:changes changes
                                               :filter false}))
            new-pos (parinfer-yx->cm-pos (.-newDoc new-transaction)
                                         cursorLine
                                         cursorX)]
        {:changes changes
         :selection (.cursor js/cm_state.EditorSelection new-pos)
         :sequential true}))))

(defn parinfer-transaction-filter []
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

(defn error->diagnostic
  [doc {:strs [x lineNo _extra message] :as _error}]
  ;; (println "extra" (pr-str extra))
  (let [pos (parinfer-yx->cm-pos doc lineNo x)]
    #js{:severity "error"
        :source "parinfer"
        :from pos
        :to (inc pos)
        :message message}))

(defn parinfer-view-update-listener []
  (.of (.-updateListener js/cm_view.EditorView)
       (fn [update]
         (when (.-docChanged update)
           (let [state (.-state update)
                 {:keys [previous current] :as _parinfer-error} (.field state parinfer-error-field)
                 diagnostic-tr
                 (if current
                    (js/cm_lint.setDiagnostics state
                                               #js[(error->diagnostic (.-doc state)
                                                                      (js->clj current))])
                    (js/cm_lint.setDiagnostics state #js[]))] ; TODO: only remove diagnostics added by parinfer
             (println "dispatching diagnostic-tr for" current)
             (.dispatch (.-view update) diagnostic-tr))))))

;; Still some racing condition
;; w.r.t. undo/redo and dispatching diagnostic effecting transactions
;; and value of parinfer-error-field
;; Strange: should all be part of synchronous effect system.

(defn parinfer-extension []
  #js[parinfer-error-field
      invert-parinfer-error
      (parinfer-transaction-filter)
      (parinfer-view-update-listener)])

(defn create-editor [doc]
  (let [start-state
        (.create
         js/cm_state.EditorState
         #js {:doc doc
              :extensions #js[(.of js/cm_language.indentUnit " ")
                              js/codemirror.basicSetup
                              (js/lang_clojure.clojure)
                              (parinfer-extension)]})]
    (js/cm_view.EditorView. #js{:state start-state
                                :parent (.getElementById js/document "editor")})))

(def initial-doc
  (str/trim "
(defn foo []
  [:bar \"baz\"])
"))

(def cm-editor
  (create-editor initial-doc))
