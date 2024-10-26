(require '[clojure.string :as str])

;; Supports:
;; - Parinfer smartmode
;;
;; Does not (yet) support:
;; - error highlighting
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
  ;; (println "line from" (.-from (.line doc (inc y))))
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
      {} ; TODO: pass and show error somehow
      (let [cursorX (.-cursorX result)
            ;; _ (println "pi x" cursorX)
            cursorLine (.-cursorLine result)
            ;; _ (println "pi y" cursorLine)
            changes (parinfer-result->cm-changes result new-text)
            ;; _ (println "changes" changes)
            new-transaction (.update new-state
                                     (clj->js {:changes changes
                                               :filter false}))
            ;; new-new-text (.toString (.-doc (.-state new-transaction)))
            ;; _ (println "new-new-text:" (str "\n" new-new-text))
            ;; new-doc is not exactly the final doc!?
            new-pos (parinfer-yx->cm-pos (.-doc (.-state new-transaction)) cursorLine cursorX)  ;; Offsets in this selection should refer to the document as it is after the transaction!?!?!
            _ (println "new-pos" new-pos)]
        {:changes changes
         :selection (.cursor js/cm_state.EditorSelection new-pos)
         :sequential true}))))

(defn parinfer-plugin []
  (.of (.-transactionFilter js/cm_state.EditorState)
       (fn [transaction]
        ;;  (println "transactionFilter parinfer-plugin current pos" (.-from (.-main (.-selection (.-state transaction)))))
         (if (.-docChanged transaction)
           (let [parinfer-changes (apply-parinfer-smart-with-diff transaction)]
             (if (seq parinfer-changes)
               #js[transaction
                   (clj->js parinfer-changes)]
               transaction))
           transaction))))

(defn create-editor [doc]
  (let [start-state
        (.create
         js/cm_state.EditorState
         #js {:doc doc
              :extensions #js [;(.of js/cm_state.EditorState.tabSize 1)
                               (.of js/cm_language.indentUnit " ")
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
