(ns parinfer-lib.js-exports
  "Exports a JavaScript module with function hooks to Parinfer."
  (:require
    [parinfer-lib.infer :as infer]
    [parinfer-lib.prep :as prep]))

;;-----------------------------------------------------------------------------
;; JS Function Wrappers
;;-----------------------------------------------------------------------------

(defn- js-infer-parens [txt js-cursor]
  (let [clj-opts {:cursor-line (aget js-cursor "row")
                  :cursor-x (aget js-cursor "column")}
        result (infer/format-text txt clj-opts)]
    (if (:valid? result)
      (:text result)
      false)))

(defn- js-format-indentation
  ([txt] (js-format-indentation txt nil))
  ([txt js-cursor]
    (let [clj-opts (if js-cursor {:cursor-line (aget js-cursor "row")
                                  :cursor-x (aget js-cursor "column")}
                                 {})
          result (prep/format-text txt clj-opts)]
      (if (:valid? result)
        (:text result)
        false))))

;;-----------------------------------------------------------------------------
;; Module Export
;;-----------------------------------------------------------------------------

(when js/module
  (set! js/module.exports
    (js-obj "inferParens" js-infer-parens
            "setIndentation" js-format-indentation)))

;; noop - needed for :nodejs CLJS build
(set! *main-cli-fn* (fn [] nil))
