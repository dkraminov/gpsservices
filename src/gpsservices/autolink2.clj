(ns gpsservices.autolink2
  (:gen-class)
  (:require [clojure.core.async :refer [timeout buffer alts!! alts! close! onto-chan go go-loop chan <! >! >!! <!! thread]]
            [manifold.stream :as s]
            [gpsservices.utils.hexlify :as hx]
            [aleph.tcp :as tcp]
            [gloss.core :as g]
            [gloss.io :as gio])
  (:use [gloss.data.primitives :refer :all])
  (:import (java.util UUID)))

(defprotocol ISockEvents
  (on-error [this session err])
  (on-open [this session])
  (on-message [this session msg])
  (on-close [this session]))

(defrecord Session [id chan user-stream info last-packet car-id SockEvents])

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

(g/defcodec header-frame (g/ordered-map :car-id :uint64 :protocol :ubyte :header :ubyte))

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

(defn full-head? [^bytes data]
  (and (= (count data) 10) (= 0xff (hx/first-byte data)) (= 0x00 (hx/last-byte data))))

(defn full-pack? [^bytes data]
  (and (= 0x5b (hx/first-byte data)) (= 0x5d (hx/last-byte data))))

(defn valid-data? [^bytes data]
  (or (full-head? data) (full-pack? data)))

(defn get-type-package [^bytes data]
  (when data (if (full-head? data) :header
                                   (if (full-pack? data) :package))))

(defn- sub-decode [^bytes bytes start end frame]
  (let [sub (hx/sub-and-reverse bytes start end)]
    (gio/decode frame sub)))

(defn get-frames
  "return all frames from packages data"
  ([^bytes data] (get-frames data 9 []))
  ([^bytes data p-start-offset coll]
   (let [frame-length (sub-decode data 3 5 :uint16)
         p-end-offset (+ p-start-offset frame-length)
         data-count (- (count data) 2)                      ;; удаляем последние два байта так как это CS и end-flag
         frame (hx/subbytes data p-start-offset p-end-offset)
         coll (into coll [frame])]
     (if (< p-end-offset data-count)
       (get-frames data p-end-offset coll)
       coll))))

(defn- bytes->point [^bytes bytes]
  (gio/decode :float32 (hx/byte-reverse bytes)))

(defn- decode-info [^bytes bytes]
  (let [speed (* (hx/get-byte bytes 3) 1.852)
        sat-bin-part (partition 4 (Integer/toString (hx/get-byte bytes 2) 2) )
        sat-GPS-count (Integer/parseInt (apply str (first sat-bin-part)) 2)
        sat-GLONASS-count (Integer/parseInt (apply str (last sat-bin-part)) 2)]
    {:speed speed
     :satellite-count (+ sat-GPS-count sat-GLONASS-count)
     :altitude        (* (hx/get-byte bytes 1) 10)
     :course          (* (hx/get-byte bytes 0) 2)}))


(defn data-sum [^bytes data]
  (let [count-data (count data)]
    (loop [i 0 sum 0]
      (let [val (hx/get-byte data i)
            new-sum (+ sum val)]
        (if (< i (dec count-data))
          (recur (inc i) new-sum) new-sum)))))

(defn valid-CS? [^bytes data]
  (let [count-data (count data)
        origin-CS (aget data (- count-data 2))
        unixtime (hx/subbytes data 5 9)
        frames (get-frames data)
        unixtime-sum (data-sum unixtime)
        frames-sum (reduce + (map data-sum frames))
        CS (hx/int->int8 (+ unixtime-sum frames-sum))]
    (= origin-CS CS)))

(defn- decode-package-frame [^bytes frame]
  (let [frame-parts (partition 5 frame)
        pull (into {} (for [part frame-parts :let [[id & rest] (vec part)]]
                        [id (byte-array rest)]))
        info (decode-info (pull 5))]
    (merge {:point {:lat (bytes->point (pull 3))
                    :lon (bytes->point (pull 4))}
            :status-bytes (pull 9)} info)))

(defn decode-packages [^bytes data]
  (let [pack-num (sub-decode data 1 2 :ubyte)
        unixtime (sub-decode data 5 9 :uint32)
        frames (get-frames data)]
    (when (valid-CS? data)
      {:type :package
       :pack-num pack-num
       :unixtime unixtime
       :data     (vec (map decode-package-frame frames))})))

(defn gen-response
  "generate response for autolink2 device"
  ([] (gen-response 0x00))
  ([pack-num]
   (let [pack-num (or pack-num 0xFE)]
     (byte-array [0x7B 0x00 pack-num 0x7D]))))


(defn decode-data
  "parse binary autolink2 data"
  [^bytes data]
  (let [type (get-type-package data)]
    (case type
      :header (let [data (hx/byte-reverse data)
                    decode-header (dissoc (gio/decode header-frame data)) ]
                    (merge decode-header {:type :header}))
      :package (decode-packages data))))

(defn- pack-reader [^bytes data ^bytes new-data]
  (let [type-new-data (get-type-package new-data)
        type-last-data (get-type-package data)
        concat-data (hx/concat-bytes [data new-data])
        size-last-data (count data)]
    (if type-new-data new-data
                      (if (> size-last-data 1500)
                        (do (throw (Exception. "exceeded the minimum (1500b) buf size")) nil)
                        (if type-last-data new-data concat-data)))))

(defn- channel-handler [session]
  (let [{chan        :chan
         user-stream :user-stream
         last-packet :last-packet
         car-id      :car-id
         SockEvents  :SockEvents} session]
    (go-loop []
      (if-let [ch-data (<! chan)]
        (let [res (try
                    (swap! last-packet pack-reader ch-data)
                    (let [last-packet @last-packet]
                      (when (valid-data? last-packet)
                        (let [decrypted-data (decode-data last-packet) type (get-type-package last-packet)]
                          (when-let [new-car-id (:car-id decrypted-data)]
                            (reset! car-id new-car-id))
                          (case type
                            :header (s/put! user-stream (gen-response))
                            :package (if @car-id
                                       (s/put! user-stream (gen-response (:pack-num decrypted-data)))
                                       (throw (Exception. "header pack is missing"))))
                          (.on-message SockEvents session decrypted-data))))
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
   (let [id (gen-uuid) chan (chan) session (->Session id chan user-stream info (atom nil) (atom nil) SockEvents)]
     (s/connect user-stream chan)
     (handler session))))

(defn start-server
  ([] (start-server (->DefEvents)))
  ([SockEvents & [options]]
   (let [port (:port options 7779)
         srv (tcp/start-server (fn [user-stream info]
                                 (let [session (channel-new user-stream SockEvents info)]
                                   (register-user session)
                                   (.on-open SockEvents session)))
                               {:port port})]
     (printf "gpsservices.autolink2 server started. listen on %s\n" port)
     (reset! server srv))))

(defn stop-server []
  "close all sessions and stop server"
  (doseq [[_ user] @users]
    (close! (:chan user)))
  (when-let [srv @server]
    (.close srv)))
