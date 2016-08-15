(ns atom-parinfer.core
  (:require
    [atom-parinfer.util :refer [always-nil by-id js-log lines-diff log
                                one? qs remove-el! split-lines]]
    [clojure.string :refer [join trim]]
    [clojure.walk :refer [keywordize-keys]]
    [goog.array :as garray]
    [goog.functions :refer [debounce]]
    [goog.string :as gstring]))

(declare toggle-mode!)

;;------------------------------------------------------------------------------
;; JS Requires
;;------------------------------------------------------------------------------

(def fs          (js/require "fs-plus"))
(def package     (js/require "../package.json"))
(def parinfer    (js/require "parinfer"))

(def version (aget package "version"))

;;------------------------------------------------------------------------------
;; Load Config (optional)
;;------------------------------------------------------------------------------

(def default-config
  {:preview-cursor-scope? false
   :show-open-file-dialog? true})

(def config-file (str (fs.getHomeDirectory) "/.atom-parinfer-config.json"))

(def config
  (try
    (->> (.readFileSync fs config-file)
         js/JSON.parse
         js->clj
         keywordize-keys
         (merge default-config))
    (catch js/Object e default-config)))

;;------------------------------------------------------------------------------
;; Get Editor State
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
  #{".clj"  ;; Clojure
    ".cljs" ;; ClojureScript
    ".cljc" ;; Clojure + ClojureScript
    ".edn"  ;; Extensible Data Notation
    ".boot" ;; Boot build script (Clojure build framework)
    ".lfe"  ;; Lisp Flavored Erlang
    ".rkt"  ;; Racket
    ".scm"  ;; Scheme
    ".lisp" ;; Lisp
    ".lsp"  ;; Lisp
    ".cl"   ;; Common Lisp
    ".el"}) ;; Emacs Lisp

(def default-file-extension-config
  (str "# one file extension per line please :)\n"
       (join "\n" (sort default-file-extensions))
       "\n"))

(def file-extensions (atom default-file-extensions))

(set-validator! file-extensions set?)

