;; Does not (yet) support:
;; - styling parentrails

(def effect-id-counter (atom 0))

(defn next-effect-id []
  (swap! effect-id-counter inc))

(defn debug [label v]
  (println label (js->clj v))
  v)

(defn filter-transaction-effects
  [effect-type tr]
  (->> (.-effects tr)
       (filter #(.is % effect-type))))

(def set-config-effect
  (.define js/cm_state.StateEffect))

(def ^:private config-field
  (.define js/cm_state.StateField
           #js{"create"
               (fn []
                 {:enabled? true
                  :mode "smart"})
               "update"
               (fn [value, tr]
                 (if-let [a-set-config-effect
                          (->> (filter-transaction-effects set-config-effect tr) last)]
                  (merge value (debug "new config" (.-value a-set-config-effect)))
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
                         {:id (next-effect-id)
                          :inverts-id id
                          :previous current
                          :current previous})))
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
           {:id (next-effect-id)
            :previous existing-parinfer-error
            :current parinfer-error}))))

(defn parinfer
   [mode text opts]
   (case mode
     "smart" (.smartMode js/parinfer text opts)
     "indent" (.indentMode js/parinfer text opts)
     "paren" (.parenMode js/parinfer text opts)))

(defn- get-config
  [state k default]
  (get (.field state config-field false) k default))

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
        result (parinfer (get-config start-state :mode "smart")
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

(defn- enabled?
  [state]
  (get-config state :enabled? true))

(defn- parinfer-transaction-filter
  [initial-config]
  (.of (.-transactionFilter js/cm_state.EditorState)
       (fn [transaction]
         (let [a-set-config-effect
               (->> (filter-transaction-effects set-config-effect transaction) last)]
           (if (or (and (enabled? (.-startState transaction))
                        (.-docChanged transaction))
                   (and a-set-config-effect (:enabled? (.-value a-set-config-effect))))
             (let [{:keys [effects changes] :as parinfer-changes}
                   (apply-parinfer-smart-with-diff transaction)]
               (if (or effects (seq (js->clj changes)))
                 #js[transaction
                     (clj->js parinfer-changes)]
                 transaction))
             (if (not (get-config (.-state transaction) :initialized? false))
               (do
                 (println "initializing parinfer" initial-config)
                 #js[transaction
                     #js{:effects (.of set-config-effect
                                       (assoc initial-config :initialized? true))}])
               transaction))))))

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
         (let [last-set-config-effect
               (->> (mapcat (partial filter-transaction-effects set-config-effect)
                            (.-transactions update))
                    last)]
           (when (or (.-docChanged update)
                     last-set-config-effect)
             (let [state (.-state update)
                   error (:current (.field state parinfer-error-field))
                   parinfer-diagnostics (if (and (enabled? state) error)
                                          (error->diagnostics (.-doc state)
                                                              (js->clj error))
                                          #js[])
                   diagnostic-tr
                   (js/cm_lint.setDiagnostics state
                                              (.concat parinfer-diagnostics
                                                       (other-diagnostics state)))]
               (.dispatch (.-view update) diagnostic-tr)))))))

;; exports
;; - config-field
;; - set-config-effect

(defn configure-parinfer
  [editor-view config]
  (.dispatch editor-view #js{:effects (.of set-config-effect config)}))

(defn switch-mode
  [editor-view mode]
  (configure-parinfer editor-view {:mode mode}))

(defn disable-parinfer
  [editor-view]
  (configure-parinfer editor-view {:enabled? false}))

(defn enable-parinfer
  [editor-view]
  (configure-parinfer editor-view {:enabled? true}))

(defn parinfer-extension
 ([]
  (parinfer-extension {}))
 ([initial-config]
  #js[config-field
      parinfer-error-field
      invert-parinfer-error
      (parinfer-transaction-filter initial-config)
      (parinfer-view-update-listener)]))
