(ns colab.core-test
  (:require [clojure.test :refer :all]
            [colab.core :refer :all]))

(use 'hashp.core)

(defn meaning-of-life
  []
  {:answer 42})

(deftest meaning-of-life-test
  (testing "FIXME, I fail."
    (let [a #p (meaning-of-life)]
      (is (= {:answer 42} a)))))
