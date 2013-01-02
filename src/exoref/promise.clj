(ns exoref.promise
  "A promise implementation based on Redis."
  {:author "Luca Antiga"}
  (:use exoref.atom))

(defn exopromise
  "Returns an exopromise object that can be read with deref/@, and set,
  once only, with deliver. Calls to deref/@ prior to delivery will
  block. All subsequent derefs will return the same delivered value
  without blocking. See also - realized?."
  [key & options]
  (let [d (java.util.concurrent.CountDownLatch. 1)
        w (promise)
        unrealized-val :__unrealized__
        v (apply exoatom key unrealized-val options)]
    (add-watch v "promise-watch" 
               (fn [_ _ _ n] 
                 (when (not= n unrealized-val)
                   (.countDown d) 
                   (deliver w 1))))
    (reify 
      clojure.lang.IDeref
      (deref [_] (.await d) @v)
      clojure.lang.IBlockingDeref
      (deref
        [_ timeout-ms timeout-val]
        (if (.await d timeout-ms java.util.concurrent.TimeUnit/MILLISECONDS)
          @v
          timeout-val))
      clojure.lang.IPending
      (isRealized [this]
        (zero? (.getCount d)))
      clojure.lang.IFn
      (invoke 
        [this x]
        (when (and (pos? (.getCount d))
                   (compare-and-set!! v unrealized-val x))
          @w
          this)))))
