(defproject parinfer-lib "0.1.0-SNAPSHOT"
  :description "chopped up version of parinfer for the atom extension"

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.122"]]

  :plugins [[lein-cljsbuild "1.1.0"]]

  :source-paths ["src"]

  :cljsbuild
    {:builds
      {:lib
        {:source-paths ["src"]
         :compiler
           {:output-to "../lib/parinfer.js"
            :optimizations :simple
            :language-in :ecmascript5
            :language-out :ecmascript5
            :target :nodejs
            :hashbang false
            :pretty-print true}}}})
