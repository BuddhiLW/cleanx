(ns cleanx.rules.model
  "Data model for secret-scanning rules and findings.")

(defrecord Rule
  [id
   description
   regex            ; compiled java.util.regex.Pattern
   regex-source     ; original string (for debugging / reports)
   keywords         ; pre-filter strings; only run regex if one is present
   entropy-min      ; nil or double
   entropy-group    ; capture group index for entropy check (nil = whole match)
   path-allowlist   ; seq of compiled Patterns; match → skip file
   regex-allowlist  ; seq of compiled Patterns; match on snippet → skip finding
   stopwords        ; seq of lowercase strings; if match contains any → skip
   tags             ; [string]
   severity])       ; :critical :high :medium :low

(defrecord Finding
  [id severity kind rule-id path line snippet evidence remedy tool ts])

(defn severity->order
  "Total order for sorting severities. Higher = more urgent."
  [s]
  (case s
    :critical 4
    :high     3
    :medium   2
    :low      1
    :info     0
    0))
