(ns parinfer.js-exports
  "Exports a JavaScript module with function hooks into Parinfer."
  (:require
    [parinfer.indent-mode :as indent-mode]
    [parinfer.paren-mode :as paren-mode]))

;;-----------------------------------------------------------------------------
;; JS Function Wrappers
;;-----------------------------------------------------------------------------

(defn- js-indent-mode
  "JavaScript wrapper around parinfer.indent-mode/format-text"
  ([txt] (js-indent-mode txt (js-obj)))
  ([txt js-opts]
   (let [clj-opts {}
         clj-opts (if (aget js-opts "row")
                    (assoc clj-opts :cursor-line (aget js-opts "row"))
                    clj-opts)
         clj-opts (if (aget js-opts "column")
                    (assoc clj-opts :cursor-x (aget js-opts "column"))
                    clj-opts)
         result (indent-mode/format-text txt clj-opts)]
     (if (:valid? result)
       (:text result)
       false))))

(defn- js-paren-mode
  "JavaScript wrapper around parinfer.paren-mode/format-text"
  ([txt] (js-paren-mode txt (js-obj)))
  ([txt js-opts]
   (let [clj-opts {}
         clj-opts (if (aget js-opts "row")
                    (assoc clj-opts :cursor-line (aget js-opts "row"))
                    clj-opts)
         clj-opts (if (aget js-opts "column")
                    (assoc clj-opts :cursor-x (aget js-opts "column"))
                    clj-opts)
         result (paren-mode/format-text txt clj-opts)]
     (if (:valid? result)
       (:text result)
       false))))

;;-----------------------------------------------------------------------------
;; Module Export
;;-----------------------------------------------------------------------------

(when js/module
  (set! js/module.exports
    (js-obj "indentMode" js-indent-mode
            "parenMode" js-paren-mode)))

;; noop - needed for :nodejs CLJS build
(set! *main-cli-fn* (fn [] nil))
