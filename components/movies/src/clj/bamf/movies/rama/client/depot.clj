(ns bamf.movies.rama.client.depot
  (:require [bamf.movies.rama.common :as common]
            [com.rpl.rama :refer [foreign-append!]]))

(defn- ensure-depot [depot] (when-not depot (throw (IllegalStateException. "Missing Rama movie depot handle"))) depot)

(defn put!
  "Wrap the provided movie payload in a movie-created-event envelope and append it to the depot.
   Returns the acknowledgement emitted by the Rama module (or a default stored response)."
  [{:keys [depot movie]}]
  (let [event (common/movie-created-event movie)
        ack   (foreign-append! (ensure-depot depot) event :ack)]
    (if (map? ack) ack {:status :stored :movie {}})))

(defn update!
  "Wrap the provided movie payload in a movie-updated-event envelope and append it to the depot.
   Returns the acknowledgement emitted by the Rama module (or a default updated response)."
  [{:keys [depot movie]}]
  (let [event (common/movie-updated-event movie)
        ack   (foreign-append! (ensure-depot depot) event :ack)]
    (if (map? ack) ack {:status :updated :movie {}})))
