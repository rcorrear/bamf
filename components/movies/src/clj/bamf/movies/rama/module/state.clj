(ns bamf.movies.rama.module.state (:use [com.rpl.rama] [com.rpl.rama.path]))

(def $$movies nil)
(def $$metadata-by-movie-id nil)
(def $$movies-id-by-metadata-id nil)
(def $$movies-id-by-tmdb-id nil)
(def $$movies-ids-by-monitor nil)
(def $$movies-ids-by-monitored nil)
(def $$movies-ids-by-tag nil)
(def $$movies-ids-by-target-system nil)

(def movie-row-schema
  (fixed-keys-schema {:added                java.time.Instant
                      :folder-name          String
                      :imdb-id              String
                      :last-search-time     java.time.Instant
                      :minimum-availability String
                      :monitored            Boolean
                      :movie-file-id        Long
                      :movie-metadata-id    Long
                      :path                 String
                      :quality-profile-id   Long
                      :root-folder-path     String
                      :tags                 (set-schema String)
                      :title                String
                      :title-slug           String
                      :tmdb-id              Long
                      :year                 Long}))

(def movies-pstate-schema (map-schema Long movie-row-schema))
(def metadata-row-schema (map-schema clojure.lang.Keyword Object))
(def metadata-by-movie-id-schema (map-schema Long metadata-row-schema))
(def movies-id-by-metadata-id-schema (map-schema Long Long))
(def movies-id-by-tmdb-id-schema (map-schema Long Long))
(def movies-ids-by-monitor-schema (map-schema String (set-schema Long)))
(def movies-ids-by-monitored-schema {Boolean (set-schema Long)})
(def movies-ids-by-tag-schema (map-schema String (set-schema Long)))
(def movies-ids-by-target-system-schema (map-schema String (set-schema Long)))

(defn declare-movies-pstate!
  [topology]
  (declare-pstate topology $$movies movies-pstate-schema)
  (declare-pstate topology $$metadata-by-movie-id metadata-by-movie-id-schema))

(defn declare-index-pstates!
  [topology]
  (declare-pstate topology $$movies-id-by-metadata-id movies-id-by-metadata-id-schema)
  (declare-pstate topology $$movies-id-by-tmdb-id movies-id-by-tmdb-id-schema)
  (declare-pstate topology $$movies-ids-by-monitor movies-ids-by-monitor-schema)
  (declare-pstate topology $$movies-ids-by-monitored movies-ids-by-monitored-schema)
  (declare-pstate topology $$movies-ids-by-tag movies-ids-by-tag-schema)
  (declare-pstate topology $$movies-ids-by-target-system movies-ids-by-target-system-schema))
