(ns bamf.test-runner
  "Entry point for `clojure -X:test`. Discovers and runs project tests."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :as t]))

(def ^:private default-test-dirs
  ["bases/rest-api/test/clj" "components/api/test/clj" "components/config/test/clj" "components/movies/test/clj"])

(def ^:private file-separator-pattern (re-pattern (java.util.regex.Pattern/quote (str java.io.File/separatorChar))))

(defn- path->namespace
  [^java.io.File root ^java.io.File file]
  (let [root-path (.getCanonicalPath root)
        file-path (.getCanonicalPath file)
        relative  (subs file-path (inc (count root-path)))
        no-ext    (cond (str/ends-with? relative ".cljc") (subs relative 0 (- (count relative) 5))
                        (str/ends-with? relative ".clj")  (subs relative 0 (- (count relative) 4))
                        :else                             nil)]
    (some-> no-ext
            (str/replace file-separator-pattern ".")
            (str/replace "_" "-")
            symbol)))

(defn- discover-test-namespaces
  [dirs]
  (->> dirs
       (map io/file)
       (filter #(.exists ^java.io.File %))
       (mapcat (fn [dir]
                 (->> (file-seq dir)
                      (filter #(.isFile ^java.io.File %))
                      (filter #(re-find #"\.clj[c]?$" (.getName ^java.io.File %)))
                      (keep #(path->namespace dir %)))))
       distinct
       sort))

(defn run
  "Run the project's test suites.

  Accepts an optional map with keys:
  - `:dirs` collection of directory strings to scan for tests.
  - `:nses` explicit collection of namespace symbols or strings to test.

  Returns the aggregated clojure.test summary map.
  Throws when any test fails to ensure a non-zero exit status."
  ([] (run {}))
  ([{:keys [dirs nses] :or {dirs default-test-dirs}}]
   (let [discovered  (when-not (seq nses) (discover-test-namespaces dirs))
         target-nses (->> (or nses discovered)
                          (map #(if (symbol? %) % (symbol (str %))))
                          (remove nil?)
                          distinct
                          sort)]
     (if (empty? target-nses)
       (do (println "No test namespaces found. Nothing to run.") (flush) {:test 0 :pass 0 :fail 0 :error 0})
       (do (doseq [ns target-nses] (require ns))
           (let [result (apply t/run-tests target-nses)]
             (when-not (t/successful? result) (throw (ex-info "Test failures" {:summary result})))
             result))))))
