(require '[clojure.string :as str])

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
  [:bar \"baz\"])"))

(def cm-editor
  (create-editor initial-doc))
