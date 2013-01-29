(ns exoref.delay-test
  (:use clojure.test
        exoref.delay))

(deftest exodelay-test
  (testing "Testing exodelay in a local environmnet"
    (let [d1 (exodelay "foo:delay")
          d2 (exodelay "foo:delay" (reduce + (range 1000)))]
      (prn "A")
      (is (and (= false (realized? d1)) (= false (realized? d2))))
      (prn "B")
      (future (deref d1))
      (. Thread (sleep 5000))
      (prn "C")
      (is (= true (realized? d1)))
      (is (= true (realized? d2)))
      (is (= @d1 (reduce + (range 1000))))
      (is (= @d2 (reduce + (range 1000)))))))

;; TODO: test force
