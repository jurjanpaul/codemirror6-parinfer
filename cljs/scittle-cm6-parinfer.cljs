;; Does not (yet) support:
;; - styling parentrails

(def effect-id-counter (atom 0))

(defn next-effect-id []
  (swap! effect-id-counter inc))

(defn debug [label v]
  ;; (println label (js->clj v))
  v)

(defn filter-transaction-effects
  [effect-type tr]
  (->> (.-effects tr)
       (filter #(.is % effect-type))))

(def set-enabled-effect
  (.define js/cm_state.StateEffect))

(def ^:private enabled-field
  (.define js/cm_state.StateField
           #js{"create"
               (fn []
                 true)
               "update"
               (fn [value, tr]
                 (if-let [a-set-enabled-effect
                          (->> (filter-transaction-effects set-enabled-effect tr) last)]
                   (.-value a-set-enabled-effect)
                   value))}))

(def switch-mode-effect
 (.define js/cm_state.StateEffect))

(def ^:private mode-field
  (.define js/cm_state.StateField
           #js{"create"
               (fn []
                 "smart")
               "update"
               (fn [value, tr]
                 (if-let [a-switch-mode-effect
                          (->> (filter-transaction-effects switch-mode-effect tr) last)]
                   (.-value a-switch-mode-effect)
                   value))}))

(def ^:private parinfer-error-effect
  (.define js/cm_state.StateEffect))


(def ^:private parinfer-error-field
  (.define js/cm_state.StateField
           #js{"create"
               (fn []
                 {:id (next-effect-id)
                  :previous nil
                  :current nil})
               "update"
               (fn [value, tr]
                 (if-let [a-parinfer-error-effect
                          (->> (filter-transaction-effects parinfer-error-effect tr) last)]
                   (.-value a-parinfer-error-effect)
                   value))}))

(def ^:private invert-parinfer-error
  (.of js/cm_commands.invertedEffects
       (fn [tr]
         (let [inverted #js[]
               parinfer-error-effects
               (filter-transaction-effects parinfer-error-effect tr)]
           (doseq [effect parinfer-error-effects
                   :let [{:keys [id previous current]} (.-value effect)]]
             (.push inverted
                    (.of parinfer-error-effect
                         (debug "invert-parinfer-error"
                                {:id (next-effect-id)
                                 :inverts-id id
                                 :previous current
                                 :current previous}))))
           inverted))))

(defn- cm-pos->parinfer-yx
  [doc pos]
  (let [line (.lineAt doc pos)
        y (dec (.-number line))
        x (- pos (.-from line))]
    [y x]))

(defn- parinfer-yx->cm-pos
  [doc y x]
  (+ (.-from (.line doc (inc y))) x))

(defn- cm-changeset->parinfer-changes
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
        diffs (js/cm_merge.presentableDiff input parinferred-text)]
     (->> (js->clj diffs)
          (mapv (fn [change]
                  (let [from-a (.-fromA change)
                        to-a (.-toA change)
                        from-b (.-fromB change)
                        to-b (.-toB change)]
                    (merge {:from from-a
                            :to to-a}
                           (when-not (= from-b to-b)
                             {:insert (subs parinferred-text from-b to-b)}))))))))

(defn- maybe-error-effect
  [start-state parinfer-error]
  (let [existing-parinfer-error
        (:current (.field start-state parinfer-error-field false))]
    (when-not (= (js->clj existing-parinfer-error)
                 (js->clj parinfer-error))
      (.of parinfer-error-effect
           (debug "maybe-error-effect"
                  {:id (next-effect-id)
                   :previous existing-parinfer-error
                   :current parinfer-error})))))

(defn parinfer
   [mode text opts]
   (case mode
     "smart" (.smartMode js/parinfer text opts)
     "indent" (.indentMode js/parinfer text opts)
     "paren" (.parenMode js/parinfer text opts)))

