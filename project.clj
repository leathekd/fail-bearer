(defproject fail-bearer "0.1.0"
  :description "Posts amusing failure gifs to github PRs with failing tests"
  :url "https://www.github.com/leathekd/fail-bearer"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.logging "0.2.6"]
                 [sonian/carica "1.1.0"]
                 [tentacles "0.2.5"]]
  :main fail.bearer
  :min-lein-version "2.3.4")
