(defproject exoref "0.1.4"
  :description "Clojure Redis-based reference types."
  :url "https://github.com/lantiga/exoref"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [com.taoensso/carmine "2.2.0"]
                 [com.taoensso/nippy "2.1.0"]]
  :min-lein-version "2.0.0"
  :global-vars {*warn-on-reflection* true})
