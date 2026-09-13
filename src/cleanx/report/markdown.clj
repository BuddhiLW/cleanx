(ns cleanx.report.markdown
  "Render a scan result as a human-readable markdown report."
  (:require [cleanx.rules.model :as m]
            [clojure.string :as str]))

(def ^:private severity-order [:critical :high :medium :low :info])

(def ^:private severity-label
  {:critical "CRITICAL"
   :high     "HIGH"
   :medium   "MEDIUM"
   :low      "LOW"
   :info     "INFO"})

(defn- group-by-severity [findings]
  (let [by (group-by :severity findings)]
    (for [s severity-order
          :let [xs (get by s)]
          :when (seq xs)]
      [s (sort-by (juxt :kind :path) xs)])))

(defn- render-finding [{:keys [rule-id path line snippet evidence remedy tool]}]
  (str "### " rule-id "\n\n"
       "- **path**: `" path "`"
       (when line (str " (line " line ")"))
       "\n"
       "- **tool**: `" tool "`\n"
       "- **evidence**: " (or evidence "—") "\n"
       "- **snippet**: `" snippet "`\n\n"
       "```sh\n" remedy "\n```\n"))

(defn- histogram [findings]
  (let [by (frequencies (map :severity findings))]
    (str/join "  "
              (for [s severity-order
                    :let [n (get by s 0)]
                    :when (pos? n)]
                (str (severity-label s) ": " n)))))

(defn render
  [{:keys [root scanned findings]}]
  (let [hist   (histogram findings)
        groups (group-by-severity findings)]
    (str "# cleanx report\n\n"
         "- **root**: `" root "`\n"
         "- **files scanned**: " scanned "\n"
         "- **findings**: " (count findings)
         (when (seq hist) (str " (" hist ")")) "\n\n"
         (if (empty? findings)
           "No findings.\n"
           (str/join
            "\n"
            (for [[sev fs] groups]
              (str "## " (severity-label sev) " (" (count fs) ")\n\n"
                   (str/join "\n" (map render-finding fs)))))))))
