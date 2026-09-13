(ns cleanx.scan.entropy
  "Shannon entropy over a byte/char sequence. Used as a false-positive gate.")

(defn shannon
  "Shannon entropy of string s, base 2. Returns a double.
   Empty string → 0.0."
  ^double [^String s]
  (if (or (nil? s) (zero? (.length s)))
    0.0
    (let [freqs (frequencies s)
          n     (double (.length s))
          log2  (Math/log 2.0)]
      (->> freqs
           vals
           (map (fn [^long c]
                  (let [p (/ (double c) n)]
                    (* p (/ (Math/log p) log2)))))
           (reduce +)
           -))))

(defn above?
  "True iff entropy of s is at least threshold. nil threshold → always true."
  [s threshold]
  (or (nil? threshold)
      (>= (shannon s) (double threshold))))
