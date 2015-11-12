(defproject atom-parinfer "0.0.0"
  :description "Parinfer extension for the Atom editor."

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.122"]]

  :plugins [[lein-cljsbuild "1.1.0"]]

  :source-paths ["src"]

  :cljsbuild
    {:builds
      {:main
        {:source-paths ["src-cljs"]
         :compiler
           {:output-to "./lib/atom-parinfer.js"
            :optimizations :simple
            :language-in :ecmascript5
            :language-out :ecmascript5
            :target :nodejs
            :hashbang false
            :pretty-print true}}}})
