(ns gpsservices.autolink2-test
  (:require [clojure.test :refer :all]
            [clojure.core.match :refer [match]]
            [gpsservices.autolink2 :as a2]))

(def ^:private exmpl-header (byte-array [0xff 0x22 0x41 0x04 0x55 0x7f 0xa0 0x15 0x03 0x00]))

(def ^:private exmpl-package (byte-array [0x5b 0x02
                                          ;;type pack
                                          0x01
                                          ;;length data
                                          0x23 0x00
                                          ;;unixtime
                                          0xe8 0x57 0xcf 0x53
                                          ;;data start
                                          0x03 0xf0 0x16 0x5c 0x42 0x04 0xeb 0xd3
                                          0xa5 0x42 0x05 0x00 0x0d 0x55 0x00 0x09
                                          0x00 0xf0 0x44 0x5e 0x08 0x63 0xfa 0x00
                                          0x0e 0xfa 0x46 0x00 0x00 0x00 0xfa 0x1f
                                          0x00 0x00 0x00 ;;data end
                                          0x7f           ;;control sum
                                          0x5d]))

(def ^:private exmpl-package-invalid (byte-array [0x5b 0x02
                                                  0x01
                                                  0x23 0x00
                                                  0xe8 0x57 0xcf 0x53
                                                  0x03 0xf0 0x16 0x5c
                                                  0x42 0x04 0xeb 0xd3 0xa5 0x42 0x05 0x00 0x0d 0x55 0x00 0x09 0x00
                                                  0xf0 0x44 0x5e 0x08 0x63 0xfa 0x00 0x0e 0xfa 0x46 0x00 0x00 0x00]))


(deftest detect-type-test-1
  (testing "Detect type head"
    (let [type (a2/get-type-package exmpl-header)]
      (is (= type :header)))))

(deftest detect-type-test-2
  (testing "Detect type package"
    (let [type (a2/get-type-package exmpl-package)]
              (is (= type :package)))))

(deftest detect-type-test-3
  (testing "Detect type invalid data"
    (let [type (a2/get-type-package exmpl-package-invalid)]
              (is (= type nil)))))

(deftest decode-data-test-1
  (testing "Decode valid data"
    (let [data (a2/decode-data exmpl-package)
          valid (match [data]
                       [{:type :package
                         :pack-num 2
                         :data [{:unixtime 1406097384
                                 :point {:lat _ :lon _}
                                 :speed 0.0
                                 :status-bytes _
                                 :satellite-count 20
                                 :altitude 130
                                 :course 0}]}] true :else false)]
      (is (= valid true)))))

(deftest decode-data-test-2
  (testing "Decode invalid data"
    (let [data (a2/decode-data exmpl-package-invalid)
          valid (match [data]
                       [{:type :package
                         :pack-num 2
                         :data [{:unixtime 1406097384
                                 :point {:lat _ :lon _}
                                 :speed 0.0
                                 :status-bytes _
                                 :satellite-count 20
                                 :altitude 130
                                 :course 0}]}] true :else false)]
      (is (= valid false)))))

(comment
  (a2/decode-data exmpl-package)
  (a2/decode-data exmpl-package-invalid)
  (run-all-tests))