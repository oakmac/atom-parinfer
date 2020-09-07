(ns atom-parinfer.core
  (:require
   [atom-parinfer.util :as util]
   [clojure.edn :as edn]
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.walk :as walk]
   [goog.array :as garray]
   [goog.functions :as gfunctions]
   [goog.string :as gstring]
   [oops.core :refer [ocall oget oset!]]))

(declare
  on-change-cursor-position
  on-did-change-text
  refresh-all-change-events!
  toggle-mode!)

;; -----------------------------------------------------------------------------
;; JS Requires

(def path     (js/require "path"))
(def fs       (js/require "fs-plus"))
(def parinfer (js/require "@chrisoakman/parinfer"))
(def package  (js/require "../package.json"))

(def version (oget package "version"))

;; -----------------------------------------------------------------------------
;; Config

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
    ".el"   ;; Emacs Lisp
    ".fnl"  ;; Fennel
    ".janet"}) ;; Janet


(def file-ext->comment-chars
  "some lisps use a different character for comment than semicolon (;)
  this is a mapping of file extension --> commentChars"
  {".janet" ["#"]})


(def config-schema
  {:use-smart-mode?
   {:order 1
    :title "Smart Mode (recommended)"
    :description "It's like Indent Mode, but tries to preserve structure too."
    :type "boolean"
    :default true}

   :force-balance?
   {:order 2
    :title "Force Balance (recommended)"
    :description
    (str "Parens are auto-balanced most of the time, but sometimes there are edge cases with unmatched closing parens on a single line.<br />"
         "<table>"
         "<tr><td>__ON__&nbsp;</td><td>- Always stay 100% balanced regardless of previous structure:</td><td><img src=\"atom://parinfer/images/forcebalance-on.gif\" height=\"21px\"></td></tr>"
         "<tr><td>__OFF__&nbsp;</td><td>- Allow some imbalance to prevent loss of structure:</td><td><img src=\"atom://parinfer/images/forcebalance-off.gif\" height=\"21px\"></td></tr>"
         "</table>")
    :type "boolean"
    :default true}

   :show-open-file-dialog?
   {:order 3
    :title "Warn when opening bad file (recommended)"
    :description "Show a dialog when opening a file with unbalanced parentheses or incorrect indentation."
    :type "boolean"
    :default true}

   :file-extensions
   {:order 4
    :title "File Extensions"
    :description "Auto-enable Parinfer for these file extensions."
    :default default-file-extensions
    :type "array"
    :items {:type "string"}}

   :comment-chars
   {:order 5
    :title "Comment Characters"
    :description (str "Some lisps use a character other than semicolon (`;`) for comments. "
                      "Override the default comment characters here with an EDN map of file-extension --> comment characters. "
                      "Most common defaults are already provided, including `#` for Janet."
                      "<br><br>"
                      "Example: `{\".clj\" [\";\"], \".janet\" [\"#\"]}`"
                      "<br>")
    :default ""
    :type "string"}})


(def default-config
  {:use-smart-mode? true
   :force-balance? true
   :show-open-file-dialog? true
   :file-extensions default-file-extensions
   :comment-chars nil})


(defn- config-key
  "Config keys must be namespaced."
  [k]
  (str "parinfer." (name k)))


(def config
  "The user's current choices for each config."
  (atom default-config))


(defn- valid-comment-chars?
  "FIXME: write me"
  [m]
  (and
    (map? m)))


(defn- valid-config-values?
  "sanity-check the config values"
  [cfg]
  (and
    (map? cfg)
    (boolean? (:use-smart-mode? cfg))
    (boolean? (:force-balance? cfg))
    (boolean? (:show-open-file-dialog? cfg))
    (set? (:file-extensions cfg))
    (every? string? (:file-extensions cfg))
    (valid-comment-chars? (:comment-chars cfg))))

(set-validator! config valid-config-values?)

(defn- warn [msg]
  ;; FIXME: write this
  (js/console.warn msg))

