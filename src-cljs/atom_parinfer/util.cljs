(ns atom-parinfer.util
  (:require [goog.dom :as gdom]))

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

(defn ends-with [train caboose]
  (let [train-len (aget train "length")
        caboose-len (aget caboose "length")
        end-of-train (.substring train (- train-len caboose-len))]
    (= end-of-train caboose)))
