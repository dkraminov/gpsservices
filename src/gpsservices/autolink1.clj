(ns gpsservices.autolink1
  (:gen-class)
  (:require [clojure.core.async :refer [timeout buffer alts!! alts! close! onto-chan go go-loop chan <! >! >!! <!! thread]]
            [manifold.stream :as s]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [aleph.tcp :as tcp]
            [clojure.tools.logging :as log]
            [clojure.string :as str]
            [gloss.core :as g]
            [gloss.io :as gio])
  (:use [gloss.data.primitives :refer :all])
  (:import (java.util UUID))
  (:import (java.math BigDecimal)))

(defn subs* [s start end]
  (if (neg? start)
    (subs s (+ (.length s) start) end)
    (subs s start end)))

(defprotocol ISockEvents
  (on-error [this session err])
  (on-open [this session])
  (on-message [this session msg])
  (on-close [this session]))

(defrecord Session [id chan user-stream info last-packet car-id SockEvents prev-packet])

(defrecord DefEvents []
  ISockEvents
  (on-error [this session err]
    (println "ISockEvents-error:" (.getMessage err)) this)
  (on-open [this session]
    (println "ISockEvents-open:") this)
  (on-message [this session msg]
    (println "ISockEvents-message:" msg " car-id > " @(:car-id session)) this)
  (on-close [this session]
    (println "ISockEvents-close: car-id >" @(:car-id session)) this))

(g/defcodec utf-frame (g/string :utf-8))

(defonce server (atom nil))

(defonce users (atom {}))

(defn- gen-uuid [] (str (UUID/randomUUID)))

(defn register-user [session]
  (let [id (:id session)]
    (swap! users assoc id session)))

(defn unregister-user [session]
  (let [id (:id session)
        SockEvents (:SockEvents session)
        user-stream (:user-stream session)]
    (.on-close SockEvents session)
    (s/close! user-stream)
    (swap! users dissoc id)))

(defn gen-response
  "generate response for autolink1 device"
  [] "RCPTOK\r\n")

(defn DMS->Decimal [dms-str]
  (let [direction (subs dms-str (- (count dms-str) 1))
        deg-min (subs dms-str 0 (- (count dms-str) 1))
        str-min (subs* deg-min -7 (count deg-min))
        minutes (Float/parseFloat str-min)
        degrees (Float/parseFloat (str/replace deg-min (re-pattern str-min) ""))
        decimal (+ (/ minutes 60) degrees)
        decimal (new BigDecimal decimal)
        decimal (.floatValue (.setScale decimal 6 BigDecimal/ROUND_HALF_UP))]
    (if (or (= "W" direction) (= "S" direction))
      (* decimal -1)
      decimal)))

(defn- get-date-from-packet [pack-vec]
  (let [time-seq (vec (partition 2 (pack-vec 18)))
        date-seq (vec (partition 2 (pack-vec 23)))
        date (t/date-time (Integer/parseInt (apply str "20" (date-seq 2)))
                          (Integer/parseInt (apply str (date-seq 1)))
                          (Integer/parseInt (apply str (date-seq 0)))
                          (Integer/parseInt (apply str (time-seq 0)))
                          (Integer/parseInt (apply str (time-seq 1)))
                          (Integer/parseInt (apply str (time-seq 2))))]
    (/ (c/to-long date) 1000)))

(defn parse-packet [packet]
  (try
    (let [pack-vec (str/split packet #",")]
      (when (= (pack-vec 0) "$AV")
        {:car-id (pack-vec 2)
         :speed (Math/round (* 1.852 (Float/parseFloat (pack-vec 21))))
         :satellite-count (Integer/parseInt (pack-vec 15))
         :course (Math/round (Float/parseFloat (pack-vec 22)))
         :unixtime (get-date-from-packet pack-vec)
         :point {:lat (DMS->Decimal (pack-vec 19))
                 :lon (DMS->Decimal (pack-vec 20)) }}))
    (catch Exception _ nil)))

(defn decode-data
  "parse autolink1 data"
  [data] (let [packets (str/split-lines data)
               packets-decode (vec (map parse-packet packets))]
           (vec (filter some? packets-decode))))

(defn- pack-reader [^bytes prev-data ^bytes new-data]
  (let [new-data (gio/decode utf-frame new-data)
        new-data-decode (decode-data new-data)]
    new-data-decode))

(defn- channel-handler [session]
  (let [{chan             :chan
         user-stream      :user-stream
         last-packet      :last-packet
         prev-packet      :prev-packet
         car-id           :car-id
         SockEvents       :SockEvents} session]
    (go-loop []
      (if-let [ch-data (<! chan)]
        (let [res (try
                    (reset! prev-packet @last-packet)
                    (swap! last-packet pack-reader ch-data)
                    (let [decrypted-data @last-packet]
                      (when-not @car-id
                        (reset! car-id (:car-id (last decrypted-data))))
                      (.on-message SockEvents session {:type :package
                                                       :data decrypted-data}))
                    (s/put! user-stream (gen-response))
                    :ok
                    (catch Exception e (.on-error SockEvents session e)
                                       (unregister-user session)
                                       (close! chan)
                                       :error))]
          (when-not (= res :error)
            (recur)))
        (unregister-user session)))) session)

(defn- channel-new
  ([user-stream SockEvents]
   (channel-new user-stream SockEvents {}))
  ([user-stream SockEvents info]
   (channel-new user-stream SockEvents info channel-handler))
  ([user-stream SockEvents info handler]
   (let [id (gen-uuid) chan (chan) session (->Session id chan user-stream info (atom nil) (atom nil) SockEvents (atom nil))]
     (s/connect user-stream chan)
     (handler session))))

(defn start-server
  ([] (start-server (->DefEvents)))
  ([SockEvents & [options]]
   (let [port (:port options 2888)
         srv (tcp/start-server (fn [user-stream info]
                                 (let [session (channel-new user-stream SockEvents info)]
                                   (register-user session)
                                   (.on-open SockEvents session)))
                               {:port port})]
     (log/infof "gpsservices.autolink1 server started. listen on %s\n" port)
     (reset! server srv))))

(defn stop-server []
  "close all sessions and stop server"
  (doseq [[_ user] @users]
    (close! (:chan user)))
  (when-let [srv @server]
    (.close srv)))
