(ns build
  (:require [clojure.tools.build.api :as b]))

(def module 'MovieModule)
(def version "0.1.0")
(def class-dir "target/classes")
(def basis (b/create-basis {}))
(def jar-file (format "target/%s-%s.jar" (name module) version))

(defn clean [_] (b/delete {:path "target"}) (println "Cleaned target"))

(defn uber
  [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["src/clj" "src/resources"] :target-dir class-dir :include "**/rama/module/**"})
  (b/uber {:class-dir class-dir :uber-file jar-file :basis basis :main 'clojure.main})
  (println "Created jar:" jar-file))
