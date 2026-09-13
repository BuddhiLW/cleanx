(ns cleanx.report.wipe
  "Render a wipe plan (or multiple) as markdown."
  (:require [clojure.string :as str]))

(defn- human-bytes [^long n]
  (cond
    (< n 1024)                (str n " B")
    (< n (* 1024 1024))       (format "%.1f KB" (/ n 1024.0))
    (< n (* 1024 1024 1024))  (format "%.1f MB" (/ n 1024.0 1024.0))
    :else                     (format "%.2f GB" (/ n 1024.0 1024.0 1024.0))))

(defn- render-plan [{:keys [tool targets preserves total-bytes notes]}]
  (str "## " (name tool) " — " (count targets) " target(s), "
       (human-bytes total-bytes) "\n\n"
       (if (seq targets)
         (str "### Will remove\n\n"
              (str/join "\n"
                        (for [t targets]
                          (format "- `%s`  _(%s, %s)_"
                                  (:path t)
                                  (name (:kind t))
                                  (human-bytes (:size t)))))
              "\n\n")
         "_Nothing to remove._\n\n")
       (when (seq preserves)
         (str "### Preserved\n\n"
              (str/join "\n"
                        (for [p preserves]
                          (format "- `%s`  _(%s)_" (:path p) (:reason p))))
              "\n\n"))
       (when (seq notes)
         (str "### Notes\n\n"
              (str/join "\n" (map #(str "- " %) notes))
              "\n\n"))))

(defn render
  "Render a seq of WipePlan (or a single one). mode = :dry-run or :applied."
  ([plans] (render plans :dry-run nil))
  ([plans mode apply-results]
   (let [plans (if (sequential? plans) plans [plans])
         head  (case mode
                 :dry-run "# cleanx wipe — dry run\n\n_No files removed. Use `--apply` to execute._\n\n"
                 :applied "# cleanx wipe — applied\n\n")
         total (reduce + 0 (map :total-bytes plans))
         body  (str/join "\n" (map render-plan plans))
         tail  (when (and (= mode :applied) apply-results)
                 (str "\n## Apply results\n\n"
                      (str/join "\n"
                                (for [r apply-results]
                                  (format "- **%s**: %s"
                                          (name (:tool r))
                                          (pr-str (dissoc r :tool)))))
                      "\n"))]
     (str head
          "**Total planned**: " (human-bytes total) "\n\n"
          body
          tail))))
