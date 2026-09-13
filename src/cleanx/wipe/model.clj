(ns cleanx.wipe.model
  "Data model for wipe plans.

   A WipeTarget is a single path the wiper wants to remove (file or dir).
   A PreserveTarget is a path that MUST NOT be touched — listed for auditability.
   A WipePlan is produced per tool and aggregates targets, preserves, total bytes.")

(defrecord WipeTarget
  [path          ; absolute path string
   kind          ; :file | :dir
   size          ; bytes (for files, 0 for dirs until walked)
   reason        ; human-readable: "conversation history" / "paste cache" / ...
   tool])        ; :claude | :aider | :ollama | :shell

(defrecord PreserveTarget
  [path kind reason tool])

(defrecord WipePlan
  [tool          ; :claude | ...
   targets       ; [WipeTarget]
   preserves     ; [PreserveTarget]
   total-bytes   ; long
   notes])       ; seq of string advisories

(defn empty-plan [tool]
  (->WipePlan tool [] [] 0 []))

(defn merge-plans [plans]
  (->WipePlan :multi
              (into [] (mapcat :targets plans))
              (into [] (mapcat :preserves plans))
              (reduce + 0 (map :total-bytes plans))
              (into [] (mapcat :notes plans))))
