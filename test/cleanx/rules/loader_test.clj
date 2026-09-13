(ns cleanx.rules.loader-test
  (:require [clojure.test :refer [deftest is testing]]
            [cleanx.rules.loader :as loader]
            [cleanx.rules.model :as m]
            [hive-dsl.result :as r]))

(deftest load-default-rules
  (testing "vendored gitleaks.toml parses"
    (let [result (loader/load-default-rules)]
      (is (r/ok? result) (str "expected ok, got " result))
      (let [{:keys [rules global-allowlist]} (:ok result)]
        (is (> (count rules) 100)
            (str "expected at least 100 rules, got " (count rules)))
        (is (every? #(instance? cleanx.rules.model.Rule %) rules))
        (is (every? #(some? (:regex %)) rules))
        (is (seq (:path-patterns global-allowlist)))))))

(deftest each-rule-has-id-and-description
  (let [{:keys [rules]} (:ok (loader/load-default-rules))]
    (doseq [rule rules]
      (is (string? (:id rule)))
      (is (string? (:description rule))))))
