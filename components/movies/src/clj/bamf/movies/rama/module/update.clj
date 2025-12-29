(ns bamf.movies.rama.module.update
  (:use [com.rpl.rama] [com.rpl.rama.path])
  (:require [bamf.movies.rama.module.helpers :as helpers]
            [bamf.movies.rama.module.state :refer [$$movies $$movies-ids-by-monitored $$movies-ids-by-tag]]
            [clojure.set :as set]
            [com.rpl.rama.ops :as ops]))

(deframafn ^:private resolve-movie-update
  [*existing-movie-row *incoming-last-search-time *incoming-minimum-availability *incoming-monitored *incoming-path
   *incoming-quality-profile-id *incoming-root-folder-path]
  (identity (get *existing-movie-row :added) :> *added)
  (identity (get *existing-movie-row :imdb-id) :> *imdb-id)
  (identity (get *existing-movie-row :last-search-time) :> *existing-last-search-time)
  (identity (get *existing-movie-row :minimum-availability) :> *existing-minimum-availability)
  (identity (get *existing-movie-row :monitored) :> *existing-monitored)
  (identity (get *existing-movie-row :movie-file-id) :> *movie-file-id)
  (identity (get *existing-movie-row :movie-metadata-id) :> *existing-movie-metadata-id)
  (identity (get *existing-movie-row :tmdb-id) :> *existing-tmdb-id)
  (identity (get *existing-movie-row :path) :> *maybe-path)
  (<<if (nil? *maybe-path) (identity "" :> *resolved-path) (else>) (identity *maybe-path :> *resolved-path))
  (identity (get *existing-movie-row :quality-profile-id) :> *existing-quality-profile-id)
  (identity (get *existing-movie-row :root-folder-path) :> *maybe-root)
  (<<if (nil? *maybe-root)
    (identity "" :> *resolved-root-folder-path)
    (else>)
    (identity *maybe-root :> *resolved-root-folder-path))
  (identity (get *existing-movie-row :title) :> *title)
  (identity (get *existing-movie-row :title-slug) :> *title-slug)
  (identity (get *existing-movie-row :year) :> *year)
  (<<if *incoming-last-search-time
    (identity (helpers/->instant *incoming-last-search-time) :> *parsed-last-search)
    (else>)
    (identity nil :> *parsed-last-search))
  (<<if (nil? *parsed-last-search)
    (identity *existing-last-search-time :> *resolved-last-search)
    (else>)
    (identity *parsed-last-search :> *resolved-last-search))
  (helpers/print-event :debug
                       :movie/update
                       :resolve-last-search
                       {:incoming *incoming-last-search-time}
                       {:parsed *parsed-last-search :existing *existing-last-search-time})
  (<<if *incoming-minimum-availability
    (identity *incoming-minimum-availability :> *resolved-minimum-availability)
    (else>)
    (identity *existing-minimum-availability :> *resolved-minimum-availability))
  (helpers/print-event :debug
                       :movie/update
                       :resolve-minimum-availability
                       {:incoming *incoming-minimum-availability}
                       {:resolved *resolved-minimum-availability})
  (<<if *incoming-quality-profile-id
    (identity *incoming-quality-profile-id :> *resolved-quality-profile-id)
    (else>)
    (identity *existing-quality-profile-id :> *resolved-quality-profile-id))
  (helpers/print-event :debug
                       :movie/update
                       :resolve-quality-profile
                       {:incoming *incoming-quality-profile-id}
                       {:resolved *resolved-quality-profile-id})
  (<<if *incoming-path (identity *incoming-path :> *resolved-path) (else>) (identity *resolved-path :> *resolved-path))
  (helpers/print-event :debug :movie/update :resolve-path {:incoming *incoming-path} {:resolved *resolved-path})
  (<<if *incoming-root-folder-path
    (identity *incoming-root-folder-path :> *resolved-root-folder-path)
    (else>)
    (identity *resolved-root-folder-path :> *resolved-root-folder-path))
  (helpers/print-event :debug
                       :movie/update
                       :resolve-root-folder
                       {:incoming *incoming-root-folder-path}
                       {:resolved *resolved-root-folder-path})
  (<<if (nil? *incoming-monitored)
    (identity *existing-monitored :> *resolved-monitored)
    (else>)
    (identity *incoming-monitored :> *resolved-monitored))
  (helpers/print-event :debug
                       :movie/update
                       :resolve-monitored
                       {:incoming *incoming-monitored}
                       {:resolved *resolved-monitored})
  (:> {:added                *added
       :imdb-id              *imdb-id
       :last-search-time     *resolved-last-search
       :minimum-availability *resolved-minimum-availability
       :monitored            *resolved-monitored
       :movie-file-id        *movie-file-id
       :movie-metadata-id    *existing-movie-metadata-id
       :tmdb-id              *existing-tmdb-id
       :path                 *resolved-path
       :quality-profile-id   *resolved-quality-profile-id
       :root-folder-path     *resolved-root-folder-path
       :title                *title
       :title-slug           *title-slug
       :year                 *year}))

