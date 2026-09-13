(ns cleanx.memory.claude-md
  "Parse Claude Code auto-memory files.

   File shape:
     ---
     name: <string>
     description: <string>
     type: user|feedback|project|reference
     ---
     <markdown body>

   We deliberately don't pull in a YAML lib: the frontmatter is a tiny
   key-colon-value block and regex handles it cleanly."
  (:require [clojure.java.io :as io]
            [clojure.string  :as str]
            [hive-dsl.result :as r])
  (:import  [java.io File]))

(defrecord Memory
  [source-path          ; absolute path to the .md file
   project-id           ; directory name under ~/.claude/projects/ (slug form)
   name
   description
   type                 ; :user :feedback :project :reference :unknown
   body                 ; markdown string
   raw-front])          ; {string → string} as parsed

(def ^:private front-re #"^---\s*\r?\n([\s\S]*?)\r?\n---\s*\r?\n([\s\S]*)$")

(defn- parse-front [^String block]
  (into {}
        (comp (map str/trim)
              (remove empty?)
              (remove #(str/starts-with? % "#"))
              (keep (fn [^String line]
                      (let [idx (.indexOf line ":")]
                        (when (pos? idx)
                          [(str/trim (subs line 0 idx))
                           (str/trim (subs line (inc idx)))])))))
        (str/split-lines block)))

(defn parse-text
  "Parse a memory-file string. Returns Result<Memory-without-path>.

   Files without frontmatter are tolerated and represented with an empty map."
  [^String content]
  (try
    (if-let [[_ front body] (re-matches front-re content)]
      (let [f    (parse-front front)
            type (keyword (get f "type" "unknown"))]
        (r/ok {:name        (get f "name")
               :description (get f "description")
               :type        (if (#{:user :feedback :project :reference} type)
                              type :unknown)
               :body        (str/trim body)
               :raw-front   f}))
      (r/ok {:name        nil
             :description nil
             :type        :unknown
             :body        (str/trim content)
             :raw-front   {}}))
    (catch Throwable t
      (r/err :memory/parse-failed {:message (ex-message t)}))))

(defn- project-id-from-path
  "Derive a project id from a path like ~/.claude/projects/<slug>/memory/foo.md"
  [^String path]
  (let [marker "/.claude/projects/"
        i      (.indexOf path marker)]
    (when (pos? i)
      (let [rest (subs path (+ i (.length marker)))
            slash (.indexOf rest "/")]
        (when (pos? slash) (subs rest 0 slash))))))

(defn parse-file
  "Parse a single memory file into a Memory record. Returns Result<Memory>."
  [^String path]
  (try
    (let [content (slurp path)
          r       (parse-text content)]
      (if (r/err? r)
        r
        (r/ok (map->Memory
               (assoc (:ok r)
                      :source-path (str path)
                      :project-id  (project-id-from-path (str path)))))))
    (catch Throwable t
      (r/err :memory/read-failed {:path path :message (ex-message t)}))))

(defn discover
  "Find every memory file under ~/.claude/projects/*/memory/ and the
   top-level /home/<user>/.claude/projects/-home-<user>/memory/ index.
   Returns a seq of absolute path strings."
  []
  (let [home (System/getProperty "user.home")
        root (io/file home ".claude" "projects")]
    (when (.exists root)
      (for [^File proj  (.listFiles root)
            :when       (.isDirectory proj)
            :let        [mem (io/file proj "memory")]
            :when       (.exists mem)
            ^File f     (file-seq mem)
            :when       (and (.isFile f)
                             (str/ends-with? (.getName f) ".md"))]
        (.getAbsolutePath f)))))

(defn parse-all
  "Parse every memory file under ~/.claude/projects. Returns a seq of Memory
   records, dropping files that fail to parse (errors logged as nil)."
  []
  (keep (fn [p]
          (let [r (parse-file p)]
            (when (r/ok? r) (:ok r))))
        (discover)))
