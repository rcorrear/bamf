;; Root build script for Rama module components.
;; Usage examples:
;;   clojure -T:build uber :component \"movies\"
;;   clojure -T:build uber-all

(ns build
  (:require [clojure.tools.build.api :as b]))

(def components
  {"movies" {:lib       'bamf/movies-rama-module
             :deps-file "components/movies/deps.edn"
             :aliases   [:build]
             :src-dirs  ["components/movies/src/clj" "components/movies/src/resources"]
             :class-dir "target/classes/movies"
             :uber-file "target/movies-rama-module-uber.jar"}})

(defn- cfg
  [component]
  (or (get components component)
      (throw (ex-info (format "Unknown component '%s'. Known: %s"
                              component
                              (-> components
                                  keys
                                  sort
                                  vec))
                      {:component component}))))

(defn clean
  "Delete the target directory for the selected component."
  [{:keys [component] :as params}]
  (let [{:keys [class-dir]} (cfg component)]
    ;; (b/delete {:path (or (some-> class-dir (re-find #\"^(.+)/[^/]+$\") second)
    ;;                      class-dir)}))
  )
  params)

(defn uber
  "Build an uberjar for the given component. Optional :env param is reserved for future src selection."
  [{:keys [component] :as params}]
  (let [{:keys [deps-file aliases src-dirs class-dir uber-file]} (cfg component)
        basis                                                    (b/create-basis {:project deps-file :aliases aliases})]
    (clean params)
    (b/copy-dir {:src-dirs src-dirs :target-dir class-dir :include "**/rama/module/**"})
    (b/uber {:class-dir class-dir :uber-file uber-file :basis basis})
    (assoc params :uber-file uber-file)))

(defn uber-all
  "Build uberjars for all configured components."
  [params]
  (doseq [component (sort (keys components))] (uber (assoc params :component component)))
  params)
