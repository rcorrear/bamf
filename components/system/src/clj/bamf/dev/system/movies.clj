(ns bamf.dev.system.movies
  "Movie-module lifecycle helpers for Donut-managed systems."
  (:require [bamf.movies.rama.common :as common]
            [bamf.movies.rama.module.core :as module]
            [bamf.rama.interface :as rama]
            [com.rpl.rama.test :as rt]
            [taoensso.telemere :as t]))

(def ^:private default-launch-config {:tasks 4 :threads 2})

(defn- connect-runtime
  [rama-runtime owns-module?]
  {:rama         rama-runtime
   :movie-depot  (rama/foreign-depot rama-runtime common/module-name common/movie-depot-name)
   :owns-module? owns-module?})

(defn start!
  [{:keys [launch-config rama] :or {launch-config default-launch-config}}]
  (let [handle (rama/handle rama)]
    (if (rama/module-deployed? rama common/module-name)
      (rt/update-module! handle module/MovieModule)
      (rt/launch-module! handle module/MovieModule launch-config))
    (connect-runtime rama true)))

(defn stop!
  [{:keys [rama owns-module?]}]
  (when (and owns-module? rama)
    (t/log! {:level :info :event :movies/rama-module-stop :reason :movies/rama-module-stop}
            "Stopping movie module for Rama runtime")
    (try (rt/destroy-module! (rama/handle rama) common/module-name) (catch Exception _ nil)))
  nil)
