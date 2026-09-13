(ns cleanx.report.finding
  "Finding constructors + redaction."
  (:require [cleanx.report.remedy :as remedy])
  (:import  [java.time Instant]
            [java.util UUID]))

(defn redact
  "Never show a full secret. Show a 4-char prefix plus length marker.
   Redaction is stable: same input → same redacted string."
  [^String s]
  (if (or (nil? s) (< (count s) 6))
    "****"
    (str (subs s 0 4) "…(" (count s) " chars)")))

(defn- ts [] (Instant/now))

(defn make-secret-finding
  [{:keys [rule-id severity path line match secret evidence tool]}]
  (let [f {:id       (UUID/randomUUID)
           :severity (or severity :high)
           :kind     :secret
           :rule-id  rule-id
           :path     path
           :line     line
           :snippet  (redact (or secret match))
           :evidence evidence
           :tool     (or tool "cleanx.regex")
           :ts       (ts)}]
    (assoc f :remedy (remedy/for-finding f))))

(defn make-perm-finding
  [{:keys [path severity kind mode max-mode evidence]}]
  (let [f {:id       (UUID/randomUUID)
           :severity severity
           :kind     :perm
           :rule-id  (str "cleanx." (name kind))
           :path     path
           :line     nil
           :snippet  (format "mode=%04o" mode)
           :evidence evidence
           :tool     "cleanx.perms"
           :ts       (ts)}]
    (assoc f :remedy (remedy/for-finding (assoc f :mode mode :max-mode max-mode)))))
