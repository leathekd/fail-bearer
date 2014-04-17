(ns fail.bearer
  (:require [carica.core :as carica]
            [clojure.string :as str]
            [taoensso.timbre :as log]
            [tentacles.issues :as issues]
            [tentacles.pulls :as pulls]))

(log/refer-timbre)

(def date-fmt (java.text.SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ss'Z'"))

(def config (carica/configurer (carica/resources "config.edn") []))

(defn make-date [date-str]
  (.parse date-fmt date-str))

(defn build-status [user repo-name oauth-token pr]
  (let [status (->> (tentacles.core/api-call
                     :get "/repos/%s/%s/statuses/%s"
                     [user repo-name (get-in pr [:head :sha])]
                     {:oauth-token oauth-token})
                    (sort-by :id)
                    (last))]
    {:state (:state status)
     :created-at (make-date (:created_at status))}))

(defn build-failed? [pr]
  (or (= "failure" (get-in pr [:status :state]))
      (= "error" (get-in pr [:status :state]))))

(defn comments [user repo-name oauth-token pr-number]
  (for [comment (->> (issues/issue-comments user repo-name pr-number
                                            {:oauth-token oauth-token})
                     (filter seq)
                     (sort-by :id))]
    {:body (:body comment)
     :created-at (make-date (:created_at comment))}))

(defn pull-requests [user repo-name oauth-token]
  (let [prs (pulls/pulls user repo-name {:oauth-token oauth-token})]
    ;; check to see if there is an HTTP status, if so, it was a bad
    ;; request
    (if-not (:status prs)
      (for [pr (filter seq prs)]
        {:number (:number pr)
         :status (build-status user repo-name oauth-token pr)
         :comments (comments user repo-name oauth-token (:number pr))})
      (log/error "Got a bad result from github:" prs))))

(defn add-comment [user repo-name oauth-token pr-number comment]
  (issues/create-comment user repo-name pr-number
                         comment
                         {:oauth-token oauth-token}))

(defn failure-image
  "takes the pr and returns a unique image or just a random one if
  this pr is really bad."
  [pr]
  (let [urls (config :failure-images)
        all-comments (str/join " " (map :body (:comments pr)))
        new-gif (some #(when-not (.contains all-comments (str "(" % ")")) %)
                      (shuffle urls))]
    (or new-gif (rand-nth urls))))

(defn failure-quote
  "take sthe pr and returns a random quote from the config file or an
  empty string if there are no quotes"
  [pr]
  (let [quotes (config :failure-quotes)
        all-comments (str/join " " (map :body (:comments pr)))
        new-quote (some #(when-not (.contains all-comments (str "(" % ")")) %)
                        quotes)]
    (or new-quote (rand-nth quotes) "Build failed.")))

(defn comment-required?
  "A comment is required unless a failure gif has been posted since
  the last build"
  [pr]
  (let [build-date (get-in pr [:status :created-at])
        ;; if build-date is greater than created-at, then remove it
        new-comments (remove #(pos? (.compareTo build-date (:created-at %)))
                             (:comments pr))
        comments-str (str/join " " (map :body new-comments))]
    (boolean (not (some #(.contains comments-str %)
                        (config :failure-images))))))

(defn process-pr [user repo-name oauth-token pr]
  (when (and (build-failed? pr)
             (comment-required? pr))
    (if-let [image (failure-image pr)]
      (add-comment user repo-name oauth-token (:number pr)
                   (str/trim (format "![%s](%s)\n\n%s"
                                     (failure-quote pr)
                                     image
                                     (or (config :comment-disclaimer) ""))))
      (log/warn "Can't post a funny image as none are defined"))))

(defn process-prs []
  (log/set-config! [:appenders :spit :enabled?] true)
  (log/set-config! [:shared-appender-config :spit-filename] "fail-bearer.log")
  (log/info :starting-process)
  (doseq [repo (config :github :repos)
          :let [oauth-token (config :github :oauth-token)
                [user repo-name] (str/split repo #"/")]
          pr (pull-requests user repo-name oauth-token)]
    (log/debug "processing pr" (:number pr))
    (process-pr user repo-name oauth-token pr)))

(defn -main [& args]
  (process-prs))
