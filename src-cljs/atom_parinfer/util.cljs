(ns atom-parinfer.util
  (:require
    [goog.dom :as gdom]))

;;------------------------------------------------------------------------------
;; Logging
;;------------------------------------------------------------------------------

(defn js-log
  "Logs a JavaScript thing."
  [js-thing]
  (js/console.log js-thing))

(defn log
  "Logs a Clojure thing."
  [clj-thing]
  (js-log (pr-str clj-thing)))

(defn log-atom-changes [atm kwd old-value new-value]
  (log old-value)
  (log new-value)
  (js-log "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"))

;;------------------------------------------------------------------------------
;; DOM
;;------------------------------------------------------------------------------

(defn by-id [id]
  (js/document.getElementById id))

(defn qs [selector]
  (js/document.querySelector selector))

(defn remove-el! [el]
  (gdom/removeNode el))

;;------------------------------------------------------------------------------
;; String
;;------------------------------------------------------------------------------

(defn split-lines
  "Same as clojure.string/split-lines, except it doesn't remove empty lines at
  the end of the text."
  [text]
  (vec (.split text #"\r?\n")))

(defn lines-diff
  "Returns a map {:diff X, :same Y} of the difference in lines between two texts.
   NOTE: this is probably a reinvention of clojure.data/diff"
  [text-a text-b]
  (let [vec-a (split-lines text-a)
        vec-b (split-lines text-b)
        v-both (map vector vec-a vec-b)
        initial-count {:diff 0, :same 0}]
    (reduce (fn [running-count [line-a line-b]]
              (if (= line-a line-b)
                (update-in running-count [:same] inc)
                (update-in running-count [:diff] inc)))
            initial-count
            v-both)))

;;------------------------------------------------------------------------------
;; Misc
;;------------------------------------------------------------------------------

(defn one? [x]
  (= 1 x))

(def always-nil (constantly nil))
