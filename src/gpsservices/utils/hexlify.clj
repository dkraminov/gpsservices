(ns gpsservices.utils.hexlify
  (:require [gloss.io :as gio])
  (:use [gloss.data.primitives :refer :all]))

(defprotocol Hexl
  (hexl-hex [val])
  (hexl-char [char]))

(extend-type Number
  Hexl
  (hexl-hex [i]
    (let [rtnval (Integer/toHexString (if (< i 0) (+ 256 i) i)) ]
      (if (< (count rtnval) 2) (str "0" rtnval) rtnval)))
  (hexl-char [b]
    (let [v (if (< b 0) (+ 256 b) b)
          c  (char v)]
      (if  (and (< v 128 )(Character/isLetter c)) (.toString c) "."))))


(extend-type Character
  Hexl
  (hexl-hex [char]
    (hexl-hex (int (.charValue char))))
  (hexl-char [char]
    (hexl-char (int (.charValue char)))))

(defn byte-reverse [^bytes bytes]
  (byte-array (reverse bytes)))

(defn subbytes
  "slice byte-array"
  ([bytes start] (subbytes bytes start (count bytes)))
  ([bytes start end]
   (let [vec-bytes (vec bytes) vec-sub (subvec vec-bytes start end)]
     (byte-array vec-sub))))

(defn sub-and-reverse [^bytes bytes start end]
  (let [sub (subbytes bytes start end)]
    (byte-reverse sub)))

(defn str-to-bytes [str]
  (.getBytes str "UTF-8"))

(defn bytes->ubutes [^bytes bytes]
  (for [val bytes] (gio/decode :ubyte (byte-array [val]))))

(defn ubytes->butes [^bytes bytes]
  (for [val bytes] (gio/decode :byte (byte-array [val]))))

(defn vec-to-bytes [vec]
  "example hex-vec [0xFF 0xFF 0xFF 0xFF 0xFF]"
  (byte-array vec))

(defn concat-bytes [nested-bytes]
  (byte-array (mapcat seq nested-bytes)))

(defn hexify-as-str
  "Convert byte sequence to hex string"
  [coll]
  (let [hex [\0 \1 \2 \3 \4 \5 \6 \7 \8 \9 \a \b \c \d \e \f]]
    (letfn [(hexify-byte [b]
                         (let [v (bit-and b 0xFF)]
                           [(hex (bit-shift-right v 4)) (hex (bit-and v 0x0F))]))]
      (apply str (mapcat hexify-byte coll)))))

(defn get-byte [^bytes bytes i]
  (byte->ubyte (aget bytes i)))

(defn first-byte [^bytes bytes]
  (get-byte bytes 0))

(defn last-byte [^bytes bytes]
  (let [size (dec (count bytes))]
    (get-byte bytes size)))

(defn hexify-str [s]
  (hexify-as-str (.getBytes s)))

(defn- byte-array? [value]
  (= (Class/forName "[B") (class value)))

(defn int->int8 [^Integer num]
  (first (bytes->ubutes (vec (byte-array [num])))))

(defn hexlify
  "Perform similar to hexlify in emacs.  Accept a seq of bytes and
  convert it into a seq of vectors.  The first element of the vector is a
  seq of 16 strings for the HEX value of each byte.  The second element
  of the vector is a seq of the printable representation of the byte and the
  third elevment of thee vector is a seq of the integer value for each
  byte.  Works for chars as well."
  ([bytes] (hexlify bytes 16))
  ([bytes size]
   (let [parts (partition-all size bytes)]
     (for [part parts]
       [ (map hexl-hex part) (map hexl-char part) (map int part) ]))))

(defn hexlify-chars
  "Convert the bytes into a string of printable chars
   with . being used for unprintable chars"
  [^bytes bytes]
  (let [chars (mapcat second (hexlify bytes))]
    (apply str chars)))