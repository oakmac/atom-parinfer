(ns atom-parinfer.core
  (:require
    [atom-parinfer.util :refer [by-id ends-with js-log log log-atom-changes qs
                                remove-el!]]
    [clojure.string :refer [join split-lines trim]]
    [parinfer.indent-mode :as indent-mode]
    [parinfer.paren-mode :as paren-mode]))

(declare load-file-extensions!)

;;------------------------------------------------------------------------------
;; Requires
;;------------------------------------------------------------------------------

(def fs (js/require "fs-plus"))
(def underscore (js/require "underscore"))

;;------------------------------------------------------------------------------
;; Editor State Predicates
;;------------------------------------------------------------------------------

(def autocomplete-el-selector "atom-text-editor.is-focused.autocomplete-active")
(defn- is-autocomplete-showing? []
  (if (qs autocomplete-el-selector) true false))

(defn- get-active-editor-id
  "Returns the id of the active editor.
   False if there is none"
  []
  (let [editor (js/atom.workspace.getActiveTextEditor)]
    (if (and editor (aget editor "id"))
      (aget editor "id")
      false)))

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

(defn- file-has-watched-extension?
  "Does this filename end with an extension that we are watching?"
  [filename]
  (and (string? filename)
       (some #(ends-with filename %) @file-extensions)))

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

(defn- remove-status-el! []
  (when-let [status-el (by-id status-el-id)]
    (remove-el! status-el)))

(defn- inject-status-el-into-dom! []
  (when-let [parent-el (qs "status-bar div.status-bar-right")]
    (let [status-el (js/document.createElement "div")]
      (aset status-el "className" status-el-classname)
      (aset status-el "id" status-el-id)
      (.insertBefore parent-el status-el (aget parent-el "firstChild")))))

;; TODO: make these <a> and clickable so the user can toggle state by clicking
;;       on them
(defn- link-text [state]
  (cond
    (= state :indent-mode) "Indent Mode"
    (= state :paren-mode) "Paren Mode"
    :else ""))

(defn- update-status-bar! [new-state]
  (if (= new-state :disabled)
    ;; remove the status element from the DOM
    (remove-status-el!)
    ;; else optionally inject and udpate it
    (doall
      (when-not (by-id status-el-id) (inject-status-el-into-dom!))
      (when-let [status-el (by-id status-el-id)]
        (aset status-el "innerHTML" (link-text new-state))))))

; function statusBarLink(state) {
;   var txt;
;   if (state === INDENT_MODE) { txt = 'Parinfer: Indent'; }
;   if (state === PAREN_MODE)  { txt = 'Parinfer: Paren'; }
;
;   return '<a class="inline-block">' + txt + '</a>';
; }

;;------------------------------------------------------------------------------
;; Editor States
;;------------------------------------------------------------------------------

(def editor-states
  "Keep track of all the editor tabs and their Parinfer states."
  (atom {}))

(defn- on-change-editor-states [_atm _kwd _old-states new-states]
  (let [editor-id (get-active-editor-id)
        current-editor-state (get new-states editor-id)]
    (when (and editor-id current-editor-state)
      (update-status-bar! current-editor-state))))

(add-watch editor-states :status-bar on-change-editor-states)

;;------------------------------------------------------------------------------
;; Apply Parinfer
;;------------------------------------------------------------------------------

(defn- find-start-row
  "Returns the index of the first line we need to send to Parinfer."
  [lines cursor-idx]
  0
  )

(defn- find-end-row
  "Returns the index of the last line we need to send to Parinfer."
  [lines cursor-idx]
  (dec (count lines)))

(defn- apply-parinfer* [editor mode]
  (let [current-txt (.getText editor)
        lines (split-lines current-txt)
        ;; add a newline at the end of the file if there is not one
        ;; https://github.com/oakmac/atom-parinfer/issues/12
        lines (if-not (= "" (peek lines)) (conj lines "") lines)
        cursor (.getCursorBufferPosition editor)
        selections (.getSelectedBufferRanges editor)
        start-row (find-start-row lines (aget cursor "row"))
        end-row (find-end-row lines (aget cursor "row"))
        adjusted-cursor {:cursor-line (- (aget cursor "row") start-row)
                         :cursor-x (aget cursor "column")}
        lines-to-infer (subvec lines start-row end-row)
        text-to-infer (join "\n" lines-to-infer)
        parinfer-fn (if (= mode :paren-mode)
                      paren-mode/format-text
                      indent-mode/format-text)
        result (parinfer-fn text-to-infer adjusted-cursor)
        inferred-text (if (:valid? result) (:text result) false)]
    (when (and (string? inferred-text)
               (not= inferred-text text-to-infer))
      (.setTextInBufferRange editor (array (array start-row 0) (array end-row 0))
                                    inferred-text
                                    (js-obj "undo" "skip"))
      (.setCursorBufferPosition editor cursor)
      (.setSelectedBufferRanges editor selections))))

(defn- apply-parinfer! [_change-info]
  (let [editor (js/atom.workspace.getActiveTextEditor)]
    (when (and editor
               (aget editor "id")
               (not (is-autocomplete-showing?)))
      (cond
        (= :indent-mode (get @editor-states (aget editor "id")))
        (apply-parinfer* editor :indent-mode)

        (= :paren-mode (get @editor-states (aget editor "id")))
        (apply-parinfer* editor :paren-mode)

        :else nil))))

(def debounce-interval-ms 20)
(def debounced-apply-parinfer
  (.debounce underscore apply-parinfer! debounce-interval-ms))

;;------------------------------------------------------------------------------
;; Atom Events
;;------------------------------------------------------------------------------

;; forget this editor
(defn- goodbye-editor
  "Runs when an editor tab is closed."
  [editor]
  (when (and editor (aget editor "id"))
    (swap! editor-states dissoc (aget editor "id"))))

(defn- hello-editor
  "Runs when an editor is opened."
  [editor]
  (let [editor-id (aget editor "id")
        init-parinfer? (file-has-watched-extension? (.getPath editor))]
    ;; add this editor state to our cache
    (swap! editor-states assoc editor-id :disabled)

    ;; listen to editor change events
    (.onDidChangeSelectionRange editor debounced-apply-parinfer)

    ;; add the destroy event
    (.onDidDestroy editor goodbye-editor)

    ;(when init-parinfer?
    ;  (js-log "init parinfer for this file"))
    ))

(defn- pane-changed
  "Runs when the user changes their pane focus.
   ie: switches editor tabs"
  [item]
  ;; update the status bar
  (swap! editor-states identity))

(defn- edit-file-extensions! []
  ;; open the file extension config file in a new tab
  (let [js-promise (js/atom.workspace.open file-extension-file)]
    (.then js-promise after-file-extension-tab-opened)))

(defn- disable! []
  (let [editor-id (get-active-editor-id)]
    (when editor-id
      (swap! editor-states assoc editor-id :disabled))))

(defn- toggle! []
  (let [editor-id (get-active-editor-id)
        current-state (get @editor-states editor-id)]
    (when current-state
      (if (= current-state :indent-mode)
        (swap! editor-states assoc editor-id :paren-mode)
        (swap! editor-states assoc editor-id :indent-mode)))))

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
