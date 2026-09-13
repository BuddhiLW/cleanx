(ns cleanx.scan.perms
  "Filesystem permission audit: compare actual modes against an expected-mode table
   for well-known sensitive paths."
  (:require [cleanx.scan.fs :as fs]
            [clojure.string :as str])
  (:import  [java.nio.file FileSystems PathMatcher]))

(def expected
  "Ordered list of {:glob :max-mode :severity :evidence} rules. First match wins."
  [;; SSH
   {:glob "**/.ssh/id_*"            :max-mode 0600 :severity :critical
    :kind :perm.ssh-key :skip-pub? true}
   {:glob "**/.ssh/*_rsa"           :max-mode 0600 :severity :critical :kind :perm.ssh-key}
   {:glob "**/.ssh/*_ed25519"       :max-mode 0600 :severity :critical :kind :perm.ssh-key}
   {:glob "**/.ssh/*_ecdsa"         :max-mode 0600 :severity :critical :kind :perm.ssh-key}
   {:glob "**/.ssh/config"          :max-mode 0600 :severity :high     :kind :perm.ssh-config}
   {:glob "**/.ssh/known_hosts*"    :max-mode 0644 :severity :low      :kind :perm.ssh-known-hosts}
   {:glob "**/.ssh/authorized_keys" :max-mode 0600 :severity :high     :kind :perm.ssh-authkeys}
   ;; GnuPG
   {:glob "**/.gnupg/*.kbx"         :max-mode 0600 :severity :high     :kind :perm.gnupg}
   {:glob "**/.gnupg/*.kbx~"        :max-mode 0600 :severity :high     :kind :perm.gnupg}
   {:glob "**/.gnupg/trustdb.gpg"   :max-mode 0600 :severity :high     :kind :perm.gnupg}
   {:glob "**/.gnupg/private-keys-v1.d/**" :max-mode 0600 :severity :critical :kind :perm.gnupg-priv}
   ;; Kubernetes
   {:glob "**/kubeconfig"           :max-mode 0600 :severity :critical :kind :perm.kubeconfig}
   {:glob "**/kubeconfig.*"         :max-mode 0600 :severity :critical :kind :perm.kubeconfig}
   {:glob "**/.kube/config"         :max-mode 0600 :severity :critical :kind :perm.kubeconfig}
   ;; Cloud credentials
   {:glob "**/.aws/credentials"     :max-mode 0600 :severity :critical :kind :perm.aws-creds}
   {:glob "**/.aws/config"          :max-mode 0600 :severity :medium   :kind :perm.aws-config}
   {:glob "**/.config/gcloud/**/credentials*" :max-mode 0600 :severity :critical :kind :perm.gcloud}
   {:glob "**/.docker/config.json"  :max-mode 0600 :severity :high     :kind :perm.docker}
   ;; Generic secrets
   {:glob "**/*.pem"                :max-mode 0600 :severity :critical :kind :perm.pem}
   {:glob "**/*.key"                :max-mode 0600 :severity :critical :kind :perm.private-key}
   {:glob "**/id_rsa"               :max-mode 0600 :severity :critical :kind :perm.private-key}
   {:glob "**/id_dsa"               :max-mode 0600 :severity :critical :kind :perm.private-key}
   ;; Dev creds
   {:glob "**/.netrc"               :max-mode 0600 :severity :high     :kind :perm.netrc}
   {:glob "**/.pgpass"              :max-mode 0600 :severity :high     :kind :perm.pgpass}
   {:glob "**/.my.cnf"              :max-mode 0600 :severity :high     :kind :perm.mycnf}
   {:glob "**/.git-credentials"     :max-mode 0600 :severity :critical :kind :perm.git-creds}
   {:glob "**/.env"                 :max-mode 0600 :severity :high     :kind :perm.env}
   {:glob "**/.envrc"               :max-mode 0600 :severity :medium   :kind :perm.envrc}])

(def ^:private ^java.nio.file.FileSystem fsys (FileSystems/getDefault))

(defn- ^PathMatcher glob->matcher [glob]
  (.getPathMatcher fsys (str "glob:" glob)))

(def ^:private matchers
  (mapv (fn [rule] (assoc rule :matcher (glob->matcher (:glob rule)))) expected))

(defn- match-rule [{:keys [^String path]}]
  (let [p (fs/->path path)]
    (some (fn [{:keys [^PathMatcher matcher] :as rule}]
            (when (.matches matcher p)
              rule))
          matchers)))

(defn- pub-key? [{:keys [path]}]
  (str/ends-with? path ".pub"))

(defn audit
  "Given a seq of stat maps, return a seq of violations.
   Each violation: {:path :mode :max-mode :severity :kind :evidence}."
  [stats]
  (->> stats
       (keep (fn [stat]
               (when-let [rule (match-rule stat)]
                 (when-not (and (:skip-pub? rule) (pub-key? stat))
                   (let [cur  (long (:mode stat))
                         lim  (long (:max-mode rule))]
                     (when (pos? (bit-and cur (bit-not lim)))
                       {:path     (:path stat)
                        :mode     cur
                        :max-mode lim
                        :severity (:severity rule)
                        :kind     (:kind rule)
                        :evidence (format "mode %04o exceeds expected %04o" cur lim)}))))))))
