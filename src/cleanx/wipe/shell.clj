(ns cleanx.wipe.shell
  "Shell history wiper. Special case: instead of deleting outright, scan each
   history file for secret-shaped lines using the cleanx regex engine and either
   (a) report redactable lines in the plan, or (b) on --apply, rewrite the file
   with offending lines removed.

   Targets ~/.bash_history and ~/.zsh_history by default."
  (:require [cleanx.rules.loader :as loader]
            [cleanx.scan.regex :as rx]
            [cleanx.wipe.util :as u]
            [cleanx.wipe.model :as m]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [hive-dsl.result :as r]))

(def tool :shell)

(def ^:private history-files
  [[".bash_history" "bash history"]
   [".zsh_history"  "zsh history"]])

(defn- sensitive-lines
  "Return a set of line numbers (1-based) in content that match any rule."
  [rules content]
  (when content
    (let [hits (rx/scan-content rules "/shell-history" content)]
      (into (sorted-set) (map :line) hits))))

(defn plan
  "Build a WipePlan. Each target's :reason includes the count of sensitive lines."
  []
  (let [rules-r (loader/load-default-rules)
        rules   (if (r/ok? rules-r) (:ok rules-r) {:rules [] :global-allowlist {}})
        targets (for [[p label] history-files
                      :when     (u/exists? p)]
                  (let [ap (u/abs-path p)
                        content (try (slurp ap) (catch Throwable _ nil))
                        lines (sensitive-lines rules content)]
                    (m/->WipeTarget
                     ap
                     :file
                     (u/file-bytes ap)
                     (format "%s (%d sensitive line(s))" label (count lines))
                     tool)))]
    (u/plan tool
            targets
            []
            ["On --apply, sensitive lines are removed in place. File itself is preserved."])))

(defn- redact-file!
  "Rewrite file keeping only non-sensitive lines. Returns {:removed-lines n}."
  [rules path]
  (let [content (slurp path)
        lines   (str/split-lines content)
        bad     (sensitive-lines rules content)
        keepers (into [] (keep-indexed (fn [i l] (when-not (contains? bad (inc i)) l)) lines))
        out     (str (str/join "\n" keepers) "\n")]
    (spit path out)
    {:removed-lines (count bad)}))

(defn apply! [plan]
  (let [rules-r (loader/load-default-rules)
        rules   (if (r/ok? rules-r) (:ok rules-r) nil)]
    (if-not rules
      {:error :shell/rules-unavailable}
      (reduce
       (fn [acc t]
         (let [{:keys [removed-lines]} (redact-file! rules (:path t))]
           (-> acc
               (update :redacted-files inc)
               (update :removed-lines + removed-lines))))
       {:redacted-files 0 :removed-lines 0}
       (:targets plan)))))
