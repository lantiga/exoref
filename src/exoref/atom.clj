(ns exoref.atom
  "A complete atom implementation based on Redis."
  {:author "Luca Antiga"}
  (:use exoref.connection)
  (:require [taoensso.carmine :as car]))

(defprotocol IExoatom
  (compareAndSet [this o n])
  (swap [this f args])
  (reset [this v])
  (triggerWatches [this payload])
  (closeListener [this]))

;; ported from clojure.lang.ARef;
;; validate in ARef is not accessible from a proxy, hence we define it here for now
(defn ^:private validate
  [^clojure.lang.ARef r val]
  (let [vf (.getValidator r)]
    (try
      (if (and (not (nil? vf)) (not (vf val)))
        (throw (IllegalStateException. "Invalid reference state")))
      (catch RuntimeException re
        (throw re))
      (catch Exception e
        (throw (IllegalStateException. "Invalid reference state"))))))

(defn uuid
  "Returns a random UUID using java.util.UUID."
  []
  (str (java.util.UUID/randomUUID)))

(defn ^:private make-proxy
  "Creates an instance of a proxy class to clojure.lang.ARef implementing
   the IExoatom protocol.
   Takes in input a Redis key, Carmine connection pool and connection spec
   objects and a Carmine listener record.
   The resulting instance is an Exoatom, a complete implementation of a Clojure
   atom based on Redis. Meta, validator and watches are defined on a per-process
   (i.e. are not serialized to Redis and shared by all processes using the Exoatom)."
  [key conn-pool conn-spec listener]

  (proxy [clojure.lang.ARef exoref.atom.IExoatom] []

    (deref []
      "Returns the value stored at key on the Redis server."
      (wcar conn-pool conn-spec (car/get key)))

    (notifyWatches [oldval newval]
      "Provides a no-op overload of ARef notifyWatches." )

    (compareAndSet [oldval newval]
      "Atomically sets the value at key to newval if and only
       if the current value at key is identical to oldval. Returns
       true if set happened, else false."
      (validate this newval)
      (wcar* conn-pool conn-spec
             (car/with-reply (car/watch key))
             (if (= oldval (car/with-reply (car/get key)))
               (let [old-uuid (uuid)
                     new-uuid (uuid)
                     expiration-secs 60
                     payload (str old-uuid " " new-uuid)]
                 (car/with-replies
                   (car/multi)
                   (car/set key (car/preserve newval))
                   (car/setex old-uuid expiration-secs (car/preserve oldval))
                   (car/setex new-uuid expiration-secs (car/preserve newval))
                   (car/publish key payload)
                   (car/exec))
                 true)
               false)))

    (swap [f args]
      "Atomically swaps the value at key to be:
       (apply f current-value-at-key args)
       Returns the value that was swapped in."
      (wcar* conn-pool conn-spec
             (loop []
               (car/with-reply (car/watch key))
               (let [oldval (car/with-reply (car/get key))
                     newval
                     (try
                       (let [newval (if args (apply f oldval args) (f oldval))]
                         (validate this newval)
                         newval)
                       (catch Exception e
                         (car/with-reply (car/unwatch))
                         (throw e)))
                     old-uuid (uuid)
                     new-uuid (uuid)
                     expiration-secs 60
                     payload (str old-uuid " " new-uuid)
                     ret (car/with-replies
                           (car/multi)
                           (car/set key (car/preserve newval))
                           (car/setex old-uuid expiration-secs (car/preserve oldval))
                           (car/setex new-uuid expiration-secs (car/preserve newval))
                           (car/publish key payload)
                           (car/exec))]
                 (if-not (empty? (last ret))  newval (recur))))))

    (reset [val]
      "Atomically set the value at key to newval. Returns newval."
      (swap this (constantly val) nil))

    (triggerWatches [payload]
      "Callback function for invoking watches."
      (let [watches (.getWatches ^clojure.lang.ARef this)]
        (when-not (empty? watches)
          (wcar* conn-pool conn-spec
                 (let [[_ old-uuid new-uuid] (re-find #"(.*) (.*)" payload)
                       [oldval newval] (car/with-replies (car/get old-uuid) (car/get new-uuid))]
                   (doseq [[k watch] watches]
                     (watch k this oldval newval)))))))

    (closeListener []
      "Closes the Redis listener. Note that this will render the listener
       unusable for subsequent calls."
      (car/close-listener listener))))

(defn ^:private make-exoatom
  "Factory function for exoatoms. Takes in input a Redis key,
   the initial exoatom value and Carmine connection pool and
   connection spec."
  [key val conn-pool conn-spec]
  (let [listener (car/with-new-pubsub-listener conn-spec {})
        ^exoref.atom.IExoatom a (make-proxy key conn-pool conn-spec listener)]
    (.reset a val)
    (swap! (:state listener) assoc key
           (fn [msg]
             (when (= "message" (first msg))
               (.triggerWatches a (last msg)))))
    (car/with-open-listener listener (car/subscribe key))
    a))

(defn exoatom
  "Create and returns an instance of Exoatom with a Redis key of key,
   an initial value of val and zero or more options (in any order):
   :conn-pool carmine-conn-pool
   :conn-spec carmine-conn-spec
   :meta metadata-map
   :validator validate-fn"
  [key val & options]
  (let [conn-pool (or *conn-pool* (car/make-conn-pool))
        conn-spec (or *conn-spec* (car/make-conn-spec))
        ;; opt-map (apply hash-map options)
        ;;conn-pool (or (:conn-pool opt-map) (car/make-conn-pool))
        ;;conn-spec (or (:conn-spec opt-map) (car/make-conn-spec))
        a (make-exoatom key val conn-pool conn-spec)
        setup-reference (ns-resolve 'clojure.core 'setup-reference)]
    (setup-reference a options)))

(defn compare-and-set!!
  "Atomically sets the value of exoatom to newval if and only if the
   current value of the exoatom is identical to oldval. Returns true if
   set happened, else false"
  [^exoref.atom.IExoatom a oldval newval]
  (.compareAndSet a oldval newval))

(defn swap!!
  "Atomically swaps the value of exoatom to be:
   (apply f current-value-of-atom args). Note that f may be called
   multiple times, and thus should be free of side effects.  Returns
   the value that was swapped in."
  [^exoref.atom.IExoatom a f & args]
  (.swap a f args))

(defn reset!!
  "Sets the value of exoatom to newval without regard for the
   current value. Returns newval."
  [^exoref.atom.IExoatom a val]
  (.reset a val))

(defn close-listener!!
  "Closes the listener for the exoatom. Note that this will render the
   listener unusable for subsequent calls."
  [^exoref.atom.IExoatom a]
  (.closeListener a))
