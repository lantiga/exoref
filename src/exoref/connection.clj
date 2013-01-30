(ns exoref.connection
  {:author "Luca Antiga"}
  (:require [taoensso.carmine :as car]))

(def ^:dynamic *conn-pool* nil)
(def ^:dynamic *conn-spec* nil)

(def make-conn-pool car/make-conn-pool)
(def make-conn-spec car/make-conn-spec)

(defmacro with-conn
  [conn-pool conn-spec & body]
  `(binding [*conn-pool* ~conn-pool
             *conn-spec* ~conn-spec]
     (do ~@body)))

(defmacro wcar
  "Evaluates body in the context of a Redis connection
   with provided connection pool and spec. Returns the
   server response."
  [conn-pool conn-spec & body]
  `(car/with-conn ~conn-pool ~conn-spec
     ~@body))

(defmacro wcar*
  "Evaluates body in the context of a Redis connection
   with provided connection pool and spec. Returns the
   evaluation of body."
  [conn-pool conn-spec & body]
  `(with-local-vars [ret# nil]
     (car/with-conn ~conn-pool ~conn-spec
       (var-set ret# (do ~@body)))
     (var-get ret#)))