(deframaop reconcile-tags
  [*id *existing-tags-set *resolved-tags]
  (<<with-substitutions [$$movies            (this-module-pobject-task-global "$$movies")
                         $$movies-ids-by-tag (this-module-pobject-task-global "$$movies-ids-by-tag")]
    (identity (set/difference *existing-tags-set *resolved-tags) :> *tags-to-remove)
    (helpers/print-event :warn :movie/update :reconcile-tags :tags-to-remove *tags-to-remove)
    ;; remove tag indexes that are no longer present
    (<<if (not (empty? *tags-to-remove))
      (ops/explode *tags-to-remove :> *tag-to-remove)
      (|hash$$ $$movies-ids-by-tag *tag-to-remove)
      (local-transform> [(keypath *tag-to-remove) (termval nil)] $$movies-ids-by-tag))
    (identity (set/difference *resolved-tags *existing-tags-set) :> *tags-to-add)
    (helpers/print-event :warn :movie/update :reconcile-tags :tags-to-add *tags-to-add)
    (<<if (not (empty? *tags-to-add))
      (ops/explode *tags-to-add :> *tag-to-add)
      (|hash$$ $$movies-ids-by-tag *tag-to-add)
      (local-transform> [(keypath *tag-to-add) NONE-ELEM (termval *id)] $$movies-ids-by-tag))))

(deframaop movie-update
  [{:keys [*id *last-search-time *minimum-availability *monitored *path *quality-profile-id *root-folder-path *tags]}]
  (<<with-substitutions [$$movies                   (this-module-pobject-task-global "$$movies")
                         $$movies-id-by-tmdb-id     (this-module-pobject-task-global "$$movies-id-by-tmdb-id")
                         $$movies-id-by-metadata-id (this-module-pobject-task-global "$$movies-id-by-metadata-id")
                         $$movies-ids-by-monitored  (this-module-pobject-task-global "$$movies-ids-by-monitored")
                         $$movies-ids-by-tag        (this-module-pobject-task-global "$$movies-ids-by-tag")]
    (helpers/print-event :debug
                         :movie/update :incoming
                         :payload      {:id                   *id
                                        :monitored            *monitored
                                        :minimum-availability *minimum-availability
                                        :path                 *path
                                        :quality-profile-id   *quality-profile-id
                                        :root-folder-path     *root-folder-path
                                        :tags                 *tags
                                        :last-search-time     *last-search-time})
    (<<if (nil? *id)
      (helpers/print-event :warn :movie/update :missing-id :payload {:id *id})
      (ack-return> {:status :not-found :movie {:id *id}})
      (else>)
      (helpers/print-event :debug :movie/update :searching-movie-row :id *id)
      (|hash$$ $$movies *id)
      (local-select> (keypath *id) $$movies :> *existing-movie-row)
      (helpers/print-event :debug :movie/update :found-movie-row :movie *existing-movie-row)
      (<<if (not (nil? *existing-movie-row))
        (identity (get *existing-movie-row :tmdb-id) :> *existing-tmdb-id)
        (identity (get *existing-movie-row :movie-metadata-id) :> *existing-metadata-id)
        (identity (get *existing-movie-row :tags) :> *existing-tags-set)
        (<<shadowif *existing-tags-set nil? #{})
        (identity *monitored :> *incoming-monitored)
        (identity *tags :> *incoming-tags)
        (<<shadowif *incoming-tags empty? nil)
        (resolve-movie-update *existing-movie-row
                              *last-search-time
                              *minimum-availability
                              *incoming-monitored
                              *path
                              *quality-profile-id
                              *root-folder-path
                              :>
                              *movie-row-base)
        (<<if (nil? *incoming-tags)
          (identity *existing-tags-set :> *resolved-tags)
          (else>)
          (identity (set *incoming-tags) :> *resolved-tags))
        (identity (assoc *movie-row-base :tags *resolved-tags) :> *movie-row)
        (helpers/print-event :debug :movie/update :resolved-update {:id *id} *movie-row)
        (local-transform> [(keypath *id) (termval *movie-row)] $$movies)
        ;; ensure id indexes stay consistent
        (|hash$$ $$movies-id-by-tmdb-id *existing-tmdb-id)
        (local-transform> [(keypath *existing-tmdb-id) (termval *id)] $$movies-id-by-tmdb-id)
        (|hash$$ $$movies-id-by-metadata-id *existing-metadata-id)
        (local-transform> [(keypath *existing-metadata-id) (termval *id)] $$movies-id-by-metadata-id)
        (<<if (not (nil? *incoming-monitored))
          (|hash$$ $$movies-ids-by-monitored true)
          (local-select> (keypath true) $$movies-ids-by-monitored :> *monitored-set)
          (<<if (nil? *monitored-set)
            (identity #{} :> *monitored-set)
            (else>)
            (identity *monitored-set :> *monitored-set))
          (<<if *incoming-monitored
            (helpers/print-event :debug :movie/update :adding-to-monitored :id *id)
            (identity (conj *monitored-set *id) :> *updated-monitored-set)
            (else>)
            (helpers/print-event :debug :movie/update :removing-from-monitored :id *id)
            (identity (disj *monitored-set *id) :> *updated-monitored-set))
          (helpers/print-event :debug
                               :movie/update
                               :saving-monitored-set
                               {:id *id :incoming-monitored *incoming-monitored}
                               *updated-monitored-set)
          (local-transform> [(keypath true) (termval *updated-monitored-set)] $$movies-ids-by-monitored)
          (else>)
          (identity nil))
        (<<if (not (nil? *incoming-tags)) (reconcile-tags *id *existing-tags-set *resolved-tags) (else>) (identity nil))
        (helpers/print-event :debug :movie/update :returning-updated :id *id)
        (ack-return> {:status :updated :movie {:id *id}})
        (else>)
        (helpers/print-event :warn :movie/update :movie-not-found :id *id)
        (ack-return> {:status :not-found :movie {:id *id}})))))
