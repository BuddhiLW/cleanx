(ns cleanx.scan.regex
  "Regex rule engine: run a set of Rules over a file's text content."
  (:require [cleanx.rules.model :as m]
            [cleanx.scan.entropy :as entropy])
  (:import  [java.util.regex Matcher Pattern]))

(defn- path-allowed?
  "True if file path matches any allowlist path-pattern."
  [^String path patterns]
  (boolean (some #(.find (.matcher ^Pattern % path)) patterns)))

(defn- snippet-allowed?
  "True if the matched snippet is explicitly allowlisted."
  [^String snippet patterns]
  (boolean (some #(.find (.matcher ^Pattern % snippet)) patterns)))

(defn- has-stopword?
  [^String snippet stopwords]
  (let [lc (.toLowerCase snippet)]
    (boolean (some #(.contains lc ^String %) stopwords))))

(defn- has-keyword?
  "Fast pre-filter: does content (lowercased) contain any of the rule's keywords?"
  [^String lc-content keywords]
  (or (empty? keywords)
      (boolean (some #(.contains lc-content ^String %) keywords))))

(defn- line-of-offset
  "Given content and a character offset, return 1-based line number."
  ^long [^String content ^long offset]
  (inc (count (re-seq #"\n" (subs content 0 (min offset (count content)))))))

(defn- extract-secret
  "Return the effective secret text for a match. Uses secretGroup if set, else group 0."
  [^Matcher m secret-group]
  (let [g (or secret-group 0)]
    (if (and secret-group (<= secret-group (.groupCount m)))
      (.group m secret-group)
      (.group m ^int (int g)))))

(defn- match->hit
  [rule ^String content ^Matcher m]
  (let [start   (.start m)
        match   (.group m)
        secret  (extract-secret m (:entropy-group rule))]
    (when (entropy/above? secret (:entropy-min rule))
      {:rule-id (:id rule)
       :rule    rule
       :start   start
       :line    (line-of-offset content start)
       :match   match
       :secret  secret})))

(defn scan-content
  "Run all rules against a single file's content. Returns a seq of hit maps.
   Short-circuits via keyword pre-filter per rule."
  [{:keys [rules global-allowlist]} ^String path ^String content]
  (when (and content (not (.isEmpty content)))
    (let [lc-content (.toLowerCase content)
          file-path  path]
      (if (or (path-allowed? file-path (:path-patterns global-allowlist))
              (path-allowed? file-path (map #(re-pattern (str ".*" (.pattern ^Pattern %) ".*"))
                                            (:path-patterns global-allowlist))))
        []
        (into []
              (comp
               (filter (fn [rule]
                         (and (not (path-allowed? file-path (:path-allowlist rule)))
                              (has-keyword? lc-content (:keywords rule)))))
               (mapcat
                (fn [rule]
                  (let [^Pattern p (:regex rule)
                        m          (.matcher p content)]
                    (loop [hits (transient [])]
                      (if (.find m)
                        (let [hit (match->hit rule content m)]
                          (if (and hit
                                   (not (snippet-allowed?
                                         (:match hit)
                                         (concat (:regex-allowlist rule)
                                                 (:regex-patterns global-allowlist))))
                                   (not (has-stopword?
                                         (:match hit)
                                         (concat (:stopwords rule)
                                                 (:stopwords global-allowlist)))))
                            (recur (conj! hits hit))
                            (recur hits)))
                        (persistent! hits)))))))
              rules)))))