(defn- file-has-watched-extension?
  "Does this filename end with an extension that we are watching?"
  [filename]
  (and (string? filename)
       (some #(gstring/endsWith filename %) @file-extensions)))

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
  (fs.writeFile file-extension-file default-file-extension-config))

;; NOTE: it is important that this file system read is done synchronously
;; see: https://github.com/oakmac/atom-parinfer/issues/60
(defn- load-file-extensions! []
  (let [file-text (try (fs.readFileSync file-extension-file utf8)
                       (catch js/Error _js-err false))]
    (if (string? file-text)
      ;; parse and load the file extensions
      (reset! file-extensions (parse-file-extension-config file-text))
      ;; else there was an issue loading the file, create one with the default extensions
      ;; NOTE: we do not need to set the file-extensions atom in this case
      ;;       because it is already set to the default extensions
      (write-default-file-extensions-config!))))

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
(def warning-class " parinfer-warning-932a4")

(defn- set-status-bar-warning!
  "Flag the status bar to indicate detection of unbalanced parens."
  []
  (when-let [status-el (by-id status-el-id)]
    (let [class-name (aget status-el "className")]
      (when (= -1 (.indexOf class-name warning-class))
        (aset status-el "className" (str class-name warning-class))))))

(defn- clear-status-bar-warning!
  "Remove status bar warning."
  []
  (when-let [status-el (by-id status-el-id)]
    (let [class-name (aget status-el "className")]
      (aset status-el "className" (.replace class-name warning-class "")))))

(defn- remove-status-el! []
  (when-let [status-el (by-id status-el-id)]
    (remove-el! status-el)))

(defn- click-status-bar-link [js-evt]
  (.preventDefault js-evt)     ;; prevent href event
  (.blur (by-id status-el-id)) ;; remove focus from the status bar element
  (toggle-mode!))

(defn- inject-status-el-into-dom! []
  (when-let [parent-el (qs "status-bar div.status-bar-right")]
    (let [status-el (js/document.createElement "a")]
      (aset status-el "className" status-el-classname)
      (aset status-el "href" "#")
      (aset status-el "id" status-el-id)
      (.addEventListener status-el "click" click-status-bar-link)
      (.insertBefore parent-el status-el (aget parent-el "firstChild")))))

(defn- link-text [state]
  (condp = state
    :indent-mode "Parinfer: Indent"
    :paren-mode  "Parinfer: Paren"
    :else        ""))

(defn- update-status-bar! [new-state]
  (if (= new-state :disabled)
    ;; remove the status element from the DOM
    (remove-status-el!)
    ;; else optionally inject and update it
    (doall
      (when-not (by-id status-el-id) (inject-status-el-into-dom!))
      (when-let [status-el (by-id status-el-id)]
        (aset status-el "innerHTML" (link-text new-state))))))

;;------------------------------------------------------------------------------
;; Editor States Atom
;;------------------------------------------------------------------------------

(def editor-states
  "Keep track of all the editor tabs and their Parinfer states."
  (atom {}))

;;------------------------------------------------------------------------------
;; Update Status Bar
;;------------------------------------------------------------------------------

(defn- on-change-editor-states [_atm _kwd _old-states new-states]
  (let [editor-id (get-active-editor-id)
        current-editor-state (get new-states editor-id)]
    (when (and editor-id current-editor-state)
      (update-status-bar! current-editor-state))))

(add-watch editor-states :status-bar on-change-editor-states)

;;------------------------------------------------------------------------------
;; Set Status Classes
;;------------------------------------------------------------------------------

(def indent-mode-class "indent-mode-76f60")
(def paren-mode-class "paren-mode-f2763")

(defn- set-status-class!
  "Sets the current parinfer status class on the editor element.
   Used for CSS selecting based on status."
  [js-editor mode]
  (when (and js-editor
             (aget js-editor "editorElement")
             (aget js-editor "editorElement" "classList"))
    (let [class-list (aget js-editor "editorElement" "classList")]
      (condp = mode
        :indent-mode
        (do (.add class-list indent-mode-class)
            (.remove class-list paren-mode-class))

        :paren-mode
        (do (.add class-list paren-mode-class)
            (.remove class-list indent-mode-class))

        ;; else disabled
        (.remove class-list indent-mode-class paren-mode-class)))))

(defn- toggle-status-classes! [_atm _kwd _old-states new-states]
  (garray/forEach (js/atom.workspace.getTextEditors)
    (fn [js-editor]
      (when-let [current-state (get new-states (aget js-editor "id"))]
        (set-status-class! js-editor current-state)))))

(add-watch editor-states :toggle-status-classes toggle-status-classes!)

;;------------------------------------------------------------------------------
;; Apply Parinfer
;;------------------------------------------------------------------------------

;; NOTE: this is the "parent expression" hack
;; https://github.com/oakmac/atom-parinfer/issues/9
(defn- is-parent-expression-line?
  [line]
  (and (string? line)
       (.match line #"^\([a-zA-Z]")))

(defn- find-start-row
  "Returns the index of the first line we need to send to Parinfer."
  [lines cursor-idx]
  (if
    ;; on the first line?
    (zero? cursor-idx) 0
    ;; else "look up" until we find the closest parent expression
    (loop [idx (dec cursor-idx)]
      (if (or (zero? idx)
              (is-parent-expression-line? (nth lines idx false)))
        idx
        (recur (dec idx))))))

(defn- find-end-row
  "Returns the index of the last line we need to send to Parinfer."
  [lines cursor-idx]
  (let [cursor-plus-1 (inc cursor-idx)
        cursor-plus-2 (inc cursor-plus-1)
        max-idx (dec (count lines))]
    ;; are we near the last line?
    (if (or (== max-idx cursor-idx)
            (== max-idx cursor-plus-1)
            (== max-idx cursor-plus-2))
      ;; if so, just return the max idx
      max-idx
      ;; else "look down" until we find the start of the next parent expression
      (loop [idx cursor-plus-2]
        (if (or (== idx max-idx)
                (is-parent-expression-line? (nth lines idx false)))
          idx
          (recur (inc idx)))))))

(defn- apply-parinfer* [editor mode]
  (let [current-txt (.getText editor)
        lines (split-lines current-txt)
        ;; add a newline at the end of the file if there is not one
        ;; https://github.com/oakmac/atom-parinfer/issues/12
        lines (if-not (= "" (peek lines)) (conj lines "") lines)
        cursors (.getCursorBufferPositions editor)
        multiple-cursors? (> (aget cursors "length") 1)
        cursor (.getCursorBufferPosition editor)
        selections (.getSelectedBufferRanges editor)
        selection? (not (.isEmpty (aget selections 0)))
        single-cursor? (not (or selection? multiple-cursors?))
        start-row (find-start-row lines (aget cursor "row"))
        end-row (find-end-row lines (aget cursor "row"))
        js-opts (js-obj "cursorLine" (- (aget cursor "row") start-row)
                        "cursorX" (aget cursor "column")
                        "previewCursorScope" (true? (:preview-cursor-scope? config)))
        lines-to-infer (subvec lines start-row end-row)
        text-to-infer (str (join "\n" lines-to-infer) "\n")
        js-result (if (= mode :paren-mode)
                    (parinfer.parenMode text-to-infer js-opts)
                    (parinfer.indentMode text-to-infer js-opts))
        new-cursor (js-obj "column" (aget js-result "cursorX")
                           "row" (aget cursor "row"))
        parinfer-success? (true? (aget js-result "success"))
        inferred-text (if parinfer-success? (aget js-result "text") false)]

    ;; update the text buffer
    (when (and (string? inferred-text)
               (not= inferred-text text-to-infer))
      (.setTextInBufferRange editor (array (array start-row 0) (array end-row 0))
                                    inferred-text
                                    (js-obj "undo" "skip"))

      (if single-cursor?
        ;; update the cursor position with the new cursor from Parinfer
        (.setCursorBufferPosition editor new-cursor)
        ;; else just re-apply the selection (or multiple cusors) we had before
        ;; the update and ignore the cursor result from Parinfer
        (.setSelectedBufferRanges editor selections)))

    ;; update the status bar
    (if (and (= mode :paren-mode)
             (not parinfer-success?))
      (set-status-bar-warning!)
      (clear-status-bar-warning!))))

(defn- apply-parinfer! [_cursor-change-info]
  (let [editor (js/atom.workspace.getActiveTextEditor)]
    (when (and editor
               (aget editor "id")
               (not (is-autocomplete-showing?)))
      (condp = (get @editor-states (aget editor "id"))
        :indent-mode (apply-parinfer* editor :indent-mode)
        :paren-mode  (apply-parinfer* editor :paren-mode)
        nil))))

;; NOTE: 20ms seems to work well for the debounce interval.
;; I don't notice any lag when typing on my machine and the result displays fast
;; enough that it doesn't feel "delayed".
;; Feel free to play around with it on your machine if that is not the case.
(def debounce-interval-ms 20)
(def debounced-apply-parinfer
  (debounce apply-parinfer! debounce-interval-ms))

;;------------------------------------------------------------------------------
;; Atom Events
;;------------------------------------------------------------------------------

(defn- goodbye-editor
  "Runs when an editor tab is closed."
  [editor]
  (when (and editor (aget editor "id"))
    (swap! editor-states dissoc (aget editor "id"))))

(defn- confirm-paren-mode-msg [filename num-lines-changed]
  (let [l (if (one? num-lines-changed) "line" "lines")]
    (str "Parinfer needs to make some changes to \"" filename "\" before enabling Indent Mode. "
         "These changes will only affect whitespace and indentation; the structure of the file will be unchanged."
         "\n\n"
         num-lines-changed " " l " will be affected."
         "\n\n"
         "Would you like Parinfer to modify the file? (recommended)")))

(defn- paren-mode-failed-msg [filename]
  (str "Parinfer was unable to parse \"" filename "\"."
       "\n\n"
       "It is likely that this file has unbalanced parentheses and will not compile."
       "\n\n"
       "Parinfer will enter Paren Mode so you may fix the problem. "
       "Press Ctrl + ( to switch to Indent Mode once the file is balanced."))

(defn- hello-editor
  "Runs when an editor is opened."
  [editor]
  (let [editor-id (aget editor "id")
        init-parinfer? (file-has-watched-extension? (.getPath editor))
        current-file (.getTitle editor)]
    ;; add this editor state to our atom
    (swap! editor-states assoc editor-id :disabled)

    ;; listen to editor change events
    (.onDidChangeSelectionRange editor debounced-apply-parinfer)

    ;; add the destroy event
    (.onDidDestroy editor goodbye-editor)

    ;; run Paren Mode on the file if we recognize the extension.
    (when init-parinfer?
      (let [show-open-file-dialog? (true? (:show-open-file-dialog? config))
            current-text (.getText editor)
            js-paren-mode-result (parinfer.parenMode current-text)
            paren-mode-succeeded? (true? (aget js-paren-mode-result "success"))
            paren-mode-text (aget js-paren-mode-result "text")
            text-delta (lines-diff current-text paren-mode-text)
            paren-mode-changed-the-file? (and paren-mode-succeeded?
                                              (not (zero? (:diff text-delta))))]
        (cond
          ;; Paren Mode failed and they want to see the dialog.
          ;; Inform them and then drop into Paren Mode.
          (and (not paren-mode-succeeded?) show-open-file-dialog?)
          (js/atom.confirm
            (js-obj "message" (str current-file " has unbalanced parens")
                    "detailedMessage" (paren-mode-failed-msg current-file)
                    "buttons" (js-obj "Ok" #(swap! editor-states assoc editor-id :paren-mode))))

          ;; Paren Mode failed and they do not want to see the dialog.
          ;; Drop them into Paren Mode silently to fix the problem.
          (and (not paren-mode-succeeded?) (not show-open-file-dialog?))
          (swap! editor-states assoc editor-id :paren-mode)

          ;; Paren Mode changed the file and they want to see the dialog.
          ;; Inform them of the change and then drop into Indent Mode if they click "Yes".
          (and paren-mode-changed-the-file? show-open-file-dialog?)
          (js/atom.confirm
            (js-obj "message" (str "Parinfer will change " current-file)
                    "detailedMessage" (confirm-paren-mode-msg current-file (:diff text-delta))
                    "buttons" (js-obj "Yes" (fn []
                                              (.setText editor paren-mode-text)
                                              (swap! editor-states assoc editor-id :indent-mode))
                                      ;; do nothing if they click "No"
                                      "No" always-nil)))

          ;; Paren Mode changed the file and they do not want to see the dialog.
          ;; Update the file and drop them into Indent Mode silently.
          (and paren-mode-changed-the-file? (not show-open-file-dialog?))
          (do (.setText editor paren-mode-text)
              (swap! editor-states assoc editor-id :indent-mode))

          ;; Paren Mode succeeded and the file was unchanged.
          ;; Drop them into Indent Mode silently.
          ;; NOTE: this is the most likely case for someone using Parinfer regularly
          :else
          (swap! editor-states assoc editor-id :indent-mode))))))

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

(defn- toggle-mode! []
  (let [editor-id (get-active-editor-id)
        current-state (get @editor-states editor-id)]
    (when current-state
      (if (= current-state :indent-mode)
        (swap! editor-states assoc editor-id :paren-mode)
        (swap! editor-states assoc editor-id :indent-mode))
      ;; run parinfer in their new mode
      (debounced-apply-parinfer))))

;;------------------------------------------------------------------------------
;; Package-required events
;;------------------------------------------------------------------------------

(defn- activate [_state]
  (js-log (str "atom-parinfer v" version " activated"))

  (load-file-extensions!)

  (js/atom.workspace.observeTextEditors hello-editor)
  (js/atom.workspace.onDidChangeActivePaneItem pane-changed)

  ;; add package events
  (js/atom.commands.add "atom-workspace"
    (js-obj "parinfer:edit-file-extensions" edit-file-extensions!
            "parinfer:disable" disable!
            "parinfer:toggle-mode" toggle-mode!))

  ;; Sometimes the editor events can all load before Atom catches up with the DOM
  ;; resulting in an initial empty status bar.
  ;; These calls help with that and it doesn't hurt anything to call them extra.
  ;; TODO: figure out if there is an actual Atom event instead of this hackery
  (js/setTimeout pane-changed 100)
  (js/setTimeout pane-changed 500)
  (js/setTimeout pane-changed 1000)
  (js/setTimeout pane-changed 2000)
  (js/setTimeout pane-changed 5000))

;;------------------------------------------------------------------------------
;; Module Export (required for Atom package)
;;------------------------------------------------------------------------------

(set! js/module.exports
  (js-obj "activate" activate
          "deactivate" always-nil
          "serialize" always-nil))

;; noop - needed for :nodejs CLJS build
(set! *main-cli-fn* always-nil)
