(ns atom-parinfer.core
  (:require
    [atom-parinfer.util :refer [by-id ends-with js-log log log-atom-changes qs]]
    [clojure.string :refer [join split-lines trim]]))

(declare load-file-extensions!)

;;------------------------------------------------------------------------------
;; Requires
;;------------------------------------------------------------------------------

(def fs (js/require "fs-plus"))
(def underscore (js/require "underscore"))

;;------------------------------------------------------------------------------
;; File Extensions Config
;;------------------------------------------------------------------------------

(def file-extension-file (str (fs.getHomeDirectory) "/.parinfer-file-extensions.txt"))
(def utf8 "utf8")

(def default-file-extensions
  #{".clj" ".cljs" ".cljc" ;; Clojure
    ".lfe"})               ;; Lisp Flavored Erlang

(def default-file-extension-config
  (str "# one file extension per line please :)\n"
       (join "\n" (sort default-file-extensions))))

(def file-extensions (atom default-file-extensions))

;; (add-watch file-extensions :change log-atom-changes)

(defn- comment-line? [l]
  (= (.charAt l 0) "#"))

(defn- parse-file-extension-line [v line]
  (let [line (trim line)]
    (cond
      ;; skip empty lines
      (empty? line) v

      ;; skip comments
      (comment-line? line) v

      ;; else add it
      :else (conj v line))))

(defn- parse-file-extension-config [txt]
  (let [lines (split-lines txt)]
    (reduce parse-file-extension-line #{} lines)))

(defn- write-default-file-extensions-config! []
  (fs.writeFile file-extension-file default-file-extension-config
    (fn [js-err]
      (if js-err
        () ;; TODO: handle error here
        ;; load the new file extensions
        (load-file-extensions!)))))

(defn- load-file-extensions-callback [js-err txt]
  (if js-err
    (write-default-file-extensions-config!)
    (reset! file-extensions (parse-file-extension-config txt))))

(defn- load-file-extensions! []
  (fs.readFile file-extension-file utf8 load-file-extensions-callback))

;; reload their file extensions when the editor is saved
(defn- after-file-extension-tab-opened [editor]
  (.onDidSave editor
    (fn []
      (reset! file-extensions (parse-file-extension-config (.getText editor))))))

;;------------------------------------------------------------------------------
;; Apply Parinfer
;;------------------------------------------------------------------------------

(def editors
  "Keep track of all the editor windows and their Parinfer states."
  (atom {}))

;;(add-watch editors :editors log-atom-changes)

(defn- apply-parinfer! [])

;;------------------------------------------------------------------------------
;; Atom Events
;;------------------------------------------------------------------------------

;; forget this editor
(defn- goodbye-editor [editor]
  (when (and editor (aget editor "id"))
    (swap! editors dissoc (aget editor "id"))))

(defn- hello-editor
  "Runs when an editor is opened."
  [editor]
  (let [editor-id (aget editor "id")
        file-path (.getPath editor)
        init-parinfer? (some #(ends-with file-path %) @file-extensions)]
    ;; add this editor to our cache
    (swap! editors assoc editor-id {})

    ;; add the destroy event
    (.onDidDestroy editor goodbye-editor)

    ;(when init-parinfer?
    ;  (js-log "init parinfer for this file"))
    ))

(defn- pane-changed [item]
  ;;(js-log "TODO: pane-changed!")
  ;; TODO: update the status bar
  )

(defn- edit-file-extensions! []
  ;; open the file extension config file in a new tab
  (let [js-promise (js/atom.workspace.open file-extension-file)]
    (.then js-promise after-file-extension-tab-opened)))

(defn- disable! []
  (js-log "TODO: disable parinfer for this editor"))

(defn- toggle! []
  (js-log "TODO: toggle parinfer for this editor"))

;;------------------------------------------------------------------------------
;; Package-required events
;;------------------------------------------------------------------------------

(defn- activate [_state]
  (js-log "Parinfer package activated.")

  (load-file-extensions!)

  (js/atom.workspace.observeTextEditors hello-editor)
  (js/atom.workspace.onDidChangeActivePaneItem pane-changed)

  ;; add package events
  (js/atom.commands.add "atom-workspace"
    (js-obj "parinfer:editFileExtensions" edit-file-extensions!
            "parinfer:disable" disable!
            "parinfer:toggleMode" toggle!)))

(defn- deactivate [])
  ;; subscriptions.dispose();

;;------------------------------------------------------------------------------
;; Module export required for Atom package
;;------------------------------------------------------------------------------

(def always-nil (constantly nil))

(set! js/module.exports
  (js-obj "activate" activate
          "deactivate" deactivate
          "serialize" always-nil))

;; noop - needed for :nodejs CLJS build
(set! *main-cli-fn* always-nil)
