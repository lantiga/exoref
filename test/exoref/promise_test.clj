(ns exoref.promise-test
  (:use clojure.test
        exoref.promise))

(deftest exopromise-test
  (testing "Testing exopromise in a local environment."
    (let [p1 (exopromise "foo")
          p2 (exopromise "foo")]
      (is (and (= false (realized? p1)) (= false (realized? p2))))
      (deliver p1 "bar")
      (. Thread (sleep 5000))
      (is (and (= true (realized? p1)) (= true (realized? p2)))))))
