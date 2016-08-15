(defproject atom-parinfer "1.16.0"
  :description "Parinfer extension for the Atom editor."

  :license {:name "ISC License"
            :url "https://github.com/oakmac/atom-parinfer/blob/master/LICENSE.md"
            :distribution :repo}

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.198"]]

  :plugins [[lein-cljsbuild "1.1.2"]]

  :source-paths ["src"]

  :clean-targets ["target"
                  "./lib/atom-parinfer.js"]

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
