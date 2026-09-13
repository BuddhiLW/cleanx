(ns cleanx.scan.fs
  "Filesystem walk + stat. Thin layer over java.nio.file."
  (:require [hive-dsl.result :as r])
  (:import  [java.nio.file Files FileVisitOption LinkOption Path Paths]
            [java.nio.file.attribute PosixFileAttributes PosixFilePermission]
            [java.util EnumSet]))

(def ^:private no-link-opts (into-array LinkOption []))
(def ^:private no-visit-opts (into-array FileVisitOption []))

(defn ^Path ->path [p]
  (cond
    (instance? Path p) p
    (instance? java.io.File p) (.toPath ^java.io.File p)
    :else (Paths/get (str p) (into-array String []))))

(def ^:private perm->bit
  {PosixFilePermission/OWNER_READ     0400
   PosixFilePermission/OWNER_WRITE    0200
   PosixFilePermission/OWNER_EXECUTE  0100
   PosixFilePermission/GROUP_READ     0040
   PosixFilePermission/GROUP_WRITE    0020
   PosixFilePermission/GROUP_EXECUTE  0010
   PosixFilePermission/OTHERS_READ    0004
   PosixFilePermission/OTHERS_WRITE   0002
   PosixFilePermission/OTHERS_EXECUTE 0001})

(defn perms->mode
  "Convert a set of PosixFilePermission into an integer octal mode."
  ^long [perms]
  (reduce + 0 (keep perm->bit perms)))

(defn stat
  "Return {:path :size :mode :dir? :symlink?} or nil if stat fails."
  [p]
  (try
    (let [path  (->path p)
          attrs (Files/readAttributes path PosixFileAttributes no-link-opts)]
      {:path     (str path)
       :size     (.size attrs)
       :mode     (perms->mode (.permissions attrs))
       :dir?     (.isDirectory attrs)
       :symlink? (.isSymbolicLink attrs)})
    (catch Throwable _ nil)))

(def ^:private default-ignore-dirs
  #{".git" ".hg" ".svn" "node_modules" ".venv" "venv" "__pycache__"
    ".mypy_cache" ".pytest_cache" ".tox" ".gradle" ".idea" "target"
    ".clj-kondo" ".cpcache" ".shadow-cljs" ".next" ".cache"
    "dist-newstyle" "build" "bower_components"})

(defn- ignored-segment? [^String seg]
  (contains? default-ignore-dirs seg))

(defn- binary?
  "Heuristic: peek first 512 bytes, flag NUL or >30% non-printable as binary."
  [^Path p]
  (try
    (with-open [is (Files/newInputStream p no-link-opts)]
      (let [buf (byte-array 512)
            n   (.read is buf)
            bs  (if (pos? n) (take n buf) [])
            nul (some #(= 0 (long %)) bs)
            non (count (filter (fn [^long b]
                                 (let [b (bit-and b 0xff)]
                                   (and (not (or (= b 9) (= b 10) (= b 13)))
                                        (or (< b 32) (> b 126)))))
                               bs))]
        (or nul (and (pos? n) (> (/ (double non) n) 0.30)))))
    (catch Throwable _ true)))

(defn walk
  "Walk a directory tree, returning a lazy seq of stat maps for files only.

   Options:
     :max-size    — skip files larger than this many bytes (default 2 MB)
     :include-dotfiles? — default true
     :skip-binary?      — default true"
  ([root] (walk root {}))
  ([root {:keys [max-size include-dotfiles? skip-binary?]
          :or   {max-size          (* 2 1024 1024)
                 include-dotfiles? true
                 skip-binary?      true}}]
   (let [root-path (->path root)]
     (when (Files/exists root-path no-link-opts)
       (let [stream (Files/walk root-path no-visit-opts)]
         (try
           (->> (iterator-seq (.iterator stream))
                (filter (fn [^Path p]
                          (let [segs (map str (iterator-seq (.iterator p)))]
                            (not-any? ignored-segment? segs))))
                (filter (fn [^Path p] (Files/isRegularFile p no-link-opts)))
                (map stat)
                (filter some?)
                (filter (fn [{:keys [^long size]}] (<= size max-size)))
                (filter (fn [{:keys [^String path]}]
                          (or include-dotfiles?
                              (not (re-find #"/\.[^/]+$" path)))))
                (filter (fn [{:keys [path]}]
                          (or (not skip-binary?)
                              (not (binary? (->path path))))))
                doall)
           (finally (.close stream))))))))

(defn read-text
  "Read file as UTF-8 string. Returns Result."
  [p]
  (try
    (r/ok (String. (Files/readAllBytes (->path p)) "UTF-8"))
    (catch Throwable t
      (r/err :fs/read-failed {:path (str p) :message (ex-message t)}))))
