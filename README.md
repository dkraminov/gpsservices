##introduction

Server implements communication device<=>server [protocol](https://docs.google.com/spreadsheets/d/15s-2ZbqOQ1bZvAtFFm9sIEuKy3jbJzxdeynp72sjoYU/edit#gid=3) 

device: gps tracker [Autolink II](http://tn-group.net/index.php?route=product/product&path=25_29&product_id=68)

## Usage

```clojure
(use 'gpsservices.core)
(require '[gpsservices.autolink2 :as autolink2])

(defrecord MyEvents []
  autolink2/ISockEvents
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


msg data is hashmap:

```clojure
   {:type :package,
    :pack-num 2,
    :unixtime 1406097384,
    :data [{:point {:lat 55.0224, :lon 82.9139},
            :status-bytes nil,
            :speed 0.0,
            :satellite-count 20,
            :altitude 130,
            :course 0}]}
````

