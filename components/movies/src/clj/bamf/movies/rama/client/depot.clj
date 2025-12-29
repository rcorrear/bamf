(ns bamf.movies.rama.client.depot
  (:require [bamf.movies.rama.common :as common]
            [com.rpl.rama :refer [foreign-append!]]))

(defn- ensure-depot [depot] (when-not depot (throw (IllegalStateException. "Missing Rama movie depot handle"))) depot)

(defn- unwrap-ack
  "Rama can return acknowledgements directly or wrapped under the pipeline name.
  Extract the inner payload but fall back to the original map for callers that
  already stub the final structure."
  [ack]
  (when (map? ack) (or (get ack common/movies-etl-name) (get ack (keyword common/movies-etl-name)) ack)))

(defn put!
  "Wrap the provided movie payload in a movie-created-event envelope and append it to the depot.
   Returns the acknowledgement emitted by the Rama module (or a default stored response)."
  [{:keys [depot movie]}]
  (let [event (common/movie-created-event movie)
        ack   (foreign-append! (ensure-depot depot) event :ack)
        data  (unwrap-ack ack)]
    (or data {:status :stored :movie {}})))

(defn update!
  "Wrap the provided movie payload in a movie-updated-event envelope and append it to the depot.
   Returns the acknowledgement emitted by the Rama module (or a default updated response)."
  [{:keys [depot movie]}]
  (let [event (common/movie-updated-event movie)
        ack   (foreign-append! (ensure-depot depot) event :ack)
        data  (unwrap-ack ack)]
    (or data {:status :updated :movie {}})))
