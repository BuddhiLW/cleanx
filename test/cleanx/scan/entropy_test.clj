(ns cleanx.scan.entropy-test
  (:require [clojure.test :refer [deftest is testing]]
            [cleanx.scan.entropy :as entropy]))

(deftest empty-and-trivial
  (testing "empty string"
    (is (= 0.0 (entropy/shannon ""))))
  (testing "single char"
    (is (= 0.0 (entropy/shannon "aaaa"))))
  (testing "two equally distributed chars"
    (is (= 1.0 (entropy/shannon "abab")))))

(deftest random-vs-english
  (let [english "the quick brown fox jumps over the lazy dog"
        random  "k8ZqP3mR9vXnT1wL7jB2hC4gF6sA5dE0"
        e-eng   (entropy/shannon english)
        e-rand  (entropy/shannon random)]
    (is (< e-eng e-rand)
        (str "random (" e-rand ") should beat english (" e-eng ")"))))

(deftest above-threshold
  (is (entropy/above? "abcdef" nil)
      "nil threshold always passes")
  (is (not (entropy/above? "aaaaaa" 2.0))
      "low entropy fails a 2.0 threshold")
  (is (entropy/above? "k8ZqP3mR9vXnT1wL7jB2hC4gF6sA5dE0" 4.0)
      "high entropy string passes a 4.0 threshold"))
