(ns cleanx.wipe.claude
  "Wipe plan for Claude Code local accumulation.

   Removes: conversation history, paste cache, telemetry, session env,
            shell snapshots, file-history, per-project tool-results.
   Preserves: credentials, settings, memory, plans, custom agents, hooks."
  (:require [cleanx.wipe.util :as u]
            [clojure.java.io :as io])
  (:import  [java.io File]))

(def tool :claude)

(def ^:private file-reasons
  [[".claude/history.jsonl"         "global cross-session history"]
   [".claude/shell-snapshots"       "captured shell env per session"]
   [".claude/session-env"           "per-session environment dumps"]
   [".claude/file-history"          "per-session edited file snapshots"]
   [".claude/paste-cache"           "pasted content cache"]
   [".claude/telemetry"             "client telemetry queue"]
   [".claude/tasks"                 "ephemeral per-session task lists"]
   [".claude/sessions"              "session metadata"]
   [".claude/cache"                 "misc in-client cache"]
   [".cache/claude-cli-nodejs"      "Node CLI cache (outside ~/.claude)"]])

(def ^:private preserved
  [[".claude/.credentials.json"      "API key / OAuth token"]
   [".claude/settings.json"          "user settings"]
   [".claude/settings.local.json"    "local settings"]
   [".claude/plugins"                "installed plugins"]
   [".claude/statsig"                "feature flag cache"]
   [".local/share/claude/versions"   "installed CLI versions"]])

(def ^:private project-preserve-names
  "Subdirectory names inside ~/.claude/projects/<proj>/ to preserve."
  #{"memory"})

(defn- project-session-targets
  "Return session transcripts + UUID session dirs under ~/.claude/projects/*/.
   Preserves anything named in project-preserve-names (memory)."
  []
  (let [root (io/file (u/abs-path ".claude/projects"))]
    (when (.exists root)
      (for [^File proj (.listFiles root)
            :when      (.isDirectory proj)
            ^File child (.listFiles proj)
            :when      (not (contains? project-preserve-names (.getName child)))]
        (.getAbsolutePath child)))))

(defn- memory-dirs
  "Per-project memory directories that must be preserved."
  []
  (let [root (io/file (u/abs-path ".claude/projects"))]
    (when (.exists root)
      (for [^File proj (.listFiles root)
            :when      (.isDirectory proj)
            :let       [m (io/file proj "memory")]
            :when      (.exists m)]
        (.getAbsolutePath m)))))

(defn plan []
  (let [kind-of   (fn [p] (if (.isDirectory (io/file (u/abs-path p))) :dir :file))
        top       (for [[p reason] file-reasons
                        :when      (u/exists? p)]
                    (u/target tool (kind-of p) reason p))
        sessions  (for [p (project-session-targets)]
                    (u/target tool
                              (if (.isDirectory (io/file p)) :dir :file)
                              "per-session conversation data"
                              p))
        keeps     (concat
                   (for [[p reason] preserved] (u/preserve tool :auto reason p))
                   (for [m (memory-dirs)]
                     (u/preserve tool :dir "per-project auto-memory" m)))
        notes     ["Running Claude Code sessions will be unaffected only if idle."
                   "Wipe does not revoke the API key — rotate separately."
                   "Per-project auto-memory is preserved (data survives)."]]
    (u/plan tool (concat top sessions) keeps notes)))

(defn apply!
  "Delete every target in this plan. Does NOT take a backup — caller does."
  [plan]
  (reduce
   (fn [acc t]
     (let [r (u/delete-recursively! (:path t))]
       (-> acc
           (update :removed-files + (:removed r))
           (update :freed-bytes + (:bytes r)))))
   {:removed-files 0 :freed-bytes 0}
   (:targets plan)))
