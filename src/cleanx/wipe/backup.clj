(ns cleanx.wipe.backup
  "Snapshot wipe targets into a tar.zst archive before deletion."
  (:require [hive-dsl.result :as r]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh])
  (:import  [java.time LocalDateTime]
            [java.time.format DateTimeFormatter]))

(def ^:private ^DateTimeFormatter ts-fmt
  (DateTimeFormatter/ofPattern "yyyyMMdd-HHmmss"))

(defn- now-stamp [] (.format (LocalDateTime/now) ts-fmt))

(defn default-backup-dir []
  (let [home (System/getProperty "user.home")]
    (str home "/.local/share/cleanx/backups")))

(defn- ensure-dir! [dir]
  (let [f (io/file dir)]
    (when-not (.exists f) (.mkdirs f))))

(defn snapshot!
  "Create a tar.zst archive at backup-dir/cleanx-<tool>-<ts>.tar.zst
   containing every path in paths. Returns Result{:ok {:archive :bytes}}.

   Uses GNU tar + zstd via /usr/bin/tar, must be on PATH."
  [tool paths {:keys [backup-dir] :or {backup-dir (default-backup-dir)}}]
  (try
    (ensure-dir! backup-dir)
    (let [stamp   (now-stamp)
          archive (str backup-dir "/cleanx-" (name tool) "-" stamp ".tar.zst")
          paths   (filter #(.exists (io/file %)) paths)]
      (if (empty? paths)
        (r/ok {:archive nil :bytes 0 :skipped :no-paths})
        (let [args (concat ["tar" "--zstd" "-cf" archive "--ignore-failed-read"
                            "-C" "/"]
                           (map #(subs % 1) paths))
              {:keys [exit err]} (apply sh/sh args)]
          (if (zero? exit)
            (r/ok {:archive archive
                   :bytes   (.length (io/file archive))})
            (r/err :backup/tar-failed {:exit exit :stderr err :archive archive})))))
    (catch Throwable t
      (r/err :backup/failed {:message (ex-message t)}))))
