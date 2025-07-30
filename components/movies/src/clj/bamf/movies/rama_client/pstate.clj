(ns bamf.movies.rama-client.pstate)

(defn movie-by-metadata-id
  [{:keys [rama]} metadata-id]
  (when-let [lookup (:lookup-by-metadata-id rama)] (lookup metadata-id)))

(defn movie-by-path [{:keys [rama]} path] (when-let [lookup (:lookup-by-path rama)] (lookup path)))

(defn movie-by-id [{:keys [rama]} id] (when-let [lookup (:lookup-by-id rama)] (lookup id)))

(defn movies-by-tag [{:keys [rama]} tag] (when-let [lookup (:lookup-by-tag rama)] (lookup tag)))

(defn movies-by-target-system
  [{:keys [rama]} system]
  (when-let [lookup (:lookup-by-target-system rama)] (lookup system)))

(defn next-id
  [{:keys [rama]}]
  (if-let [next-id-fn (:next-id rama)]
    (long (next-id-fn))
    (throw (ex-info "Missing Rama next-id function" {:env-keys (keys rama)}))))