(defn- apply-parinfer-smart-with-diff
  [transaction]
  (let [start-state (.-startState transaction)
        old-cursor (-> start-state .-selection .-main .-head)
        old-doc (.-doc start-state)
        [old-y old-x] (cm-pos->parinfer-yx old-doc old-cursor)
        new-doc (.-newDoc transaction)
        new-text (.toString new-doc)
        new-selection (-> transaction .-newSelection .-main)
        new-cursor (.-head new-selection)
        [new-y new-x] (cm-pos->parinfer-yx new-doc new-cursor)
        changes (cm-changeset->parinfer-changes old-doc
                                                (.-changes transaction))
        selection-start-line
        (when-not (.-empty new-selection)
           (-> (cm-pos->parinfer-yx new-doc (.-from new-selection))
               first))
        result (parinfer (or (.field start-state mode-field false) "smart")
                         new-text
                         #js{:prevCursorX old-x
                             :prevCursorLine old-y
                             :cursorX new-x
                             :cursorLine new-y
                             :changes changes
                             :selectionStartLine selection-start-line})]
    (if (not (.-success result))
      {:effects (maybe-error-effect start-state (.-error result))}
      (let [changes (parinfer-result->cm-changes result new-text)
            new-transaction (.update (.-state transaction) ; strongly discouraged because expensive
                                     (clj->js {:changes changes
                                               :filter false}))
            new-pos (parinfer-yx->cm-pos (.-newDoc new-transaction)
                                         (.-cursorLine result)
                                         (.-cursorX result))]
        {:changes changes
         :selection (.cursor js/cm_state.EditorSelection new-pos)
         :sequential true
         :effects (maybe-error-effect start-state nil)}))))

(defn- enabled? [state]
  (.field state enabled-field false))

(defn- parinfer-transaction-filter []
  (.of (.-transactionFilter js/cm_state.EditorState)
       (fn [transaction]
         (let [a-set-enabled-effect
               (->> (filter-transaction-effects set-enabled-effect transaction) last)]
           (if (or (and (enabled? (.-startState transaction))
                        (.-docChanged transaction))
                   (and a-set-enabled-effect (.-value a-set-enabled-effect)))
             (let [{:keys [effects changes] :as parinfer-changes}
                   (apply-parinfer-smart-with-diff transaction)]
               (if (or effects (seq (js->clj changes)))
                 #js[transaction
                     (clj->js parinfer-changes)]
                 transaction))
             transaction)))))

(defn- error->diagnostics
  [doc {:strs [x lineNo extra message] :as _error}]
  (let [pos (parinfer-yx->cm-pos doc lineNo x)
        extra-diagnostics (if extra
                             (error->diagnostics doc extra)
                             #js[])]
    (.concat #js[#js{:severity "error"
                     :source "parinfer"
                     :from pos
                     :to (inc pos)
                     :message message}]
             extra-diagnostics)))

(defn- other-diagnostics
  [state]
  (let [others #js[]]
    (js/cm_lint.forEachDiagnostic state
                                  (fn [d _from _to]
                                    (when-not (= "parinfer" (.-source d))
                                      (.push others d))))
    others))

(defn- parinfer-view-update-listener []
  (.of (.-updateListener js/cm_view.EditorView)
       (fn [update]
         (let [last-set-enabled-effect
               (->> (mapcat (partial filter-transaction-effects set-enabled-effect)
                            (.-transactions update))
                    last)]
           (when (or (.-docChanged update)
                     last-set-enabled-effect)
             (let [state (.-state update)
                   enabled? (.field state enabled-field false)
                   error (:current (.field state parinfer-error-field))
                   parinfer-diagnostics (if (and enabled? error)
                                          (error->diagnostics (.-doc state)
                                                              (js->clj error))
                                          #js[])
                   diagnostic-tr
                   (js/cm_lint.setDiagnostics state
                                              (.concat parinfer-diagnostics
                                                       (other-diagnostics state)))]
               (.dispatch (.-view update) diagnostic-tr)))))))


;; exports

;; - set-enabled-effect
;; - switch-mode-effect

(defn switch-mode
  [editor-view mode]
  (.dispatch editor-view #js{:effects (.of switch-mode-effect mode)}))

(defn disable-parinfer
  [editor-view]
  (.dispatch editor-view #js{:effects (.of set-enabled-effect false)}))

(defn enable-parinfer
  [editor-view]
  (.dispatch editor-view #js{:effects (.of set-enabled-effect true)}))

(defn parinfer-extension []
  #js[enabled-field
      mode-field
      parinfer-error-field
      invert-parinfer-error
      (parinfer-transaction-filter)
      (parinfer-view-update-listener)])
