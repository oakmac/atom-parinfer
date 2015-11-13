(ns atom-parinfer.core
  (:require
    [atom-parinfer.util :refer [by-id ends-with js-log log log-atom-changes qs
                                remove-el!]]
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

(set-validator! file-extensions set?)

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
;; Status Bar
;;------------------------------------------------------------------------------

(def status-el-id (random-uuid))
(def status-el-classname "inline-block parinfer-notification-c7a5b")

(def valid-states #{:disabled :indent-mode :paren-mode})

(def current-state
  "Holds the current state of Parinfer"
  (atom :disabled))

;; just in case :)
(set-validator! current-state #(contains? valid-states %))

(defn- remove-status-el! []
  (when-let [status-el (by-id status-el-id)]
    (remove-el! status-el)))

(defn- inject-status-el-into-dom! []
  (when-let [parent-el (qs "status-bar div.status-bar-right")]
    (let [status-el (js/document.createElement "div")]
      (aset status-el "className" status-el-classname)
      (aset status-el "id" status-el-id)
      (.insertBefore parent-el status-el (aget parent-el "firstChild")))))

(defn- update-status-bar! [_atm _kwd _old-state new-state]
  (if (= new-state :disabled)
    ;; remove the status element from the DOM
    (remove-status-el!)
    ;; else optionally inject and udpate it
    (doall
      (when-not (by-id status-el-id) (inject-status-el-into-dom!))
      (when-let [status-el (by-id status-el-id)]
        (aset status-el "innerHTML" (name new-state))))))

(add-watch current-state :status-bar update-status-bar!)

; function statusBarLink(state) {
;   var txt;
;   if (state === INDENT_MODE) { txt = 'Parinfer: Indent'; }
;   if (state === PAREN_MODE)  { txt = 'Parinfer: Paren'; }
;
;   return '<a class="inline-block">' + txt + '</a>';
; }

;;------------------------------------------------------------------------------
;; Apply Parinfer
;;------------------------------------------------------------------------------

(def editors
  "Keep track of all the editor windows and their Parinfer states."
  (atom {}))

;;(add-watch editors :editors log-atom-changes)

;; NOTE: onDidChangeCursorPosition sends an argument
;;       onDidStopChanging does not
(defn- apply-parinfer! [x]
  (let [editor (js/atom.workspace.getActiveTextEditor)]
    ;;(js-log x)
    ;;(js-log (str "parinfer " (rand-int 10)))
    ;;(js-log "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~")
    ))

;;------------------------------------------------------------------------------
;; Atom Events
;;------------------------------------------------------------------------------

;; forget this editor
(defn- goodbye-editor [editor]
  (when (and editor (aget editor "id"))
    (swap! editors dissoc (aget editor "id"))))

(def debounce-interval-ms 10)

(defn- hello-editor
  "Runs when an editor is opened."
  [editor]
  (let [editor-id (aget editor "id")
        file-path (.getPath editor)
        ;;init-parinfer? (some #(ends-with file-path %) @file-extensions)
        debounced-apply-parinfer (.debounce underscore apply-parinfer! debounce-interval-ms)
        ]
    ;; add this editor to our cache
    (swap! editors assoc editor-id {})

    ;; listen to editor change events
    (.onDidChangeSelectionRange editor debounced-apply-parinfer)

    ;; add the destroy event
    (.onDidDestroy editor goodbye-editor)

    ;(when init-parinfer?
    ;  (js-log "init parinfer for this file"))
    ))

(defn- pane-changed [item]
  ;; TODO: update the status bar
  )

(defn- edit-file-extensions! []
  ;; open the file extension config file in a new tab
  (let [js-promise (js/atom.workspace.open file-extension-file)]
    (.then js-promise after-file-extension-tab-opened)))

(defn- disable! []
  (reset! current-state :disabled))

(defn- toggle! []
  (cond
    (= @current-state :disabled) (reset! current-state :indent-mode)
    (= @current-state :indent-mode) (reset! current-state :paren-mode)
    :else (reset! current-state :indent-mode)))

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
