;; Supports:
;; - Parinfer smartmode
;;
;; Does not (yet) support:
;; - styling parentrails
;; - importing as library
;; - turning off parinfer or switching to other modes

(def effect-id-counter (atom 0))

(defn next-effect-id []
  (swap! effect-id-counter inc))

(defn debug [label v]
  ;(println label (js->clj v))
  v)

(def ^:private parinfer-error-effect
  (.define js/cm_state.StateEffect))

(def ^:private parinfer-error-field
  (.define js/cm_state.StateField
           #js{"create"
               (fn []
                 (debug "create"
                        {:id (next-effect-id)
                         :previous nil
                         :current nil}))
               "update"
               (fn [value, tr]
                 (if-let [a-parinfer-error-effect
                          (->> (.-effects tr)
                               (filter #(.is % parinfer-error-effect))
                               last)]
                   (.-value a-parinfer-error-effect)
                   value))}))

(def ^:private invert-parinfer-error
  (.of js/cm_commands.invertedEffects
       (fn [tr]
         (let [inverted #js[]
               parinfer-errors
               (->> (.-effects tr)
                    (filter #(.is % parinfer-error-effect)))]
           (doseq [effect parinfer-errors
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
        result (.smartMode js/parinfer
                           new-text
                           #js {:prevCursorX old-x
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

(defn- parinfer-transaction-filter []
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
         (when (.-docChanged update)
           (let [state (.-state update)
                 {:keys [_previous current] :as _parinfer-error}
                 (.field state parinfer-error-field)
                 parinfer-diagnostics (if current
                                        (error->diagnostics (.-doc state)
                                                            (js->clj current))
                                        #js[])
                 diagnostic-tr
                 (js/cm_lint.setDiagnostics state
                                            (.concat parinfer-diagnostics
                                                     (other-diagnostics state)))]
             (.dispatch (.-view update) diagnostic-tr))))))

(defn parinfer-extension []
  #js[parinfer-error-field
      invert-parinfer-error
      (parinfer-transaction-filter)
      (parinfer-view-update-listener)])
