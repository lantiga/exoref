# Exoref

Exoref aims at providing a [Redis](http://redis.io/)-based implementation of [Clojure](http://clojure.org) reference types. This is a rather natural fit given the lock-free, optimistic concurrency approach of Redis transactions.

Exoref allows to use Clojure reference types and concurrency primitives in distributed applications by storing their values on a Redis server. This enables to develop and deploy distributed applications on local setups or on PaaS providers (e.g. [Heroku](http://www.heroku.com/)) while leveraging on Clojure reference type semantics.

Exoref is based on [Carmine](https://github.com/ptaoussanis/carmine) Redis client library.

## Installation

To include Exoref in your project, simply add the following to your `project.clj` dependencies:

```clojure
[exoref "0.1.4"]
```
   
## Usage

This is work in progress. 

As of 0.1.4, Exoref provides Redis-based counterparts of 
    
* Atom (since version 0.1.0)
* Promise (since version 0.1.1)
* Delay (since version 0.1.2)

### Exoatom

An Exoatom provides a complete Redis-based implementation of a Clojure atom, including meta, validator and watches.

To instantiate an Exoatom, just make sure you have a redis server running and go:

```clojure
(ns hello-world
  (:use [exoref.connection :only [with-conn make-conn-pool make-conn-spec]]
        [exoref.atom :only [exoatom swap!! reset!! compare-and-set!!]]))

(def eatom (exoatom "some:redis:key" {:a 1 :b "hey"}))

@eatom
```

A Redis key has to be provided, in order to allow other components of the distributed system (or different workers running the same code on different machines) to access the Exoatom value.

To connect to a remote Redis server, use the `with-conn` macro in `exoref.connection` (for more details refer to [Carmine](https://github.com/ptaoussanis/carmine)):
```clojure
(def conn-pool (make-conn-pool))
(def conn-spec (make-conn-spec :host "redis://redishost.com" :port 6379 :password "changeme"))

(def eatom (with-conn conn-pool conn-spec (exoatom "some:redis:key" {:a 1 :b "hey"})))
```

The `with-conn` macro can be used with all exoref reference types.

Meta and validators are supported as in standard Clojure Atoms:
```clojure
(def eatom (exoatom "some:redis:key" {:a 1 :b "hey"} :meta {:foo "bar"} :validator #(odd? (:a %)))

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

### Exopromise

An Exopromise is a Redis-based implementation of a Clojure promise. To create a promise, go:

```clojure
(ns hello-world
  (:use [exoref.promise :only [exopromise]]))

(def epromise (exopromise "some:redis:key"))

@epromise
```

You can then dereference and deliver the exopromise as usual

```clojure
(realized? epromise) 
;; false
(deliver epromise {:a 1})
@epromise
;; {:a 1}
```

Dereferencing is blocking, as for standard promises. If two processes share a promise (i.e. two exopromises are created with the same Redis key), dereferencing in one process and delivering in the other process will unblock in both.

### Exodelay

An Exodelay is a Redis-based implementation of a Clojure delay. An exodelay can be created in separate processes with a Redis key and a body of code, or with the key alone. As soon as one of the exodelays sharing the same Redis key is dereferenced, the body is executed in one (and only one) of the processes in which the exodelay has been created with a body. All calls to `deref` will block until the body has been executed, at which point the value is cached for subsequent calls in any process.

To create a delay, go:

```clojure
(ns hello-world-1
  (:user [exoref.delay :only [exodelay]))

(def edelay-1 (exodelay "some:redis:key"))
```

and in another process

```clojure
(ns hello-world-2
  (:user [exoref.delay :only [exodelay]))

(def edelay-2 (exodelay "some:redis:key" (+ 1 2))
```

If the delay is deref'd in the first process
```clojure
@edelay-1
```

the body `(+ 1 2)` will be triggered in the second process and the deref in the first process will block until the body is done. At this point, both `@edelay-1` and `@edelay-2` will return `3`.

## Limitations

Right now exoref relies on raw Redis PubSub, which is [Fire and Forget](http://stackoverflow.com/questions/7662896/does-the-redis-pub-sub-model-require-persistent-connections-to-redis). This means that if the connection is lost temporarily, an exoref might not be notified of a change to the value of a ref. This affects atom watches, promise and delay. In particular, for the latter two, a deref might block indefinitely.

There are several ways to solve this issue on the exoref side, e.g. relying on notification queues + client registration, possibly managed through a server-side Lua script. There are of course right [tools](http://www.zeromq.org/) [for](http://activemq.apache.org/amq-message-store.html) [the](http://www.rabbitmq.com/) [job](http://clojurerabbitmq.info/), but we'll try to stick with Redis for the proof of concept and see how far it takes us.

It also looks like reliable PubSub might be a feature scheduled for future Redis releases (see [Redis docs](http://redis.io/topics/notifications) and a [couple](https://twitter.com/redisfeed/status/295854377216921600) [of](https://twitter.com/redisfeed/status/295854484339453952) tweets).


## Install to local repo

Install [lein-localrepo](https://github.com/kumarshantanu/lein-localrepo) and run 

    lein localrepo install target/exoref-0.1.x-SNAPSHOT.jar exoref/exoref 0.1.x-SNAPSHOT

after replacing `0.1.x` with the actual version number.

## Acknowledgements

Many thanks to @ptaoussanis (Peter Taoussanis) for clarifications on the use of [Carmine](https://github.com/ptaoussanis/carmine).

## Contact

For questions please contact me at luca dot antiga at [orobix](http://www.orobix.com) dot com. Pull requests are greatly welcome.

## License

Copyright © 2013 Luca Antiga.

Distributed under the Eclipse Public License, the same as Clojure.
