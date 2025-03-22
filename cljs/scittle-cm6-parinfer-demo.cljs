(require '[clojure.string :as str])

(defn create-editor [doc]
  (let [start-state
        (.create
         js/cm_state.EditorState
         #js {:doc doc
              :extensions #js[(.of js/cm_language.indentUnit " ")
                              js/codemirror.basicSetup
                              (.define (.-StreamLanguage js/cm_language)
                                       js/lang_clojure)
                              (parinfer-extension)]})]
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
  (switch-mode cm-editor value))

(defn set-parinfer-enabled
  [v]
  (if v
    (enable-parinfer cm-editor)
    (disable-parinfer cm-editor)))

(aset js/window "switch_parinfer_mode" switch-parinfer-mode)
(aset js/window "set_parinfer_enabled" set-parinfer-enabled)
