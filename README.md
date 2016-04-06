[![Build Status](https://travis-ci.org/seryh/gpsservices.svg?branch=master)](https://travis-ci.org/seryh/gpsservices)

[![Clojars Project](https://img.shields.io/clojars/v/gpsservices.svg)](https://clojars.org/gpsservices)

##introduction

Server implements communication device<=>server [Autolink-II protocol](https://docs.google.com/spreadsheets/d/15s-2ZbqOQ1bZvAtFFm9sIEuKy3jbJzxdeynp72sjoYU/edit#gid=3)

device: gps tracker [Autolink-II](http://tn-group.net/index.php?route=product/product&path=25_29&product_id=68)

also supported Autolink-I protocol

## Usage autolink2

```clojure
(use 'gpsservices.core)

(defrecord MyEvents []
  ISockEvents-autolink2
  (on-error [this session err]
    (println "ISockEvents-error:" (.getMessage err)))
  (on-open [this session]
    (println "ISockEvents-open:"))
  (on-message [this session msg]
    (println "ISockEvents-message:" msg))
  (on-close [this session]
    (println "ISockEvents-close: car-id " @(:car-id session))))
    
(start-alink2-server (->MyEvents) 7779) ;; 7779 - listen port
````

autolink2 msg data is hashmap:
```clojure
   {:type :package,
    :pack-num 2,
    :data [{:point {:lat 55.0224, :lon 82.9139},
            :unixtime 1406097384,
            :status-bytes [...],
            :speed 0.0,           ;; km/h
            :satellite-count 20,
            :altitude 130,
            :course 0}]}
````

## Usage autolink1

```clojure
(use 'gpsservices.core)

(defrecord MyEvents []
  ISockEvents-autolink1
  (on-error [this session err]
    (println "ISockEvents-error:" (.getMessage err)))
  (on-open [this session]
    (println "ISockEvents-open:"))
  (on-message [this session msg]
    (println "ISockEvents-message:" msg))
  (on-close [this session]
    (println "ISockEvents-close: car-id " @(:car-id session))))
    
(start-alink1-server (->MyEvents) 7778) ;; 7778 - listen port
````

autolink1 msg data is hashmap:
```clojure
   {:type :package,
    :data [{:point {:lat 55.0224, :lon 82.9139},
            :unixtime 1406097384,
            :car-id "473646",
            :speed 0.0,           ;; km/h
            :satellite-count 20,
            :altitude 130,
            :course 0}]}
````

