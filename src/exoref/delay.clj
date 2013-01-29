(ns exoref.delay
  "A delay implementation based on Redis."
  {:author "Luca Antiga"}
  (:refer-clojure :exclude [force])
  (:use exoref.atom))

(defprotocol IForce
  (force [this]))

;; TODO: check that bodies on different exodelays are nil or equivalent.

(defmacro exodelay
  "Takes a Redis key and a body of expressions and yields an exodelay object that will
  invoke the body only the first time it is forced (with force or deref/@), and
  will cache the result and return it on all subsequent force calls."
  [key & body]
  `(let [d# (java.util.concurrent.CountDownLatch. 1)
         unrealized-val# :__unrealized__
         requested-val# :__requested__
         realizing-val# :__realizing__
         v# (exoatom ~key unrealized-val#)
         request#
         (fn []
           (compare-and-set!! v# unrealized-val# requested-val#))
         eval-body-when-provided#
         (fn []
           (when (and '~@body (pos? (.getCount d#))
                      (compare-and-set!! v# requested-val# realizing-val#))
             (compare-and-set!! v# realizing-val# (do ~@body))))]
     (add-watch v# "delay-watch"
                (fn [_# _# _# n#]
                  (cond
                   (= requested-val# n#)
                   (eval-body-when-provided#)
                   (not (#{unrealized-val# requested-val# realizing-val#} n#))
                   (.countDown d#))))
     (reify
       clojure.lang.IDeref
       (deref [_#]
         (request#)
         (.await d#)
         @v#)
       IForce
       (force [this#]
         (deref this#))
       clojure.lang.IPending
       (isRealized [_#]
         (zero? (.getCount d#))))))
