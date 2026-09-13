(ns cleanx.rules.loader
  "Parse a gitleaks-format TOML rule file into cleanx.rules.model/Rule records."
  (:require [cleanx.rules.model :as m]
            [clojure.java.io :as io]
            [hive-dsl.result :as r])
  (:import  [java.util.regex Pattern PatternSyntaxException]
            [org.tomlj Toml TomlArray TomlParseResult TomlTable]))

(defn- compile-pattern
  "Compile a regex string into a java Pattern. Returns nil on failure (never throws)."
  [^String src]
  (try
    (Pattern/compile src)
    (catch PatternSyntaxException _ nil)))

(defn- compile-patterns [ss]
  (->> ss (keep compile-pattern) vec))

(defn- array->strings [^TomlArray arr]
  (when arr
    (mapv #(.getString arr ^long %) (range (.size arr)))))

(defn- array->tables [^TomlArray arr]
  (when arr
    (mapv #(.getTable arr ^long %) (range (.size arr)))))

(defn- merge-allowlists
  "Collapse all per-rule [[rules.allowlists]] sub-tables into a single view."
  [^TomlTable rule-table]
  (let [arr    (.getArray rule-table "allowlists")
        tables (array->tables arr)
        collect (fn [key-name]
                  (into [] (mapcat
                            (fn [^TomlTable t]
                              (array->strings (.getArray t key-name))))
                        tables))]
    {:paths     (collect "paths")
     :regexes   (collect "regexes")
     :stopwords (collect "stopwords")}))

(defn- coerce-double [v]
  (cond
    (nil? v)    nil
    (number? v) (double v)
    :else       (try (Double/parseDouble (str v)) (catch Throwable _ nil))))

(defn- coerce-int [v]
  (cond
    (nil? v)    nil
    (number? v) (int v)
    :else       (try (Integer/parseInt (str v)) (catch Throwable _ nil))))

(defn- parse-rule [^TomlTable rt]
  (let [id            (.getString rt "id")
        description   (.getString rt "description")
        regex-source  (.getString rt "regex")
        pattern       (some-> regex-source compile-pattern)
        keywords      (or (array->strings (.getArray rt "keywords")) [])
        entropy       (coerce-double (.get rt "entropy"))
        secret-group  (coerce-int (.get rt "secretGroup"))
        tags          (or (array->strings (.getArray rt "tags")) [])
        allow         (merge-allowlists rt)]
    (when (and id pattern)
      (m/->Rule
       id
       description
       pattern
       regex-source
       (mapv #(.toLowerCase ^String %) keywords)
       entropy
       secret-group
       (compile-patterns (:paths allow))
       (compile-patterns (:regexes allow))
       (mapv #(.toLowerCase ^String %) (:stopwords allow))
       tags
       :high))))

(defn- parse-global-allowlist [^TomlParseResult toml]
  (let [t (.getTable toml "allowlist")]
    (if t
      {:paths     (or (array->strings (.getArray t "paths")) [])
       :regexes   (or (array->strings (.getArray t "regexes")) [])
       :stopwords (or (array->strings (.getArray t "stopwords")) [])}
      {:paths [] :regexes [] :stopwords []})))

(defn load-rules
  "Parse a gitleaks TOML file into {:rules [Rule] :global-allowlist {...}}.
   Returns a Result."
  [source]
  (try
    (let [toml       (cond
                       (instance? java.io.File source) (Toml/parse (.toPath ^java.io.File source))
                       (instance? java.nio.file.Path source) (Toml/parse ^java.nio.file.Path source)
                       :else (Toml/parse (str source)))
          errors     (.errors toml)
          rules-arr  (.getArray toml "rules")
          rule-ts    (array->tables rules-arr)
          rules      (into [] (keep parse-rule) rule-ts)
          global     (parse-global-allowlist toml)]
      (if (seq errors)
        (r/err :rules/toml-parse {:errors (mapv str errors)})
        (r/ok {:rules            rules
               :global-allowlist {:path-patterns    (compile-patterns (:paths global))
                                  :regex-patterns   (compile-patterns (:regexes global))
                                  :stopwords        (mapv #(.toLowerCase ^String %)
                                                          (:stopwords global))}})))
    (catch Throwable t
      (r/err :rules/load-failed {:message (ex-message t)}))))

(defn load-default-rules
  "Load the vendored gitleaks.toml from resources/rules/."
  []
  (if-let [res (io/resource "rules/gitleaks.toml")]
    (try
      (let [bytes (.getBytes (slurp res) "UTF-8")
            tmp   (doto (java.io.File/createTempFile "cleanx-gitleaks" ".toml")
                    (.deleteOnExit))]
        (with-open [out (io/output-stream tmp)] (.write out bytes))
        (load-rules tmp))
      (catch Throwable t
        (r/err :rules/resource-load-failed {:message (ex-message t)})))
    (r/err :rules/resource-missing {:resource "rules/gitleaks.toml"})))
