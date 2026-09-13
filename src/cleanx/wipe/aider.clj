(ns cleanx.wipe.aider
  "Wipe plan for Aider's per-repo history and caches."
  (:require [cleanx.wipe.util :as u]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import  [java.io File]))

(def tool :aider)

(def ^:private global-targets
  [[".aider.chat.history.md"    "global chat history"]
   [".aider.input.history"      "global input history"]
   [".aider.llm.history"        "global llm log"]
   [".aider.tags.cache.v3"      "tags cache"]])

(def ^:private preserved
  [[".aider.conf.yml"           "user config"]])

(defn- per-repo-targets
  "Look under ~/PP/ for project-local .aider.* files created by aider invocations."
  []
  (let [pp (io/file (u/abs-path "PP"))]
    (when (.exists pp)
      (for [^File proj (file-seq pp)
            :when      (and (.isFile proj)
                            (let [n (.getName proj)]
                              (or (str/starts-with? n ".aider.chat.")
                                  (str/starts-with? n ".aider.input.")
                                  (str/starts-with? n ".aider.llm."))))]
        (.getAbsolutePath proj)))))

(defn plan []
  (let [top   (for [[p reason] global-targets :when (u/exists? p)]
                (u/target tool :file reason p))
        repo  (for [p (per-repo-targets)]
                (u/target tool :file "per-repo aider history" p))
        keeps (for [[p reason] preserved]
                (u/preserve tool :file reason p))]
    (u/plan tool (concat top repo) keeps
            ["Aider config is preserved; only history/cache files are removed."])))

(defn apply! [plan]
  (reduce
   (fn [acc t]
     (let [r (u/delete-recursively! (:path t))]
       (-> acc
           (update :removed-files + (:removed r))
           (update :freed-bytes + (:bytes r)))))
   {:removed-files 0 :freed-bytes 0}
   (:targets plan)))
