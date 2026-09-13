(ns cleanx.cli
  "CLI argument parsing for cleanx."
  (:require [clojure.tools.cli :as cli]))

(def scan-options
  [["-o" "--out FILE"     "Output file for the report"
    :default nil]
   ["-f" "--format FMT"   "Output format: md | json | edn"
    :default "md"
    :validate [#{"md" "json" "edn"} "must be md, json, or edn"]]
   [nil  "--concurrency N" "Parallel workers"
    :default 8 :parse-fn #(Integer/parseInt %)]
   [nil  "--timeout-ms N"  "Per-file timeout (ms)"
    :default 10000 :parse-fn #(Integer/parseInt %)]
   [nil  "--max-size N"    "Skip files larger than N bytes"
    :default (* 2 1024 1024) :parse-fn #(Integer/parseInt %)]
   [nil  "--rules FILE"    "Use custom gitleaks-format rules file"
    :default nil]
   ["-h" "--help"          "Show help"]])

(defn parse-scan [argv]
  (let [{:keys [options arguments errors summary] :as parsed}
        (cli/parse-opts argv scan-options)]
    (assoc parsed
           :root (or (first arguments) "."))))

(def wipe-options
  [[nil  "--apply"          "Actually delete files (default: dry run)"
    :default false]
   [nil  "--no-backup"      "Skip tar.zst backup before deletion"
    :default false]
   [nil  "--backup-dir DIR" "Override backup directory"
    :default nil]
   ["-o" "--out FILE"        "Output file for the report"
    :default nil]
   ["-f" "--format FMT"      "Output format: md | json | edn"
    :default "md"
    :validate [#{"md" "json" "edn"} "must be md, json, or edn"]]
   ["-h" "--help"            "Show help"]])

(defn parse-wipe [argv]
  (let [{:keys [options arguments] :as parsed}
        (cli/parse-opts argv wipe-options)]
    (assoc parsed :tools (mapv keyword arguments))))
