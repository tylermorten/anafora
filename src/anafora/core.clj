(ns anafora.core
  (:require [clojure.core.async :refer [go close! go-loop <! <!! >! chan timeout]])
  (:import (com.bloomberglp.blpapi Event)
           (com.bloomberglp.blpapi Element)
           (com.bloomberglp.blpapi InvalidRequestException)
           (com.bloomberglp.blpapi Message)
           (com.bloomberglp.blpapi MessageIterator)
           (com.bloomberglp.blpapi Name)
           (com.bloomberglp.blpapi Request)
           (com.bloomberglp.blpapi Service)
           (com.bloomberglp.blpapi Session)
           (com.bloomberglp.blpapi SessionOptions)
           (com.bloomberglp.blpapi Event$EventType)))

(def api-ref-data-svc "//blp/refdata")

 ; session options
(def ^:dynamic *session-options* (new SessionOptions))

(def ^:dynamic *security-data* (Name/getName "securityData"))
(def ^:dynamic *security* (Name/getName "security"))
(def ^:dynamic *field-data* (Name/getName "fieldData"))

; if you need anything other than localhost you will have
; use Clojures bindings and create your own *session-options*
(.setServerHost *session-options* "localhost")
(.setServerPort *session-options* 8194)

(defn start-session
  [session]
  (.start session)
  (.openService session api-ref-data-svc)
  session)

(defn create-session
  []
  (let [session (Session. *session-options*)]
    (start-session session)))

;; data service
(defn get-data-service
  [session]
  (.getService session api-ref-data-svc))

(defn setup-request
  [request s f]
 (let [securities (.getElement request "securities")
        fields (.getElement request "fields")]
    (doseq [security s]
      (.appendValue securities security))
    (doseq [field f]
      (.appendValue fields field)))
  request)

(defmacro defreferencerequest
  [name securities fields]
  `(def ~name
     {:securities ~securities :fields ~fields :request-type "ReferenceDataRequest"}))

(defn create-request
  [session securities fields type]
  (->
   (.createRequest (get-data-service session) type)
   (setup-request securities fields)))

(declare event-loop)

(defn send-request
  [request]
  (let [{:keys [session request c]} (event-loop request)]
    (.sendRequest session request nil)
    {:c c
     :session session
     :request request}))

(defn get-next-event
  [session]
  (.nextEvent session))

(defn get-event-iterator
  [event]
  (.messageIterator event))

(defn get-next-message
  [iterator]
  (.next iterator))

(defn has-next-message?
  [iterator]
  (.hasNext iterator))

(defn process-fields
  [security]
  (if (.hasElement security *field-data*)
    (let [fields (.getElement security *field-data*)
          num-fields (.numElements fields)]
      (for [i (range num-fields)]
        (let [field (.getElement fields i)
              name  (.name field)
              value (.getValueAsString field)]
          {(keyword (clojure.string/lower-case (.toString name))) value}))
      )))

(defn process-security
  [security]
  (let [ticker (.getElementAsString security *security*)]
    (if-not (.hasElement security "securityError")
      (into {} (process-fields security)))))

(defn process-securities
  [message]
  (let [securities (.getElement message *security-data*)
        num-securities (.numValues securities)]
    (for [i (range num-securities)]
      (process-security (.getValueAsElement securities i)))))

(defn process-event
  [event] 
  (if-let [iterator (get-event-iterator event)]
    (loop [data []]
      (if-not (has-next-message? iterator)
        data
        (let [message (get-next-message iterator)]
          (if (.hasElement message *security-data*) 
            (recur (into data (process-securities message)))
            (recur data)))))))

(defn event-loop
  [{:keys [securities fields request-type]}]
  (let [c (chan)
        session (create-session)
        request (create-request session securities fields request-type)]
    (go
     (while true
       (let [event (.nextEvent session)]
         (cond
          (= (.eventType event) Event$EventType/PARTIAL_RESPONSE (>! c event))
          (= (.eventType event) Event$EventType/RESOLUTION_STATUS (>! c event))
          :else
          (let [msgIter (.messageIterator event)]
            (while (.hasNext msgIter)
              (if-let [msg (.next msgIter)]
                (if (= (.eventType event) Event$EventType/SESSION_STATUS)
                  (if
                      (or (= (.messageType msg) "SessionTerminated")
                          (= (.messageType msg) "SessionStartupFailure"))
                    (close! c))))))))))
     {:c c
      :request request
      :session session}))
   
(defn submit-request
  [request]
  (let [{:keys [c session]} (send-request request)
        size (count (:securities request))]
    (loop [bonds []]
      (if (= (count bonds) size)
        (do (.stop session)
            bonds)
        (when-let [data (<!! c)]
          (recur (into bonds (process-event data))))))))

