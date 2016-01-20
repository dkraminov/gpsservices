(ns gpsservices.core
  (:gen-class)
  (:require [gpsservices.autolink2 :as autolink2]))

(defn start-alink2-server [SockEvents port]
  "SockEvents is record implements the protocol gpsservices.autolink2/ISockEvents"
  (autolink2/start-server SockEvents {:port port}))

(defn stop-alink2-server []
  (autolink2/stop-server))