(ns cleanx.wipe.shell-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.java.io :as io]
            [cleanx.wipe.shell :as sh]))

(def ^:dynamic *home* nil)

(defn- with-fake-home [f]
  (let [tmp (doto (java.io.File/createTempFile "cleanx-home" "")
              (.delete))
        _ (.mkdirs tmp)
        prev (System/getProperty "user.home")]
    (try
      (System/setProperty "user.home" (.getAbsolutePath tmp))
      (binding [*home* (.getAbsolutePath tmp)]
        (f))
      (finally
        (System/setProperty "user.home" prev)
        (doseq [file (reverse (file-seq tmp))] (.delete file))))))

(use-fixtures :each with-fake-home)

(defn- spit-home [rel content]
  (spit (doto (io/file *home* rel) (-> .getParentFile .mkdirs)) content))

(deftest plan-finds-history-files
  (spit-home ".bash_history" "ls -la\necho hi\n")
  (let [p (sh/plan)
        paths (map :path (:targets p))]
    (is (some #(.endsWith ^String % ".bash_history") paths))))

(deftest apply-redacts-sensitive-lines
  (spit-home ".zsh_history"
             (str "ls -la\n"
                  "export GITHUB_TOKEN=ghp_ABCdef0123456789GHIjklMNOpqrSTU4567vwx\n"
                  "echo hi\n"))
  (let [plan (sh/plan)
        _    (sh/apply! plan)
        body (slurp (io/file *home* ".zsh_history"))]
    (is (not (re-find #"ghp_" body))
        "sensitive line should be removed")
    (is (re-find #"echo hi" body)
        "non-sensitive lines should be preserved")))
