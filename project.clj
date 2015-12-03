(defproject atom-parinfer "0.6.0"
  :description "Parinfer extension for the Atom editor."

  :license {:name "ISC License"
            :url "https://github.com/oakmac/atom-parinfer/blob/master/LICENSE.md"
            :distribution :repo}

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.122"]
                 [parinfer "0.2.3"]
                 [rodnaph/lowline "0.0.2"]]

  :plugins [[lein-cljsbuild "1.1.0"]]

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
