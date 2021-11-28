(ns colab.cljs.main
  (:require [reagent.dom :as rd]
            [reagent.core :as r]
            [clojure.edn :as edn]
            [colab.cljs.components.mui :refer [Button]]))

(defonce ws-conn (r/atom nil))
(defonce peer-conn (r/atom nil))

(def ice-candidate-config (clj->js {:iceServers           [{:urls ["stun:stun1.l.google.com:19302"
                                                                   "stun:stun2.l.google.com:19302"]}]
                                    :iceCandidatePoolSize 10}))

(def user-media-constraints #js {:video true
                                 :audio false})

(defn send-when-connected
  ([ws-conn msg]
   (send-when-connected ws-conn msg 0 100))


  ([ws-conn msg n limit]
   (if (< n limit)
     (if (= (.-readyState ws-conn) 1)
       (do
        (js/console.log "About to send message: " (clj->js msg))
        (.send ws-conn msg))
       (js/setTimeout (fn [] (send-when-connected ws-conn msg (inc n) limit)) 10))
     (js/console.log "Drop message due reached retry limits: " (clj->js msg)))))

(defn local-video []
  (js/document.getElementById "local-video"))

(defn remote-video []
  (js/document.getElementById "remote-video"))

(defn set-stream
  [video stream]
  (set! (.-srcObject video ) stream))

(defn add-tracks
  [peer-conn stream]
  (doseq [track (.getTracks stream)]
    (js/console.log "adding track: " track)
    (.addTrack peer-conn track)))

(defn send-offer
  [peer-conn ws-conn]
  (-> peer-conn
      .createOffer
      (.then (fn [offer]
               (.setLocalDescription peer-conn offer)
               offer))
      (.then (fn [offer]
               (send-when-connected ws-conn {:type    :offer
                                             :payload (.toJSON offer)})))
      (.catch
       (fn [e]
         (js/console.error "Failed to create offer: " e)))))


(defn handle-receive-answer
  [answer]
  (.setRemoteDescription @peer-conn (js/RTCSessionDescription. answer)))

(defn handle-receive-ice-candidate
  [ice-candidate]
  (let [ice-candidate (js/RTCIceCandidate. ice-candidate)]
    (-> @peer-conn
        (.addIceCandidate ice-candidate)
        (.catch (fn [e]
                  (js/console.error "Failed to add candidate: " e))))))

(defn handle-on-peer-conn-state-change
  [e]
  (js/console.log "Connection state changed: " e))

(defn handle-peer-on-track
  [e]
  (js/console.log "track ne: " e)
  (set-stream (remote-video) (first (.-streams e))))

(defn handle-on-ice-candidate
  [e]
  (when  (.-candidate e)
    (send-when-connected @ws-conn {:type    :candidate
                                   :payload (.toJSON (.-candidate e))})))

(defn handle-receive-offer
  [offer]
  (let [peer (js/RTCPeerConnection. ice-candidate-config)]

    (set! (.-OnConnectionStateChange peer) handle-on-peer-conn-state-change)
    (set! (.-onicecandidate peer) handle-on-ice-candidate)
    (set! (.-ontrack peer) handle-peer-on-track)
    (reset! peer-conn peer)

    (let [desc (js/RTCSessionDescription. offer)]
      (-> @peer-conn
          (.setRemoteDescription desc)
          (.then (fn []
                   (-> js/navigator
                       .-mediaDevices
                       (.getUserMedia user-media-constraints)
                       (.then (fn [stream]
                                (set-stream (local-video) stream)
                                stream))
                       (.catch (fn [e]
                                 (js/console.error "Failed to start local video: " e))))))
          (.then (fn [stream]
                   (add-tracks @peer-conn stream)))
          (.then (fn []
                   (-> @peer-conn
                       .createAnswer
                       (.then
                        (fn [answer]
                          (.setLocalDescription @peer-conn answer)
                          answer))
                       (.then (fn [answer]
                                (send-when-connected @ws-conn {:type    :answer
                                                               :payload (.toJSON answer)})))
                       (.catch
                        (fn [e]
                          (js/console.error "Failed to create offer: " e))))))))))

(defn handle-ws-message
  [msg]
  (let [msg (-> msg .-data edn/read-string)]
    (js/console.log "Got a message: " (clj->js msg))
    (case (keyword (:type msg))
      :offer     (handle-receive-offer (:payload msg))
      :answer    (handle-receive-answer (:payload msg))
      :candidate (handle-receive-ice-candidate (:payload msg))
      :else      (js/console.warn "Unhandled msg: " (:type msg)))))



(defn dial
  []
  (let [peer (js/RTCPeerConnection. ice-candidate-config)]


    (set! (.-OnConnectionStateChange peer) handle-on-peer-conn-state-change)
    (set! (.-onicecandidate peer) handle-on-ice-candidate)
    (set! (.-ontrack peer) handle-peer-on-track)
    (reset! peer-conn peer)

    (-> js/navigator
        .-mediaDevices
        (.getUserMedia user-media-constraints)
        (.then (fn [stream]
                 (set-stream (local-video) stream)
                 (add-tracks @peer-conn stream)))
        (.then (fn []
                 (send-offer @peer-conn @ws-conn)))
        (.catch (fn [e]
                  ;; TODO: handle cases when users don't have camera on audio
                  (js/console.error "Failed to start local video: " e))))))


(defn App
  []
  (let [ws   (js/WebSocket. "ws://localhost:3000/ws/")]
    (set! (.-onmessage ws) handle-ws-message)
    (set! (.-onopen ws) (fn [_e] (js/console.log "WebSocket connected!")))
    (reset! ws-conn ws)
    (r/create-class
     {:component-did-mount
      (fn [_this]
        (println "mount"))

      :reagent-render
      (fn []
        [:<>
         [:h1 "alo ha"]
         [:video {:id "local-video"
                  :autoPlay true
                  :class "border-2 border-black m-2"}]
         [:video {:id "remote-video"
                  :autoPlay true
                  :class "border-2 border-red-400 m-2"}]
         [Button {:on-click (fn [] (dial))}
          "Call"]
         ])})))

(defn init []
  (rd/render
   [App]
   (js/document.getElementById "root")))
