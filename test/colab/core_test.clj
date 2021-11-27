(ns colab.core-test
  (:require [clojure.test :refer :all]
            [colab.core :refer :all]))

(use 'hashp.core)

(deftest a-test
  (testing "FIXME, I fail."
    (let [a #p (+ 1 2 3)]
      (is (= 3 a)))))
