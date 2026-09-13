(ns cleanx.scan.engine
  "Top-level scan orchestration. Loads rules, walks the tree, runs regex + perms,
   returns a unified list of Findings."
  (:require [cleanx.rules.loader :as loader]
            [cleanx.rules.model :as m]
            [cleanx.scan.fs :as fs]
            [cleanx.scan.perms :as perms]
            [cleanx.scan.regex :as regex-scan]
            [cleanx.report.finding :as finding]
            [hive-dsl.result :as r]
            [hive-weave.parallel :as weave]))

(defn- hit->finding [{:keys [rule-id rule start line match secret] :as _hit} path]
  (finding/make-secret-finding
   {:rule-id  (str "gitleaks." rule-id)
    :severity (or (:severity rule) :high)
    :path     path
    :line     line
    :match    match
    :secret   secret
    :evidence (:description rule)
    :tool     "cleanx.regex"}))

(defn- scan-file [rules stat]
  (let [{:keys [path]} stat
        read-result    (fs/read-text path)]
    (if (r/err? read-result)
      []
      (let [content (:ok read-result)
            hits    (regex-scan/scan-content rules path content)]
        (mapv #(hit->finding % path) hits)))))

(defn- perm->finding [v]
  (finding/make-perm-finding
   {:path     (:path v)
    :severity (:severity v)
    :kind     (:kind v)
    :mode     (:mode v)
    :max-mode (:max-mode v)
    :evidence (:evidence v)}))

(defn run
  "Scan a directory tree. Returns Result<{:findings [Finding] :scanned n :root root}>.

   opts:
     :rules        — loaded rule set (defaults to loading vendored gitleaks.toml)
     :concurrency  — bounded-pmap workers (default 8)
     :timeout-ms   — per-file timeout (default 10000)
     :walk-opts    — passed to fs/walk"
  ([root] (run root {}))
  ([root {:keys [rules concurrency timeout-ms walk-opts]
          :or   {concurrency 8 timeout-ms 10000 walk-opts {}}}]
   (let [rules-r (or (some-> rules r/ok) (loader/load-default-rules))]
     (if (r/err? rules-r)
       rules-r
       (let [rule-set (:ok rules-r)
             stats    (fs/walk root walk-opts)
             regex-findings
             (into []
                   cat
                   (weave/bounded-pmap
                    {:concurrency concurrency
                     :timeout-ms  timeout-ms
                     :fallback    []}
                    (partial scan-file rule-set)
                    stats))
             perm-findings (mapv perm->finding (perms/audit stats))]
         (r/ok {:root      (str root)
                :scanned   (count stats)
                :findings  (into [] (concat regex-findings perm-findings))}))))))
