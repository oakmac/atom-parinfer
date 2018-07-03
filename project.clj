(defproject atom-parinfer "1.22.0"
  :description "Parinfer extension for the Atom editor."

  :license {:name "ISC License"
            :url "https://github.com/oakmac/atom-parinfer/blob/master/LICENSE.md"
            :distribution :repo}

  :dependencies
    [[org.clojure/clojure "1.9.0"]
     [org.clojure/clojurescript "1.10.339"]
     [binaryage/oops "0.6.2"]]

  :plugins [[lein-cljsbuild "1.1.7"]]

  :source-paths ["src"]

  :clean-targets ["target"
                  "./lib/atom-parinfer.js"]

  :cljsbuild
    {:builds
     [{:id "prod"
       :source-paths ["src-cljs"]
       :compiler
         {:output-to "./lib/atom-parinfer.js"
          :optimizations :advanced
          :language-in :ecmascript5
          :language-out :ecmascript5
          :target :nodejs
          :hashbang false
          :pretty-print false
          :pseudo-names false}}]})
