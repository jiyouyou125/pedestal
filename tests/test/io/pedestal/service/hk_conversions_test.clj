; Copyright 2025 Nubank NA

; The use and distribution terms for this software are covered by the
; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0)
; which can be found in the file epl-v10.html at the root of this distribution.
;
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
;
; You must not remove this notice, or any other, from this software.

(ns io.pedestal.service.hk-conversions-test
  "Tests for conversions from Pedestal's allowed response bodies to the more limited set supported by HttpKit."
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [clojure.core.async :refer [go]]
            [io.pedestal.http.http-kit.response :refer [convert-response-body]])
  (:import (java.io InputStream)
           (java.nio ByteBuffer)
           (java.nio.channels Channels)))

(deftest response-byte-buffer
  (let [s          "Are we not men? We are Devo."
        byte-array (.getBytes s "UTF-8")
        buf        (ByteBuffer/wrap byte-array)
        stream     (convert-response-body buf)]
    (is (instance? InputStream stream))
    (is (= s
           (slurp stream)))))

(deftest response-async-channel
  (let [s         "Clojure is a dynamic, general-purpose programming language, combining the approachability
  and interactive development of a scripting language with an efficient and robust infrastructure
  for multithreaded programming."
        ch        (go s)
        converted (convert-response-body ch)]
    (is (= s
           converted))))

(deftest response-async-channel-non-string
  (let [s         "Clojure is a dialect of Lisp, and shares with Lisp the code-as-data philosophy and a powerful macro system."
        ch        (go (-> s (.getBytes "UTF-8") ByteBuffer/wrap))
        stream (convert-response-body ch)]
    (is (instance? InputStream stream))
    (is (= s
           (slurp stream)))))

(deftest response-byte-channel
  (let [file (io/file "file-root/sub/index.html")
        channel (Channels/newChannel (io/input-stream file))
        stream (convert-response-body channel)]
    (is (instance? InputStream stream))
    (is (= (slurp file)
           (slurp stream)))))

(deftest nil-passes-through-unchanged
  (is (= nil
         (convert-response-body nil))))

(deftest input-stream-passes-through-unchanged
  (let [stream (-> "file-root/test.html" io/file io/input-stream)]
    (is (identical? stream (convert-response-body stream)))))
