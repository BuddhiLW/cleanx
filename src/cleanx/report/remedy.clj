(ns cleanx.report.remedy
  "Map a finding to a concrete remedy block (shell commands + guidance)."
  (:require [clojure.string :as str]))

(defn- perm-remedy [{:keys [path max-mode kind]}]
  (let [chmod (format "chmod %04o %s" (or max-mode 0600) (pr-str path))]
    (case kind
      :perm.ssh-key
      (str chmod "\n"
           "# If this key has no passphrase, add one:\n"
           "#   ssh-keygen -p -f " (pr-str path))

      :perm.kubeconfig
      (str chmod "\n"
           "# Also check for backup copies:\n"
           "#   find $(dirname " (pr-str path) ") -name 'kubeconfig*' -exec chmod 600 {} \\;")

      :perm.aws-creds
      (str chmod "\n"
           "# Consider moving to aws-vault or SSO instead of long-lived keys.")

      :perm.git-creds
      (str chmod "\n"
           "# Plaintext git credentials are risky. Use a credential helper:\n"
           "#   git config --global credential.helper store   # or libsecret")

      (str chmod))))

(defn- secret-remedy [{:keys [rule-id path]}]
  (str "# Rotate this secret at the provider now.\n"
       "# Then remove it from " (pr-str path) " and replace with:\n"
       "#   - an env var read at runtime, or\n"
       "#   - pass(1) / sops+age / 1Password injection, or\n"
       "#   - a per-service systemd credential.\n"
       "# Rule: " rule-id))

(defn for-finding
  "Return a remedy string for a finding map."
  [{:keys [kind] :as f}]
  (case kind
    :perm   (perm-remedy f)
    :secret (secret-remedy f)
    ""))
