(ns cleanx.wipe.engine
  "Top-level wipe orchestration. Dispatches per-tool plan+apply, takes a
   tar.zst backup via cleanx.wipe.backup before applying."
  (:require [cleanx.wipe.aider  :as aider]
            [cleanx.wipe.backup :as backup]
            [cleanx.wipe.claude :as claude]
            [cleanx.wipe.model  :as m]
            [cleanx.wipe.ollama :as ollama]
            [cleanx.wipe.shell  :as shell]
            [hive-dsl.result    :as r]))

(def registry
  "Registered wipers, in canonical order. Each entry maps a keyword to
   {:plan fn :apply fn}."
  {:claude  {:plan  claude/plan  :apply claude/apply!}
   :aider   {:plan  aider/plan   :apply aider/apply!}
   :ollama  {:plan  ollama/plan  :apply ollama/apply!}
   :shell   {:plan  shell/plan   :apply shell/apply!}})

(defn known-tools [] (keys registry))

(defn- select-wipers [tools]
  (let [tools (if (or (nil? tools) (empty? tools)) (known-tools) tools)]
    (for [k tools :when (contains? registry k)] k)))

(defn plan
  "Compute plans for the requested tools. Returns Result<seq WipePlan>.
   tools: nil | [] | seq of keywords. nil/empty = all registered."
  ([tools]
   (try
     (r/ok (mapv (fn [k] ((:plan (registry k)))) (select-wipers tools)))
     (catch Throwable t
       (r/err :wipe/plan-failed {:message (ex-message t)})))))

(defn- apply-one!
  [plan {:keys [backup? backup-dir]}]
  (let [tool  (:tool plan)
        paths (mapv :path (:targets plan))
        back  (when backup?
                (backup/snapshot! tool paths {:backup-dir backup-dir}))]
    (if (and backup? (r/err? back))
      {:tool tool :error back}
      (let [apply-fn (:apply (registry tool))
            result   (apply-fn plan)]
        {:tool    tool
         :backup  (when backup? (:ok back))
         :result  result}))))

(defn apply!
  "Apply a seq of wipe plans. Takes a tar.zst backup per-tool unless :backup? false."
  [plans opts]
  (try
    (r/ok (mapv #(apply-one! % opts) plans))
    (catch Throwable t
      (r/err :wipe/apply-failed {:message (ex-message t)}))))
