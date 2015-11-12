(ns atom-parinfer.core
  (:require
    [atom-parinfer.util :refer [js-log log]]
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

;;------------------------------------------------------------------------------
;; Apply Parinfer
;;------------------------------------------------------------------------------

(defn- apply-parinfer! [])

;;------------------------------------------------------------------------------
;; Atom Events
;;------------------------------------------------------------------------------

(defn- hello-editor [js-editor])

(defn- goodbye-editor [])

;;------------------------------------------------------------------------------
;; Package-required events
;;------------------------------------------------------------------------------

;; stupid variable for Atom's stupid CompositeDisposable system
(def subscriptions nil)

(defn- js-activate [_state]
  (js-log "Parinfer package activated.")

  (load-file-extensions!)

  (js/atom.workspace.observeTextEditors hello-editor))
  ;;(js/atom.workspace.onDidChangeActivePaneItem panel-changed))

(defn- js-deactivate [])
  ;; subscriptions.dispose();

;;------------------------------------------------------------------------------
;; Module export required for Atom package
;;------------------------------------------------------------------------------

(def always-nil (constantly nil))

(set! js/module.exports
  (js-obj "activate" js-activate
          "deactivate" js-deactivate
          "serialize" always-nil))

;; noop - needed for :nodejs CLJS build
(set! *main-cli-fn* always-nil)
