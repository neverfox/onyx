(defproject onyx "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/core.async "0.1.267.0-0d7780-alpha"]
                 [org.clojure/data.generators "0.1.2"]
                 [org.hornetq/hornetq-core-client "2.4.0.Final"]
                 [com.stuartsierra/component "0.2.1"]
                 [com.datomic/datomic-free "0.9.4384"]
                 [com.datomic/simulant "0.1.6"]
                 [com.taoensso/timbre "3.0.1"]
                 [zookeeper-clj "0.9.1"]
                 [incanter "1.5.4"]
                 [dire "0.5.1"]]
  :profiles {:dev {:dependencies [[midje "1.6.0"]]
                   :plugins [[lein-midje "3.1.1"]]}})

