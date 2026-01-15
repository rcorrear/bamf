(ns bamf.movies.rama.module.create
  (:use [com.rpl.rama] [com.rpl.rama.path])
  (:require [bamf.movies.rama.module.helpers :as helpers]
            [bamf.movies.rama.module.state :refer
             [$$metadata-by-movie-id $$movies $$movies-id-by-metadata-id $$movies-id-by-tmdb-id $$movies-ids-by-monitor
              $$movies-ids-by-monitored $$movies-ids-by-tag $$movies-ids-by-target-system]]
            [com.rpl.rama.ops :as ops])
  (:import [com.rpl.rama.helpers ModuleUniqueIdPState]))

(deframaop movie-create
  [{:keys [*certification *clean-original-title *clean-title *collection *digital-release *genres *images *imdb-id
           *in-cinemas *last-info-sync *minimum-availability *monitor *monitored *movie-file-id *movie-metadata-id
           *original-language *original-title *overview *path *physical-release *popularity *quality-profile-id *ratings
           *recommendations *root-folder-path *runtime *secondary-year *sort-title *status *studio *tags *target-system
           *title *title-slug *tmdb-id *website *year *you-tube-trailer-id]}]
  (<<with-substitutions [$$movies                      (this-module-pobject-task-global "$$movies")
                         $$metadata-by-movie-id        (this-module-pobject-task-global "$$metadata-by-movie-id")
                         $$movies-id-by-tmdb-id        (this-module-pobject-task-global "$$movies-id-by-tmdb-id")
                         $$movies-ids-by-monitor       (this-module-pobject-task-global "$$movies-ids-by-monitor")
                         $$movies-ids-by-monitored     (this-module-pobject-task-global "$$movies-ids-by-monitored")
                         $$movies-ids-by-tag           (this-module-pobject-task-global "$$movies-ids-by-tag")
                         $$movies-ids-by-target-system (this-module-pobject-task-global "$$movies-ids-by-target-system")
                         $$movies-id-by-metadata-id    (this-module-pobject-task-global "$$movies-id-by-metadata-id")
                         $$id                          (this-module-pobject-task-global "$$id")]
    (|hash$$ $$movies *tmdb-id)
    (local-select> (keypath *tmdb-id) $$movies-id-by-tmdb-id :> *existing-movie-id)
    (<<if (nil? *existing-movie-id)
      (helpers/print-event :debug :movie/save :generating-new-id)
      (java-macro! (.genId (ModuleUniqueIdPState. "$$id") "*new-movie-id"))
      (|hash$$ $$movies *new-movie-id)
      (helpers/print-event :info :movie/save {:id *new-movie-id})
      (helpers/print-event :debug :movie/save :creating-movie)
      (<<shadowif *tags vector? (set *tags))
      (identity {:added                (java.time.Instant/now)
                 :imdb-id              *imdb-id
                 :last-search-time     nil
                 :minimum-availability *minimum-availability
                 :monitored            *monitored
                 :movie-file-id        *movie-file-id
                 :movie-metadata-id    *movie-metadata-id
                 :path                 *path
                 :quality-profile-id   *quality-profile-id
                 :root-folder-path     *root-folder-path
                 :tags                 *tags
                 :title                *title
                 :title-slug           *title-slug
                 :tmdb-id              *tmdb-id
                 :year                 *year}
                :>
                *movie-row)
      (helpers/print-event :debug :movie/save :saving-movie {:id *new-movie-id} *movie-row)
      (local-transform> [(keypath *new-movie-id) (termval *movie-row)] $$movies)
      (identity (->> {:certification        *certification
                      :clean-original-title *clean-original-title
                      :clean-title          *clean-title
                      :collection           *collection
                      :digital-release      *digital-release
                      :genres               *genres
                      :images               *images
                      :in-cinemas           *in-cinemas
                      :last-info-sync       *last-info-sync
                      :original-language    *original-language
                      :original-title       *original-title
                      :overview             *overview
                      :physical-release     *physical-release
                      :popularity           *popularity
                      :ratings              *ratings
                      :recommendations      *recommendations
                      :runtime              *runtime
                      :secondary-year       *secondary-year
                      :sort-title           *sort-title
                      :status               *status
                      :studio               *studio
                      :website              *website
                      :year                 *year
                      :you-tube-trailer-id  *you-tube-trailer-id}
                     (remove (comp nil? val))
                     (into {}))
                :>
                *metadata)
      (<<if (seq *metadata)
        (helpers/print-event :debug :movie/save :hashing-by-movie-id)
        (|hash$$ $$metadata-by-movie-id *new-movie-id)
        (helpers/print-event :debug :movie/save :saving-metadata {:movie-id *new-movie-id} *metadata)
        (local-transform> [(keypath *new-movie-id) (termval *metadata)] $$metadata-by-movie-id))
      (|hash$$ $$movies-id-by-metadata-id *movie-metadata-id)
      (helpers/print-event :debug
                           :movie/save
                           :saving-movie-metadata-id
                           {:movie-metadata-id *movie-metadata-id}
                           *new-movie-id)
      (local-transform> [(keypath *movie-metadata-id) (termval *new-movie-id)] $$movies-id-by-metadata-id)
      (helpers/print-event :debug :movie/save :hashing-by-tmdb-id {:tmdb-id *tmdb-id} *new-movie-id)
      (|hash$$ $$movies-id-by-tmdb-id *tmdb-id)
      (helpers/print-event :debug :movie/save :saving-tmdb-id {:tmdb-id *tmdb-id} *new-movie-id)
      (local-transform> [(keypath *tmdb-id) (termval *new-movie-id)] $$movies-id-by-tmdb-id)
      (helpers/print-event :debug :movie/save :hashing-by-monitor {:monitor *monitor} *new-movie-id)
      (|hash$$ $$movies-ids-by-monitor *monitor)
      (helpers/print-event :debug :movie/save :saving-monitor {:monitor *monitor} *new-movie-id)
      (local-transform> [(keypath *monitor) NONE-ELEM (termval *new-movie-id)] $$movies-ids-by-monitor)
      (<<if *monitored
        (helpers/print-event :debug :movie/save :hashing-by-monitored {:monitored *monitored} *new-movie-id)
        (|hash$$ $$movies-ids-by-monitored *monitored)
        (helpers/print-event :debug :movie/save :saving-monitored {:monitored *monitored} *new-movie-id)
        (local-transform> [(keypath true) NONE-ELEM (termval *new-movie-id)] $$movies-ids-by-monitored))
      (<<if (not (empty? *tags))
        (helpers/print-event :debug :movie/save :exploding-tags {:tags *tags} *new-movie-id)
        (ops/explode *tags :> *tag)
        (helpers/print-event :debug :movie/save :hashing-by-tag {:tag *tag} *new-movie-id)
        (|hash$$ $$movies-ids-by-tag *tag)
        (helpers/print-event :debug :movie/save :saving-tag {:tag *tag} *new-movie-id)
        (local-transform> [(keypath *tag) NONE-ELEM (termval *new-movie-id)] $$movies-ids-by-tag))
      (helpers/print-event :debug :movie/save :hashing-by-target-system {:target-system *target-system} *new-movie-id)
      (|hash$$ $$movies-ids-by-target-system *target-system)
      (helpers/print-event :debug :movie/save :saving-target-system {:target-system *target-system} *new-movie-id)
      (local-transform> [(keypath *target-system) NONE-ELEM (termval *new-movie-id)] $$movies-ids-by-target-system)
      (helpers/print-event :debug :movie/save {:returning-new-id {:id *new-movie-id}})
      (ack-return> {:status :stored :movie {:id *new-movie-id}})
      (else>)
      (helpers/print-event :debug :movie/save {:returning-existing-id {:id *existing-movie-id}})
      (ack-return> {:status :duplicate}))))
