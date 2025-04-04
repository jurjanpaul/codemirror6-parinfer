(require '[clojure.string :as str])

(defn create-editor [doc]
  (let [start-state
        (.create
         js/cm_state.EditorState
         #js {:doc doc
              :extensions #js[(.of js/cm_language.indentUnit " ")
                              js/codemirror.basicSetup
                              (js/lang_clojure.clojure)
                              (js/codemirror6_parinfer.parinferExtension)]})]
    (js/cm_view.EditorView. #js{:state start-state
                                :parent (.getElementById js/document "editor")})))

(def initial-doc
  (str/trim "
(defn foo []
  [:bar \"baz\"])"))

(def cm-editor
  (create-editor initial-doc))

(defn switch-parinfer-mode
  [value]
  (js/codemirror6_parinfer.switchMode cm-editor value))

(defn set-parinfer-enabled
  [v]
  (if v
    (js/codemirror6_parinfer.enableParinfer cm-editor)
    (js/codemirror6_parinfer.disableParinfer cm-editor)))

(aset js/window "switch_parinfer_mode" switch-parinfer-mode)
(aset js/window "set_parinfer_enabled" set-parinfer-enabled)
