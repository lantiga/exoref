(ns exoref.atom-test
  (:use clojure.test
        exoref.atom))

(defn stress-exoatom [n]
  (let [a (exoatom (uuid) {:bar 0})
        f (partial swap!! a update-in [:bar] inc)]
    (doall (apply pcalls (repeat n f)))
    (is (= (:bar @a) n))))

(deftest exoatom-stress-test
  (testing "Testing inc on 100 concurrent threads. Test it 100 times."
    (doall (repeatedly 100 (partial stress-exoatom 100)))))

(deftest exoatom-functions
  (testing "Testing compare-and-set!! reset!! swap!! and deref"
    (let [a (exoatom "foo" 1)]
      (is (true? (compare-and-set!! a 1 2)))
      (is (not (true? (compare-and-set!! a 1 2)))))
    (let [a (exoatom "foo" 1)]
      (reset!! a 2)
      (is (= 2 @a)))
    (let [a (exoatom "foo" 1)]
      (swap!! a (constantly 3))
      (is (= 3 @a)))))

(deftest exoatom-meta-test
  (testing "Setting and getting meta"
    (let [a (exoatom "foo" 1 :meta {:a 1})]
      (is (= 1 (:a (meta a)))))
    (let [a (exoatom "foo" 1)]
      (is (nil? (meta a)))
      (reset-meta! a {:a 1})
      (is (= 1 (:a (meta a)))))))

(deftest exoatom-validator-test
  (testing "Validator"
    (let [a (exoatom "foo" 1 :validator #(= 1 %))]
      (is (thrown? IllegalStateException (reset!! a 2))))
    (let [a (exoatom "foo" 1)]
      (is (thrown? IllegalStateException (set-validator! a #(= 2 %)))))
    (let [a (exoatom "foo" 1)]
      (set-validator! a #(= 1 %))
      (is (thrown? IllegalStateException (compare-and-set!! a 1 2)))
      (is (thrown? IllegalStateException (reset!! a 2)))
      (is (thrown? IllegalStateException (swap!! a (constantly 2))))
      (set-validator! a #(or (= 1 %) (= 2 %)))
      (reset!! a 2)
      (is (= 2 @a)))))

(deftest exoatom-watch-test
  (testing "Watches"
    (let [a (exoatom "foo" 1)
          watch-ret (promise)]
      (add-watch a "watch-key" 
                 (fn [k r oldval newval]
                   (deliver watch-ret [k oldval newval])))
      (reset!! a 2)
      (is (= ["watch-key" 1 2] @watch-ret)))
    (let [a (exoatom "foo" 1)
          watch-ret (promise)]
      (add-watch a "watch-key" 
                 (fn [k r oldval newval]
                   (deliver watch-ret [k oldval newval])))
      (swap!! a (constantly 2))
      (is (= ["watch-key" 1 2] @watch-ret)))
    (let [a (exoatom "foo" 1)
          watch-ret (promise)]
      (add-watch a "watch-key" 
                 (fn [k r oldval newval]
                   (deliver watch-ret [k oldval newval])))
      (compare-and-set!! a 1 2)
      (is (= ["watch-key" 1 2] @watch-ret)))))
