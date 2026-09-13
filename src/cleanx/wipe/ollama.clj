(ns cleanx.wipe.ollama
  "Wipe plan for Ollama prompt history. Preserves model blobs and keys."
  (:require [cleanx.wipe.util :as u]))

(def tool :ollama)

(def ^:private targets
  [[".ollama/history"    "prompt history"]
   [".ollama/logs"       "runtime logs"]])

(def ^:private preserved
  [[".ollama/id_ed25519"      "client private key"]
   [".ollama/id_ed25519.pub"  "client public key"]
   [".ollama/models"          "model blobs"]])

(defn plan []
  (let [top   (for [[p reason] targets :when (u/exists? p)]
                (u/target tool :file reason p))
        keeps (for [[p reason] preserved]
                (u/preserve tool :auto reason p))]
    (u/plan tool top keeps
            ["Only history/logs removed; model blobs and keys are preserved."])))

(defn apply! [plan]
  (reduce
   (fn [acc t]
     (let [r (u/delete-recursively! (:path t))]
       (-> acc
           (update :removed-files + (:removed r))
           (update :freed-bytes + (:bytes r)))))
   {:removed-files 0 :freed-bytes 0}
   (:targets plan)))
