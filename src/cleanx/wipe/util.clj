(ns cleanx.wipe.util
  "Shared helpers for wipe modules."
  (:require [clojure.java.io :as io]
            [cleanx.wipe.model :as m])
  (:import  [java.nio.file Files LinkOption]))

(def ^:private no-link (into-array LinkOption []))

(defn home [] (System/getProperty "user.home"))

(defn abs-path
  "Resolve a path relative to $HOME if not abs-patholute."
  [p]
  (if (.startsWith ^String p "/") p (str (home) "/" p)))

(defn exists? [p]
  (.exists (io/file (abs-path p))))

(defn file-bytes
  "Size of a file in bytes. 0 for missing or non-file."
  ^long [p]
  (let [f (io/file (abs-path p))]
    (if (and (.exists f) (.isFile f)) (.length f) 0)))

(defn dir-bytes
  "Recursive byte count of a directory (best effort, symlinks not followed)."
  ^long [p]
  (let [root (io/file (abs-path p))]
    (if-not (.exists root)
      0
      (try
        (with-open [s (Files/walk (.toPath root) (into-array java.nio.file.FileVisitOption []))]
          (->> (iterator-seq (.iterator s))
               (filter #(Files/isRegularFile % no-link))
               (map #(try (Files/size %) (catch Throwable _ 0)))
               (reduce + 0)))
        (catch Throwable _ 0)))))

(defn target
  "Build a WipeTarget. If path doesn't exist, returns nil (skipped)."
  [tool kind reason p]
  (let [ap (abs-path p)]
    (when (exists? ap)
      (m/->WipeTarget
       ap
       kind
       (case kind :file (file-bytes ap) :dir (dir-bytes ap))
       reason
       tool))))

(defn preserve [tool kind reason p]
  (m/->PreserveTarget (abs-path p) kind reason tool))

(defn plan
  "Build a WipePlan from a tool keyword, seq of WipeTargets, preserves, and notes."
  [tool targets preserves notes]
  (let [targets (filterv some? targets)]
    (m/->WipePlan
     tool
     targets
     (vec preserves)
     (reduce + 0 (map :size targets))
     (vec notes))))

(defn delete-recursively!
  "Delete a path recursively. Returns {:removed n :bytes b}."
  [p]
  (let [root (io/file (abs-path p))
        n    (atom 0)
        b    (atom 0)]
    (when (.exists root)
      (doseq [^java.io.File f (reverse (file-seq root))]
        (when (.isFile f)
          (swap! b + (.length f)))
        (when (.delete f)
          (swap! n inc))))
    {:removed @n :bytes @b}))
