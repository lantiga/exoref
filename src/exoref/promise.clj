(ns exoref.promise
  "A promise implementation based on Redis."
  {:author "Luca Antiga"}
  (:use exoref.atom))

(defn exopromise
  "Returns an exopromise object that can be read with deref/@, and set,
  once only, with deliver. Calls to deref/@ prior to delivery will
  block. All subsequent derefs will return the same delivered value
  without blocking."
  [key & options]
  (let [d (java.util.concurrent.CountDownLatch. 1)
        w (promise)
        v (apply exoatom key nil options)]
    (add-watch v "promise-watch" (fn [_ _ _ n] (when n (.countDown d)) (deliver w 1)))
    (reify 
     clojure.lang.IDeref
      (deref [_] (.await d) @v)
     clojure.lang.IFn
      (invoke [this x]
        (locking d
          (if (pos? (.getCount d))
            (do 
              (reset!! v x)
              @w
              this)
            (throw (IllegalStateException. "Multiple deliver calls to a promise"))))))))

