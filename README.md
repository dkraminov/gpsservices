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
    (println "ISockEvents-error:" (.getMessage err)) this)
  (on-open [this session]
    (println "ISockEvents-open:") this)
  (on-message [this session msg]
    (println "ISockEvents-message:" msg " car-id > " @(:car-id session)) this)
  (on-close [this session]
    (println "ISockEvents-close: car-id >" @(:car-id session)) this))
    
(start-alink2-server (->MyEvents) 7779)
````

