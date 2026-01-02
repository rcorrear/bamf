(ns bamf.rest-api.core-test
  (:require [bamf.casing :as casing]
            [bamf.rest-api.core :as core]
            [bamf.rest-api.routes :as routes]
            [bamf.rest-api.spec :as raspec]
            [clojure.test :refer [deftest is testing]]
            [taoensso.telemere :as t]
            [aleph.http :as http]))

(deftest get-routes-defaults-to-empty-vector
  (is (= [] (core/get-routes)) "get-routes should default to empty route vector when no catalog is provided")
  (is (= [:a] (core/get-routes {:routes [:a]})) "get-routes should pull :routes from the provided catalog"))

(deftest wrap->kebab->camel-normalizes-request-and-response
  (let [captured (atom nil)
        handler  (fn [req]
                   (reset! captured req)
                   {:status 200 :body {:movie-id 42 :nested-response {:next-action "go"}}})
        wrapped  (casing/wrap->kebab->camel handler)
        response (wrapped {:params       {"movieId" 1 "deepValue" {"innerValue" 2}}
                           :body-params  {"pathValue" "x"}
                           :form-params  {"movieId" 2}
                           :query-params {"pageNumber" 3}
                           :path-params  {"movieId" "abc"}
                           :parameters   {:path {"movieId" "zzz"} :query {"pageNumber" 5}}})]
    (testing "Request maps are normalized to kebab-case (string keys for incoming params)"
      (is (= {"movie-id" 1 "deep-value" {"inner-value" 2}} (:params @captured)))
      (is (= {"movie-id" 2} (:form-params @captured)))
      (is (= {"movie-id" "abc"} (:path-params @captured)))
      (is (= {:path {"movie-id" "zzz"} :query {"page-number" 5}} (:parameters @captured))))
    (testing "Response bodies are camelized"
      (is (= {:movieId 42 :nestedResponse {:nextAction "go"}} (:body response))))))

(deftest handler-prefers-repl-in-dev-and-static-otherwise
  (let [calls (atom [])]
    (with-redefs [core/static-ring-handler        (fn [rt catalog] (swap! calls conj [:static rt catalog]) :static)
                  core/repl-friendly-ring-handler (fn [rt catalog] (swap! calls conj [:repl rt catalog]) :repl)
                  t/log!                          (fn [& args] (swap! calls conj [:log args]))]
      (is (= :repl (#'core/handler :development {:cfg true} {:routes [:r]})))
      (is (= :static (#'core/handler :production {:cfg true} {:routes [:r]})))
      (is (some #(= [:repl {:cfg true} {:routes [:r]}] %) @calls))
      (is (some #(= [:static {:cfg true} {:routes [:r]}] %) @calls)))))

(deftest start-wires-server-with-validated-config
  (let [calls       (atom [])
        fake-server {:server true}
        cfg         {:aleph {:port 8080} :environment :test :http-components {:c 1} :http/runtime-state {:rs true}}]
    (with-redefs [core/validate-config    (fn [spec config] (swap! calls conj [:validate spec config]))
                  routes/aggregate        (fn [ctx] (swap! calls conj [:aggregate ctx]) {:routes [:r]})
                  core/handler            (fn [env config catalog]
                                            (swap! calls conj [:handler env config catalog])
                                            :handler)
                  core/wrap-with-telemere (fn [handler opts] (swap! calls conj [:wrap handler opts]) :wrapped)
                  http/start-server       (fn [handler opts] (swap! calls conj [:start handler opts]) fake-server)
                  t/log!                  (fn [& _])]
      (is (= fake-server (core/start cfg)))
      (is (= [[:validate (raspec/get-spec) cfg] [:aggregate {:http-components {:c 1} :runtime-state {:rs true}}]
              [:handler :test cfg {:routes [:r]}] [:wrap :handler {:log-exceptions? false}]
              [:start :wrapped {:shutdown-executor? true :port 8080}]]
             @calls)))))

(deftest stop-closes-server
  (let [closed? (atom false)]
    (with-redefs [t/log! (fn [& _])]
      (core/stop (reify
                  java.io.Closeable
                    (close [_] (reset! closed? true)))))
    (is @closed?)))

(deftest static-ring-handler-returns-404-when-no-route-matches
  (let [handler (#'core/static-ring-handler {:runtime :test} {:routes []})
        resp    (handler {:request-method :get :uri "/nope"})]
    (is (= 404 (:status resp)))))

(deftest wrap-with-telemere-hooks-logger
  (let [opts    (atom nil)
        handler (fn [_] :ok)]
    (with-redefs [t/log!                       (fn [& _]) ;; silence telemetry while asserting wiring
                  ring.logger/wrap-with-logger (fn [h log-opts] (reset! opts log-opts) (fn [req] (h req)))]
      (let [wrapped (#'core/wrap-with-telemere handler {:log-exceptions? true})
            log-fn  (:log-fn @opts)]
        (is (= :ok (wrapped {:request-method :get :uri "/"})))
        (is (fn? log-fn))
        ;; log-fn should handle both success and error inputs without throwing
        (log-fn {:level :info :message "ok"})
        (log-fn {:level :error :message "fail" :throwable (Exception. "boom")})
        (is (:log-exceptions? @opts))))))
