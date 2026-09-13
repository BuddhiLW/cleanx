(ns cleanx.wipe.claude-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.java.io :as io]
            [cleanx.wipe.claude :as claude]
            [cleanx.wipe.util :as u]))

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

(defn- mkfile [rel content]
  (let [f (io/file *home* rel)]
    (.mkdirs (.getParentFile f))
    (spit f content)
    f))

(deftest plan-includes-history-excludes-credentials
  (testing "history.jsonl is targeted; .credentials.json is preserved"
    (mkfile ".claude/history.jsonl"       "[old conversation]")
    (mkfile ".claude/.credentials.json"   "{\"token\":\"x\"}")
    (mkfile ".claude/settings.json"       "{}")
    (mkfile ".claude/paste-cache/abc.txt" "pasted")
    (let [p (claude/plan)
          paths (into #{} (map :path) (:targets p))
          kept  (into #{} (map :path) (:preserves p))]
      (is (contains? paths (.getAbsolutePath (io/file *home* ".claude/history.jsonl"))))
      (is (contains? paths (.getAbsolutePath (io/file *home* ".claude/paste-cache"))))
      (is (contains? kept  (.getAbsolutePath (io/file *home* ".claude/.credentials.json"))))
      (is (contains? kept  (.getAbsolutePath (io/file *home* ".claude/settings.json")))))))

(deftest plan-targets-sessions-preserves-memory
  (mkfile ".claude/projects/proj-a/abc-session.jsonl"    "[transcript]")
  (mkfile ".claude/projects/proj-a/abc-session/tool-results/x.txt" "dump")
  (mkfile ".claude/projects/proj-a/tool-results/big.txt" "old")
  (mkfile ".claude/projects/proj-a/memory/MEMORY.md"     "# index")
  (let [p     (claude/plan)
        paths (into #{} (map :path) (:targets p))
        kept  (into #{} (map :path) (:preserves p))
        home  *home*]
    (is (contains? paths
                   (.getAbsolutePath (io/file home ".claude/projects/proj-a/abc-session.jsonl"))))
    (is (contains? paths
                   (.getAbsolutePath (io/file home ".claude/projects/proj-a/abc-session"))))
    (is (contains? paths
                   (.getAbsolutePath (io/file home ".claude/projects/proj-a/tool-results"))))
    (is (contains? kept
                   (.getAbsolutePath (io/file home ".claude/projects/proj-a/memory"))))
    (is (not (contains? paths
                        (.getAbsolutePath (io/file home ".claude/projects/proj-a/memory")))))))

(deftest apply-deletes-targets-only
  (mkfile ".claude/history.jsonl"     "hist")
  (mkfile ".claude/.credentials.json" "creds")
  (mkfile ".claude/settings.json"     "{}")
  (let [p (claude/plan)
        result (claude/apply! p)]
    (is (pos? (:removed-files result)))
    (is (not (.exists (io/file *home* ".claude/history.jsonl"))))
    (is (.exists (io/file *home* ".claude/.credentials.json")))
    (is (.exists (io/file *home* ".claude/settings.json")))))
