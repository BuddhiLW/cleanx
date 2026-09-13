(ns cleanx.report.json
  "Render scan results as JSON (or EDN)."
  (:require [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.pprint :as pp]))

(defn- coerce [v]
  (cond
    (keyword? v) (name v)
    (uuid? v)    (str v)
    (inst? v)    (str v)
    (map? v)     (into {} (map (fn [[k x]] [(if (keyword? k) (name k) (str k))
                                             (coerce x)]) v))
    (coll? v)    (mapv coerce v)
    :else        v))

(defn render-json
  [scan-result]
  (json/generate-string (coerce scan-result) {:pretty true}))

(defn render-edn
  [scan-result]
  (with-out-str (pp/pprint scan-result)))
