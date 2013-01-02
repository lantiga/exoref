# Exoref

Exoref aims at providing a [Redis](http://redis.io/)-based implementation of [Clojure](http://clojure.org) reference types. This is a rather natural fit given the lock-free, optimistic concurrency approach of Redis transactions.

Exoref allows to use Clojure reference types and concurrency primitives in distributed applications by storing their values on a Redis server. This enables to develop and deploy distributed applications on local setups or on PaaS providers (e.g. [Heroku](http://www.heroku.com/)) while leveraging on Clojure reference type semantics.

Exoref is based on [Carmine](https://github.com/ptaoussanis/carmine) Redis client library.

## Installation

To include Exoref in your project, simply add the following to your `project.clj` dependencies:

   [exoref "0.1.0"]
   
## Usage

This is work in progress. 

As of 0.1.0, Exoref provides a full-featured Clojure Atom implementation. 

Promises are around the corner. Other reference types will come with time.

### Exoatom

An Exoatom provides a complete Redis-based implementation of Clojure atoms, including meta, validator and watches.

To instantiate an Exoatom, just make sure you have a redis server running and go:

```clojure
(ns hello-world
  (:require [exoref.atom :as exo]))

(def eatom (exo/exoatom "some:redis:key" {:a 1 :b "hey"}))
```

A Redis key has to be provided, in order to allow other components of the distributed system (or different workers running the same code on different machines) to access the Exoatom value.

To connect to a remote Redis server, provide a connection spec map (as in [Carmine](https://github.com/ptaoussanis/carmine)):
```clojure
(def conn-spec {host "redis://redishost.com" port 6379 password "changeme" timeout 0 db 0}

(def eatom (exo/exoatom "some:redis:key" {:a 1 :b "hey"} :conn-spec conn-spec))
```

Meta and validators are supported as in standard Clojure Atoms:
```clojure
(def eatom (exo/exoatom "some:redis:key" {:a 1 :b "hey"} :meta {:foo "bar"} :validator #(odd? (:a %)))

(reset-meta! eatom {:foo "biz"})

(set-validator! eatom #(= "hey" (:b %)))
```

As well as watches:

```clojure
(add-watch eatom "watch-key" 
  (fn [k r oldval newval] (prn [k oldval newval])))
```

To change the value of the Exoatom use double-bang (as in [Avout](https://github.com/liebke/avout)) version of the atom functions, namely `compare-and-set!!`, `swap!!`, `reset!!`:

```clojure
(swap!! eatom update-in [:a] inc)

(reset!! eatom {:c "foo"})

(compare-and-set!! eatom {:c "foo"} {:c "bar"})
```

## Acknowledgements

Many thanks to @ptaoussanis (Peter Taoussanis) for clarifications on the use of [Carmine](https://github.com/ptaoussanis/carmine).

## Contact

For questions please contact me at (Luca Antiga) at [orobix.com](http://www.orobix.com). Pull requests are greatly welcome.

## License

Copyright © 2013 Luca Antiga.

Distributed under the Eclipse Public License, the same as Clojure.
