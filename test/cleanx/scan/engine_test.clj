(ns cleanx.scan.engine-test
  (:require [clojure.test :refer [deftest is testing]]
            [cleanx.scan.engine :as engine]
            [clojure.java.io :as io]
            [hive-dsl.result :as r])
(:import [java.io File]
[java.nio.file Files]
[java.nio.file.attribute FileAttribute]))

(def fixtures-root
  (.getAbsolutePath (io/file "test/cleanx/fixtures")))

;; Split literals: a verbatim AWS pair in the repo is rejected by GitHub push protection.
(def aws-fixture
  (str "aws_access_key_id = " "AKIA" "ZXCVBNM2345QRSTU" "\n"
       "aws_secret_access_key = " "wJalrXUtnFEMI/K7MDENG" "/bPxRfiCYzZ9k8mQ7xP" "\n"))

(defn- materialize-fixtures!
  "Copy the fixture tree into a fresh temp dir, add positive/aws.txt. Returns the root File."
  []
  (let [src  (io/file fixtures-root)
        root (.toFile (Files/createTempDirectory "cleanx-fixtures" (make-array FileAttribute 0)))]
    (doseq [f (file-seq src) :when (.isFile ^File f)]
      (let [dst (io/file root (str (.relativize (.toPath src) (.toPath ^File f))))]
        (io/make-parents dst)
        (io/copy f dst)))
    (spit (io/file root "positive" "aws.txt") aws-fixture)
    root))

(defn- delete-tree! [^File root]
  (doseq [^File f (reverse (file-seq root))]
    (.delete f)))

(deftest scan-fixtures
  (let [root (materialize-fixtures!)]
    (try
      (let [result (engine/run (.getAbsolutePath ^File root) {:concurrency 2})]
        (is (r/ok? result))
        (let [{:keys [findings scanned]}  (:ok result)
              paths   (into #{} (map :path) findings)
              rule-ids (into #{} (map :rule-id) findings)]
          (is (pos? scanned))
          (testing "positive fixtures produce expected rule hits"
            (is (contains? rule-ids "gitleaks.aws-access-token")
                (str "expected aws-access-token, got " rule-ids))
            (is (contains? rule-ids "gitleaks.github-pat")
                (str "expected github-pat, got " rule-ids))
            (is (contains? rule-ids "gitleaks.private-key")
                (str "expected private-key, got " rule-ids)))
          (testing "perm rule fires on the .pem file"
            (is (contains? rule-ids "cleanx.perm.pem")))
          (testing "negative fixtures produce no findings"
            (is (not-any? #(re-find #"/negative/" %) paths)
                "negative fixtures should produce no hits"))))
      (finally
        (delete-tree! root)))))