(defmulti set-config-value!
  (fn [config-key _new-value]
    (if (contains? #{:use-smart-mode? :force-balance? :show-open-file-dialog?} config-key)
      :boolean
      config-key)))

(defmethod set-config-value! :boolean
  [cfg-key new-value]
  (swap! config assoc cfg-key (boolean new-value)))

(defmethod set-config-value! :file-extensions
  [cfg-key new-value]
  (swap! config assoc cfg-key (-> new-value js->clj set)))

(defmethod set-config-value! :comment-chars
  [cfg-key new-value]
  (let [overrides-map (try
                        (edn/read-string new-value)
                        (catch js/Object _err nil))]
    (cond
      ;; FIXME: bad EDN
      ;; FIXME: bad format
      (valid-comment-chars? overrides-map) (swap! config assoc cfg-key overrides-map)
      :else nil)))

(defmethod set-config-value! :default
  [cfg-key _new-value]
  (warn (str "Unable to set value for unknown config key:" cfg-key)))


(defn- observe-config!
  "Initialize config values and keep up with changes made from the UI."
  []
  (doseq [k (keys config-schema)]
    (ocall (oget js/atom "config") "observe" (config-key k)
           (fn [new-value]
             (set-config-value! k new-value))))
  (ocall (oget js/atom "config") "onDidChange" (config-key :use-smart-mode?)
         (fn [] (refresh-all-change-events!))))


(defn- file-has-watched-extension?
  "Does this filename end with an extension that we are watching?"
  [filename]
  (and (string? filename)
       (some #(gstring/endsWith filename %) (:file-extensions @config))))


(defn- get-comment-chars
  "returns the commentChars option for a file extension
  nil if there is no commentChars option"
  [file-ext]
  (or
    (get (:comment-chars @config) file-ext)
    (get file-ext->comment-chars file-ext)))


;; -----------------------------------------------------------------------------
;; Old Config

(def old-config-file (str (ocall fs "getHomeDirectory") "/.atom-parinfer-config.json"))
(def old-file-extension-file (str (ocall fs "getHomeDirectory") "/.parinfer-file-extensions.txt"))


(defn- read-old-config []
  (try
    (->> (ocall fs "readFileSync" old-config-file "utf8")
         js/JSON.parse
         js->clj
         walk/keywordize-keys)
    (catch js/Object _err nil)))


(defn- read-old-file-extensions []
  (try
    (let [txt (ocall fs "readFileSync" old-file-extension-file "utf8")
          lines (util/split-lines txt)]
      (reduce
        (fn [v line]
          (let [trimmed-line (str/trim line)]
            (cond
              (str/blank? trimmed-line) v             ;; skip empty lines
              (gstring/startsWith trimmed-line "#") v ;; skip comments
              :else (conj v trimmed-line))))          ;; else add it
        []
        lines))
    (catch js/Object _err nil)))


(defn- handle-old-config-files!
  "Get data from old config files if still present, then delete them."
  []
  ;; old config
  (when-let [old-config (read-old-config)]
    (doseq [[k v] old-config]
      (when (config-schema k)
        (ocall (oget js/atom "config") "set" (config-key k) v)))
    (ocall fs "unlink" old-config-file))

  ;; old file extensions
  (when-let [old-file-extensions (clj->js (read-old-file-extensions))]
    (ocall (oget js/atom "config") "set" (config-key :file-extensions) old-file-extensions)
    (ocall fs "unlink" old-file-extension-file)))


;; -----------------------------------------------------------------------------
;; Editor States Atom

(def editor-states
  "Keep track of all the editor tabs and their Parinfer states."
  (atom {}))


;; -----------------------------------------------------------------------------
;; Get Editor State

(def autocomplete-el-selector "atom-text-editor.is-focused.autocomplete-active")


(defn- get-active-editor
  "Returns the active editor if it has an ID"
  []
  (when-let [js-editor (ocall js/atom "workspace.getActiveTextEditor")]
    (when-let [id (oget js-editor "id")]
      {:editor-id id
       :editor-state (get @editor-states id)
       :js-editor js-editor})))


;; -----------------------------------------------------------------------------
;; Status Bar

(def status-el-id (str (random-uuid)))
(def status-el-classname "inline-block parinfer-notification-c7a5b")
(def warning-class "parinfer-warning-932a4")


(defn- set-status-bar-warning!
  "Flag the status bar to indicate detection of unbalanced parens."
  []
  (when-let [status-el (util/by-id status-el-id)]
    (ocall (oget status-el "classList") "add" warning-class)))


(defn- clear-status-bar-warning!
  "Remove status bar warning."
  []
  (when-let [status-el (util/by-id status-el-id)]
    (ocall (oget status-el "classList") "remove" warning-class)))


(defn- remove-status-el! []
  (when-let [status-el (util/by-id status-el-id)]
    (util/remove-el! status-el)))


(defn- click-status-bar-link [js-evt]
  (ocall js-evt "preventDefault") ;; prevent href event
  (ocall (util/by-id status-el-id) "blur") ;; remove focus from the status bar element
  (toggle-mode!))


(defn- inject-status-el-into-dom! []
  (when-let [parent-el (util/qs "status-bar div.status-bar-right")]
    (let [status-el (ocall js/document "createElement" "a")]
      (oset! status-el "className" status-el-classname)
      (oset! status-el "href" "#")
      (oset! status-el "id" status-el-id)
      (ocall status-el "addEventListener" "click" click-status-bar-link)
      (ocall parent-el "insertBefore" status-el (oget parent-el "firstChild")))))


(defn- link-text [state]
  (case state
    :indent-mode (if (:use-smart-mode? @config)
                   "Parinfer: Smart"
                   "Parinfer: Indent")
    :paren-mode  "Parinfer: Paren"
    ""))


(defn- update-status-bar! [new-state]
  (if (= new-state :disabled)
    ;; remove the status element from the DOM
    (remove-status-el!)
    ;; else optionally inject and update it
    (do
      (when-not (util/by-id status-el-id) (inject-status-el-into-dom!))
      (when-let [status-el (util/by-id status-el-id)]
        (oset! status-el "innerHTML" (link-text new-state))))))


;; -----------------------------------------------------------------------------
;; Update Status Bar

(defn- toggle-status-bar! [_atm _kwd _old-states new-states]
  (let [{:keys [editor-id]} (get-active-editor)
        current-editor-state (get new-states editor-id)]
    (when (and editor-id current-editor-state)
      (update-status-bar! current-editor-state))))


(add-watch editor-states :status-bar toggle-status-bar!)


;; -----------------------------------------------------------------------------
;; Set Status Classes

(def indent-mode-class "indent-mode-76f60")
(def paren-mode-class "paren-mode-f2763")


(defn- set-status-class!
  "Sets the current parinfer status class on the editor element.
   Used for CSS selecting based on status."
  [js-editor mode]
  (let [js-editor-element (or (oget js-editor "element")
                              (oget js-editor "editorElement"))
        js-classlist (when js-editor-element (oget js-editor-element "classList"))]
    (when js-classlist
      (case mode
        :indent-mode
        (do (ocall js-classlist "add" indent-mode-class)
            (ocall js-classlist "remove" paren-mode-class))

        :paren-mode
        (do (ocall js-classlist "add" paren-mode-class)
            (ocall js-classlist "remove" indent-mode-class))

        ;; else disabled
        (ocall js-classlist "remove" indent-mode-class paren-mode-class)))))


(defn- toggle-status-classes! [_atm _kwd _old-states new-states]
  (garray/forEach (ocall js/atom "workspace.getTextEditors")
    (fn [js-editor]
      (when-let [current-state (get new-states (oget js-editor "id"))]
        (set-status-class! js-editor current-state)))))


(add-watch editor-states :toggle-status-classes toggle-status-classes!)


;; -----------------------------------------------------------------------------
;; Error Markers

(def error-marker-class "parinfer-error-marker-778ea")
(def error-marker-ids (atom #{}))

(defn add-error-marker
  [js-editor start-row js-error]
  (let [row (+ (oget js-error "lineNo") start-row)
        col (oget js-error "x")
        range (array (array row col)
                     (array row (inc col)))
        marker (ocall js-editor "markBufferRange" range
                 (js-obj "invalidate" "never"))
        decorator (ocall js-editor "decorateMarker" marker
                    (js-obj "type" "highlight"
                            "class" error-marker-class))]
    (swap! error-marker-ids conj (oget marker "id"))))


(defn marker-inside?
  [marker start-row end-row]
  (let [marker-row (oget (ocall marker "getBufferRange") "start" "row")]
    (<= start-row marker-row end-row)))


(defn get-error-markers
  [js-editor start-row end-row]
  (->> (ocall js-editor "findMarkers")
       (filter #(@error-marker-ids (oget % "id")))
       (filter #(marker-inside? % start-row end-row))))


(defn clear-error-markers
  [js-editor start-row end-row _js-error]
  (let [markers (get-error-markers js-editor start-row end-row)
        ids (set (map #(oget % "id") markers))]
    (run! #(ocall % "destroy") markers)
    (swap! error-marker-ids set/difference ids)))


;; -----------------------------------------------------------------------------
;; Apply Parinfer

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


(defn- atom-changes->parinfer-changes
  "Convert Atom's changes object into the format that Parinfer expects."
  [js-atom-changes start-row]
  (if (and js-atom-changes
           (array? (oget js-atom-changes "?changes")))
    (ocall (oget js-atom-changes "changes") "map"
      (fn [ch]
        (js-obj
          "oldText" (oget ch "oldText")
          "newText" (oget ch "newText")
          "lineNo" (- (oget ch "oldRange.start.row") start-row)
          "x" (oget ch "oldRange.start.column"))))
    nil))


(defn extname
  "get file extension for file"
  [f]
  (ocall path "extname" f))


(def previous-cursor (atom nil))
(def monitor-cursor? (atom true))
(def previous-tabstops (atom nil))


(defn- apply-parinfer2 [js-editor mode js-atom-changes]
  (let [file (ocall js-editor "getPath")
        file-extension (extname file)
        current-txt (ocall js-editor "getText")
        lines (util/split-lines current-txt)
        ;; add a newline at the end of the file if there is not one
        ;; https://github.com/oakmac/atom-parinfer/issues/12
        lines (if-not (= "" (peek lines)) (conj lines "") lines)
        cursors (ocall js-editor "getCursorBufferPositions")
        multiple-cursors? (> (oget cursors "length") 1)
        cursor (ocall js-editor "getCursorBufferPosition")
        js-selections (ocall js-editor "getSelectedBufferRanges")
        selection? (not (ocall (aget js-selections 0) "isEmpty"))
        single-cursor? (not (or selection? multiple-cursors?))
        start-row (find-start-row lines (oget cursor "row"))
        end-row (find-end-row lines (oget cursor "row"))

        adjusted-cursor-line (- (oget cursor "row") start-row)
        cursor-x (oget cursor "column")

        adjusted-selection-line (when selection?
                                  (- (oget js-selections "0" "start" "row") start-row))

        js-opts (js-obj "cursorLine" adjusted-cursor-line
                        "cursorX" cursor-x
                        "prevCursorLine" (:cursor-line @previous-cursor nil)
                        "prevCursorX" (:cursor-x @previous-cursor nil)
                        "selectionStartLine" adjusted-selection-line

                        ;; TODO: handle selectionStartLine
                        ;; "selectionStartLine" 0
                        "changes" (atom-changes->parinfer-changes js-atom-changes start-row)

                        "forceBalance" (:force-balance? @config)
                        "partialResult" false)

        ;; add commentChars if needed
        js-opts (if-let [comment-chars (get-comment-chars file-extension)]
                  (oset! js-opts "commentChars" (clj->js comment-chars))
                  js-opts)

        ;; save this cursor information for the next iteration
        ;; TODO: we need to clear the previous-cursor atom when changing parent expressions
        _ (reset! previous-cursor {:cursor-line adjusted-cursor-line
                                   :cursor-x cursor-x})

        lines-to-infer (subvec lines start-row end-row)
        text-to-infer (str (str/join "\n" lines-to-infer) "\n")
        js-result (if (= mode :paren-mode)
                    (ocall parinfer "parenMode" text-to-infer js-opts)
                    (if (:use-smart-mode? @config)
                      (ocall parinfer "smartMode" text-to-infer js-opts)
                      (ocall parinfer "indentMode" text-to-infer js-opts)))
        parinfer-success? (true? (oget js-result "success"))
        js-error (oget js-result "?error")
        ;; TODO: save tabStops here
        new-cursor (if parinfer-success?
                     (js-obj "column" (oget js-result "cursorX")
                             "row" (+ (oget js-result "cursorLine") start-row))
                     nil)
        inferred-text (if parinfer-success? (oget js-result "text") nil)]

    (reset! previous-tabstops (oget js-result "?tabStops"))

    ;; update error markers
    (clear-error-markers js-editor start-row end-row (oget js-result "?error"))
    (when js-error
      (add-error-marker js-editor start-row js-error)
      (when-let [extra (oget js-error "extra")]
        (add-error-marker js-editor start-row extra)))

    ;; update the text buffer
    (when (and (string? inferred-text)
               (not= inferred-text text-to-infer))
      (reset! monitor-cursor? false)
      (let [js-buffer (ocall js-editor "getBuffer")]
        (ocall js-editor "setTextInBufferRange" (array (array start-row 0) (array end-row 0))
                                                inferred-text)
        (ocall js-buffer "groupLastChanges"))

      (if (and single-cursor? new-cursor)
        ;; update the cursor position with the new cursor from Parinfer
        (ocall js-editor "setCursorBufferPosition" new-cursor)
        ;; else just re-apply the selection (or multiple cusors) we had before
        ;; the update and ignore the cursor result from Parinfer
        (ocall js-editor "setSelectedBufferRanges" js-selections))
      (js/setTimeout #(reset! monitor-cursor? true) 0))

    ;; update the status bar
    (if (not parinfer-success?)
      (set-status-bar-warning!)
      (clear-status-bar-warning!))))


(defn- apply-parinfer! [js-changes]
  (when-let [{:keys [js-editor editor-state]} (get-active-editor)]
    (let [;; When smart-mode is false, this function is debounced from
          ;; onDidChangeSelectionRange which we will just identify by a non-nil
          ;; `.selection` property.
          selection-debounce? (and js-changes
                                   (oget js-changes "?selection"))

          ;; js-changes is null when the cursor caused this event
          cursor-change? (nil? js-changes)

          ;; When we apply parinfer, it kicks off a secondary change, but the
          ;; change is empty for some reason.  We don't want to process a secondary
          ;; change, and we don't want to process an empty change anyway.
          empty-change? (and (not cursor-change?)
                             (nil? (first (oget js-changes "?changes"))))

          should-apply? (or selection-debounce?
                            cursor-change?
                            (not empty-change?))]

      (when should-apply?
        (case editor-state
          :indent-mode (apply-parinfer2 js-editor :indent-mode js-changes)
          :paren-mode  (apply-parinfer2 js-editor :paren-mode nil)
          nil)))))


;; NOTE: 20ms seems to work well for the debounce interval.
;; I don't notice any lag when typing on my machine and the result displays fast
;; enough that it doesn't feel "delayed".
;; Feel free to play around with it on your machine if that is not the case.
(def debounce-interval-ms 20)
(def debounced-apply-parinfer (gfunctions/debounce apply-parinfer! debounce-interval-ms))

;; -----------------------------------------------------------------------------
;; Tab Stops

(def paren->spaces
  "We insert extra tab stops according to the type of open-paren."
  {"(" 2
   "{" 1
   "[" 1})


(def default-tab-stops
  "Default tab stops (x-locations) for fallback."
  [0 2])


(defn expand-tab-stops
  "Expand on Parinfer's tabStops (at open-parens) for our indentation style.
  For example a single tabStop has the following information about an open-paren,
  and we choose our desired tabStops from it.

    (foo bar
    | |  |
    | |  ^-argX
    | ^-insideX
    ^-x
  "
  [stops]
  (let [xs #js[]
        prevX #(or (last xs) -1)]
    (doseq [stop stops]
      (let [x (oget stop "x")
            argX (oget stop "?argX")
            ch (oget stop "ch")
            insideX (+ x (paren->spaces ch))]
        (when (>= (prevX) x)
          (.pop xs))
        (.push xs x)
        (.push xs insideX)
        (when argX
          (.push xs argX))))
    (if (seq xs)
      (vec xs)
      default-tab-stops)))


(defn next-stop
  "Get the next tab stop starting from x, moving in the dx direction (+1/-1)
  For example:

    stops: [0 2   6 8    12]
        x:       5
     left:    2
    right:        6
  "
  [stops x dx]
  (let [left (last (filter #(> x %) stops))
        right (first (filter #(< x %) stops))]
    (case dx
      1 right
      -1 (or left 0)
      nil)))


(defn get-line-indentation
  "Get the indentation length of the given line. Return nil if blank."
  [line]
  (when-not (gstring/isEmptyOrWhitespace line)
    (- (count line)
       (count (gstring/trimLeft line)))))


(defn indent-line
  "Add or remove spaces from the front of the line."
  [js-buffer row delta]
  (if (pos? delta)
    (ocall js-buffer "insert" (array row 0) (gstring/repeat " " delta))
    (let [range (array (array row 0) (array row (- delta)))
          removed (ocall js-buffer "getTextInRange" range)]
      (when (gstring/isEmptyOrWhitespace removed)
        (ocall js-buffer "delete" range)))))


(defn indent-lines
  "Add or remove spaces from the front of each non-empty line.
  Peforms change as single transaction."
  [js-buffer rows delta]
  (ocall js-buffer "transact"
    (fn []
      (doseq [row rows]
        (when-not (ocall js-buffer "isRowBlank" row)
          (indent-line js-buffer row delta))))))


(defn tab-at-selection
  "Try indenting the selected lines according to structural tab stops.
  Return true on success."
  [js-editor selection dx stops]
  (let [start-row (oget selection "start" "row")
        end-col (oget selection "end" "column")
        end-row (cond-> (oget selection "end" "row") (zero? end-col) dec)
        rows (range start-row (inc end-row))
        js-buffer (ocall js-editor "getBuffer")
        indents (map #(get-line-indentation (ocall js-buffer "lineForRow" %)) rows)
        min-indent (apply min (remove nil? indents))
        indent-row (first (remove #(ocall js-buffer "isRowBlank" %) rows))
        indentX (when indent-row (get-line-indentation (ocall js-buffer "lineForRow" indent-row)))
        nextX (when indentX (next-stop stops indentX dx))
        delta (when nextX (max (- min-indent) (- nextX indentX)))]
    (when delta
      (indent-lines js-buffer rows delta)
      true)))


(defn tab-at-cursor
  "Try indenting the line at the cursor according to structural tab stops.
  Return true on success."
  [js-editor cursor dx stops]
  (let [row (oget cursor "row")
        js-buffer (ocall js-editor "getBuffer")
        line (ocall js-buffer "lineForRow" row)
        indentX (get-line-indentation line)
        empty-line? (nil? indentX)
        x (if (and indentX (= dx -1)) ;; shift-tab anywhere dedents as if we are at indentation point
            indentX
            (oget cursor "column"))
        use-stops? (or empty-line? (<= x indentX))
        nextX (when use-stops?
                (next-stop stops x dx))]
    (when nextX
      (when (and indentX (< x indentX))
        (ocall js-editor "setCursorBufferPosition" (array row indentX)))
      (indent-line js-buffer row (- nextX x))
      true)))


(defn on-tab
  "Try indenting cursor or selection according to structural tabstops.
  Return true on success."
  [js-editor dx]
  (let [selections (ocall js-editor "getSelectedBufferRanges")
        selection? (not (ocall (aget selections 0) "isEmpty"))
        multiple-selections? (> (oget selections "length") 1)
        cursors (ocall js-editor "getCursorBufferPositions")
        multiple-cursors? (> (oget cursors "length") 1)
        stops (expand-tab-stops @previous-tabstops)]
    (if selection?
      (when-not multiple-selections?
        (tab-at-selection js-editor (first selections) dx stops))
      (when-not multiple-cursors?
        (tab-at-cursor js-editor (first cursors) dx stops)))))


;; -----------------------------------------------------------------------------
;; Atom Events

(def change-subscriptions
  "Editor id -> list of subscriptions for `.dispose`ing"
  (atom {}))


(defn- add-change-events! [js-editor]
  (when (and js-editor (oget js-editor "id"))
    (let [js-buffer (ocall js-editor "getBuffer")
          subs (if (:use-smart-mode? @config)
                 [(ocall js-buffer "onDidChangeText" on-did-change-text)
                  (ocall js-editor "onDidChangeCursorPosition" on-change-cursor-position)]
                 [(ocall js-editor "onDidChangeSelectionRange" debounced-apply-parinfer)])]
      (swap! change-subscriptions assoc (oget js-editor "id") subs))))


(defn- remove-change-events! [js-editor]
  (when (and js-editor (oget js-editor "id"))
    (let [editor-id (oget js-editor "id")]
      (run! #(ocall % "dispose") (@change-subscriptions editor-id))
      (swap! change-subscriptions update editor-id empty))))


(defn- refresh-change-events! [js-editor]
  (remove-change-events! js-editor)
  (add-change-events! js-editor))


(defn- refresh-all-change-events! []
  (let [js-editors (ocall js/atom "workspace.getTextEditors")]
    (run! refresh-change-events! js-editors)))


(defn- goodbye-editor
  "Runs when an editor tab is closed."
  [js-editor]
  (when (and js-editor (oget js-editor "id"))
    (swap! editor-states dissoc (oget js-editor "id"))
    (remove-change-events! js-editor)))


(defn- confirm-paren-mode-msg [filename num-lines-changed]
  (let [l (if (util/one? num-lines-changed) "line" "lines")]
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


(def js-change-timeout nil)


(defn- on-did-change-text [js-txt-changes]
  (js/clearTimeout js-change-timeout)
  (apply-parinfer! js-txt-changes))


(defn- on-change-cursor-position [_js-cursor-changes]
  (js/clearTimeout js-change-timeout)
  (when @monitor-cursor?
    (set! js-change-timeout (js/setTimeout (fn [] (apply-parinfer! nil)) 0))))


(defn- hello-editor
  "Runs when an editor is opened."
  [js-editor]
  (let [editor-id (oget js-editor "id")
        file (ocall js-editor "getPath")
        file-extension (extname file)
        init-parinfer? (file-has-watched-extension? file)
        current-file (ocall js-editor "getTitle")]
    ;; add this editor state to our atom
    (swap! editor-states assoc editor-id :disabled)

    ;; listen to the buffer and editor change events
    (add-change-events! js-editor)

    ;; add the destroy event
    (ocall js-editor "onDidDestroy" goodbye-editor)

    ;; run Paren Mode on the file if we recognize the extension.
    (when init-parinfer?
      (let [show-open-file-dialog? (:show-open-file-dialog? @config)
            current-text (ocall js-editor "getText")
            ;; add commentChars option if needed
            js-opts (if-let [comment-chars (get-comment-chars file-extension)]
                      (js-obj "commentChars" (clj->js comment-chars))
                      nil)
            js-paren-mode-result (ocall parinfer "parenMode" current-text js-opts)
            paren-mode-succeeded? (true? (oget js-paren-mode-result "success"))
            paren-mode-text (oget js-paren-mode-result "text")
            text-delta (util/lines-diff current-text paren-mode-text)
            paren-mode-changed-the-file? (and paren-mode-succeeded?
                                              (not (zero? (:diff text-delta))))]
        (cond
          ;; Paren Mode failed and they want to see the dialog.
          ;; Inform them and then drop into Paren Mode.
          (and (not paren-mode-succeeded?) show-open-file-dialog?)
          (ocall js/atom "confirm"
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
          (ocall js/atom "confirm"
            (js-obj "message" (str "Parinfer will change " current-file)
                    "detailedMessage" (confirm-paren-mode-msg current-file (:diff text-delta))
                    "buttons" (js-obj "Yes" (fn []
                                              (ocall js-editor "setText" paren-mode-text)
                                              (swap! editor-states assoc editor-id :indent-mode))
                                      ;; do nothing if they click "No"
                                      "No" util/always-nil)))

          ;; Paren Mode changed the file and they do not want to see the dialog.
          ;; Update the file and drop them into Indent Mode silently.
          (and paren-mode-changed-the-file? (not show-open-file-dialog?))
          (do (ocall js-editor "setText" paren-mode-text)
              (swap! editor-states assoc editor-id :indent-mode))

          ;; Paren Mode succeeded and the file was unchanged.
          ;; Drop them into Indent Mode silently.
          ;; NOTE: this is the most likely case for someone using Parinfer regularly
          :else
          (swap! editor-states assoc editor-id :indent-mode))))))


(defn- pane-changed
  "Runs when the user changes their pane focus.
   ie: switches editor tabs"
  [_item]
  ;; update the status bar
  (swap! editor-states identity))


(defn- disable! []
  (when-let [{:keys [editor-id]} (get-active-editor)]
    (swap! editor-states assoc editor-id :disabled)))


(defn- toggle-mode! []
  (let [{:keys [editor-id editor-state]} (get-active-editor)]
    (when editor-state
      (if (= editor-state :indent-mode)
        (swap! editor-states assoc editor-id :paren-mode)
        (swap! editor-states assoc editor-id :indent-mode))
      ;; run parinfer in their new mode
      (on-change-cursor-position nil))))


(defn- next-tab-stop! [e dx]
  (let [tabbed? (when-let [{:keys [js-editor editor-state]} (get-active-editor)]
                  (when (not= :disabled editor-state)
                    (on-tab js-editor dx)))]
    (when-not tabbed?
      (ocall e "abortKeyBinding"))))


;; -----------------------------------------------------------------------------
;; Package-required events

(def init!
  (gfunctions/once
    (fn [_state]
      (util/js-log (str "atom-parinfer v" version " activated"))

      (observe-config!)
      (try
        (handle-old-config-files!)
        (catch js/Object _err nil))

      (ocall js/atom "workspace.observeTextEditors" hello-editor)
      (ocall js/atom "workspace.onDidChangeActivePaneItem" pane-changed)

      ;; add package events
      (ocall js/atom "commands.add" "atom-workspace"
        (js-obj "parinfer:disable" disable!
                "parinfer:toggle-mode" toggle-mode!))
      (ocall js/atom "commands.add" "atom-text-editor"
        (js-obj "parinfer:next-tab-stop" #(next-tab-stop! % 1)
                "parinfer:prev-tab-stop" #(next-tab-stop! % -1)))

      ;; Sometimes the editor events can all load before Atom catches up with the DOM
      ;; resulting in an initial empty status bar.
      ;; These calls help with that and it doesn't hurt anything to call them extra.
      ;; TODO: figure out if there is an actual Atom event instead of this hackery
      (js/setTimeout pane-changed 100)
      (js/setTimeout pane-changed 500)
      (js/setTimeout pane-changed 1000)
      (js/setTimeout pane-changed 2000)
      (js/setTimeout pane-changed 5000))))

;; -----------------------------------------------------------------------------
;; Module Export (required for Atom package)

(oset! js/module "exports"
  (js-obj "activate" init!
          "config" (clj->js config-schema)
          "deactivate" util/always-nil
          "serialize" util/always-nil))


;; noop - needed for :nodejs CLJS build
(set! *main-cli-fn* util/always-nil)
