(ns cleanx.core
  "Entry point: dispatch scan / report / wipe subcommands."
  (:require [cleanx.cli :as cli]
            [cleanx.rules.loader :as loader]
            [cleanx.scan.engine :as engine]
            [cleanx.report.markdown :as md]
            [cleanx.report.json :as jsonr]
            [cleanx.report.wipe :as wipe-report]
            [cleanx.wipe.engine :as wipe-engine]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [hive-dsl.result :as r])
  (:gen-class))

(defn- render [format result]
  (case format
    "md"   (md/render result)
    "json" (jsonr/render-json result)
    "edn"  (jsonr/render-edn result)))

(defn- default-out-path [root format]
  (let [ts (-> (java.time.LocalDateTime/now)
               (.format (java.time.format.DateTimeFormatter/ofPattern "yyyyMMdd-HHmmss")))
        ext (case format "md" "md" "json" "json" "edn" "edn")]
    (str "./cleanx-report-" ts "." ext)))

(defn- write-out! [out text]
  (if out
    (do (spit out text)
        (println "Report written to" out))
    (println text)))

(defn- rules-for
  [{:keys [rules]}]
  (if rules
    (loader/load-rules rules)
    (loader/load-default-rules)))

(defn cmd-scan [argv]
  (let [{:keys [options root errors summary]} (cli/parse-scan argv)]
    (cond
      (:help options)
      (do (println "cleanx scan [PATH] [options]\n")
          (println summary))

      errors
      (do (run! println errors) (System/exit 2))

      :else
      (let [rules-r (rules-for options)]
        (if (r/err? rules-r)
          (do (println "Failed to load rules:" rules-r) (System/exit 3))
          (let [result-r (engine/run
                          root
                          {:rules       (:ok rules-r)
                           :concurrency (:concurrency options)
                           :timeout-ms  (:timeout-ms options)
                           :walk-opts   {:max-size (:max-size options)}})]
            (if (r/err? result-r)
              (do (println "Scan failed:" result-r) (System/exit 4))
              (let [result   (:ok result-r)
                    format   (:format options)
                    rendered (render format result)
                    out      (or (:out options)
                                 (default-out-path root format))]
                (write-out! out rendered)
                (when (seq (:findings result))
                  (println "Found" (count (:findings result)) "issues."))))))))))

(defn cmd-report
  "Re-render an existing scan result (edn) into a different format."
  [argv]
  (let [[from & _] argv]
    (if-not from
      (do (println "cleanx report --from FILE [--format md|json|edn]") (System/exit 2))
      (let [result (edn/read-string (slurp from))]
        (write-out! nil (md/render result))))))

(defn cmd-wipe [argv]
  (let [{:keys [options tools errors summary]} (cli/parse-wipe argv)]
    (cond
      (:help options)
      (do (println "cleanx wipe [TOOL...] [--apply] [--no-backup] [--backup-dir DIR]\n")
          (println "Tools:" (str/join ", " (map name (wipe-engine/known-tools))))
          (println "Default: all tools, dry-run only.\n")
          (println summary))

      errors
      (do (run! println errors) (System/exit 2))

      :else
      (let [plans-r (wipe-engine/plan tools)]
        (if (r/err? plans-r)
          (do (println "Plan failed:" plans-r) (System/exit 3))
          (let [plans (:ok plans-r)]
            (if (:apply options)
              (let [apply-r (wipe-engine/apply!
                             plans
                             {:backup?    (not (:no-backup options))
                              :backup-dir (:backup-dir options)})]
                (if (r/err? apply-r)
                  (do (println "Apply failed:" apply-r) (System/exit 4))
                  (let [text (wipe-report/render plans :applied (:ok apply-r))]
                    (write-out! (:out options) text))))
              (let [text (wipe-report/render plans :dry-run nil)]
                (write-out! (:out options) text)))))))))

(defn -main [& args]
  (let [[cmd & rest] args]
    (case cmd
      "scan"   (cmd-scan rest)
      "report" (cmd-report rest)
      "wipe"   (cmd-wipe rest)
      (do (println "Usage: cleanx {scan|report|wipe} [args]") (System/exit 1)))))
