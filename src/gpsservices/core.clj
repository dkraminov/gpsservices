(ns gpsservices.core
  (:gen-class)
  (:require [gpsservices.autolink2 :as autolink2]
            [gpsservices.autolink2 :as autolink1]))

(def ISockEvents autolink1/ISockEvents)
(def ISockEvents autolink2/ISockEvents)

(def autolink1-users autolink1/users)
(def autolink2-users autolink2/users)

(defn start-alink1-server [SockEvents port]
  "SockEvents is record implements the protocol gpsservices.autolink1/ISockEvents"
  (autolink1/start-server SockEvents {:port port}))

(defn stop-alink1-server []
  (autolink1/stop-server))


(defn start-alink2-server [SockEvents port]
  "SockEvents is record implements the protocol gpsservices.autolink2/ISockEvents"
  (autolink2/start-server SockEvents {:port port}))

(defn stop-alink2-server []
  (autolink2/stop-server))