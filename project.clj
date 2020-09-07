(defproject atom-parinfer "1.24.0"
  :description "Parinfer extension for the Atom editor."

  :author "Chris Oakman <chris@oakmac.com>"
  :url "https://github.com/oakmac/atom-parinfer"

  :license {:name "ISC License"
            :url "https://github.com/oakmac/atom-parinfer/blob/master/LICENSE.md"
            :distribution :repo}

  :dependencies
  [[org.clojure/clojure "1.10.1"]
   [org.clojure/clojurescript "1.10.764"]
   [binaryage/oops "0.7.0"]]

  :plugins [[lein-cljsbuild "1.1.7"]]

  :source-paths ["src"]

  :clean-targets ["target" "./lib/atom-parinfer.js"]

  :cljsbuild
    {:builds
     [{:id "prod"
       :source-paths ["src-cljs"]
       :compiler
         {:output-to "./lib/atom-parinfer.js"
          :optimizations :advanced
          :language-out :ecmascript5
          :target :nodejs
          :hashbang false
          :pretty-print false
          :pseudo-names false}}]})
