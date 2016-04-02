(defproject gpsservices "0.1.1-SNAPSHOT"
  :description "autolink device GPS server"
  :url "https://github.com/seryh/gpsservices"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :source-paths ["src"]
  :test-paths ["test"]
  :main gpsservices.core
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/tools.logging "0.2.6"]
                 [log4j "1.2.17" :exclusions [javax.mail/mail
                                              javax.jms/jms
                                              com.sun.jdmk/jmxtools
                                              com.sun.jmx/jmxri]]
                 [org.clojure/core.async "0.2.374"]
                 [aleph "0.4.1-beta3"]
                 [gloss "0.2.5"]]
  :profiles {:uberjar {:aot :all}
             :dev {:dependencies [[org.clojure/core.match "0.3.0-alpha4"]]}})
