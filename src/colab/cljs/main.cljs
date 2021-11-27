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

(defn register-peer-connection-listeners [peer-connection]
  (doto peer-connection
    (.addEventListener "icegatheringstatechange"
                       (fn [] (js/console.log "ICE gathering state changed: " (.-iceGatheringState peer-connection))))
    (.addEventListener "connectionstatechange"
                       (fn [] (js/console.log "Connection state change: " (.-connectionState peer-connection))))
    (.addEventListener "signalingstatechange"
                       (fn [] (js/console.log "Signaling state changed: " (.-signalingState peer-connection))))
    (.addEventListener "iceconnectionstatechange"
                       (fn [] (js/console.log "ICE connection state changed: " (.-iceConnectionState peer-connection))))))

(defn send-when-connected
  ([ws-conn msg]
   (send-when-connected ws-conn msg 0 100))

  ([ws-conn msg n limit]
   (when (< n limit)
     (if (= (.-readyState ws-conn) 1)
       (do
        (js/console.log "about to send a msg: " (clj->js msg))
        (.send ws-conn msg))
       (js/setTimeout (fn [] (send-when-connected ws-conn msg (inc n) limit)) 10)))))

(defn connect
  []
  (let [conn (js/RTCPeerConnection. ice-candidate-config)]
    (register-peer-connection-listeners conn)))

(def local-stream (r/atom nil))

(defn local-video []
  (js/document.getElementById "local-video"))

(defn remote-video []
  (js/document.getElementById "remote-video"))

(defn set-stream
  [video stream]
  (set! (.-srcObject video ) stream)
  (set! (.-onloadedmetadata video) (fn [] (.play video))))

(defn handle-get-media-succeed
  [stream peer-conn]
  (set-stream (local-video) stream)
  (map #(.addTrack peer-conn %) (.getTracks stream)))

(defn send-offer
  [peer-conn ws-conn]
  (.createOffer peer-conn
                (fn [offer]
                  (.setLocalDescription peer-conn offer)
                  (send-when-connected ws-conn {:type    :offer
                                                :payload offer}))
                (fn [e]
                  (js/console.error "Failed to create offer: " e))))

(defn handle-receive-offer
  [offer]
  (let [desc (js/RTCSessionDescription. offer)]
    (-> @peer-conn
        (.setRemoteDescription desc)
        (.then (fn []
                 (-> js/navigator
                     .-mediaDevices
                     (.getUserMedia #js {:video true :audio false})
                     (.then (fn [stream]
                              (set-stream (local-video) stream)
                              (doall (map #(.addTrack @peer-conn %) (.getTracks stream)))))
                     (.catch (fn [e]
                               (js/console.error "Failed to start local video: " e))))))
        (.then (fn []
                 (.createAnswer @peer-conn
                                (fn [answer]
                                  (js/console.log "senders" (.getSenders @peer-conn))
                                  (.setLocalDescription @peer-conn answer)
                                  (send-when-connected @ws-conn {:type    :answer
                                                                 :payload answer}))
                                (fn [e]
                                  (js/console.error "Failed to create answer: " e))))))))
(defn handle-receive-answer
  [answer]
  (.setRemoteDescription @peer-conn (js/RTCSessionDescription. answer)))

(defn handle-receive-ice-candidate [ice-candidate]
  (let [ice-candidate (js/RTCIceCandidate. ice-candidate)]
    (-> @peer-conn
        (.addIceCandidate  ice-candidate)
        (.catch (fn [e]
                  (js/console.error "Failed to add candidate: " e))))))

(defn handle-ws-message
  [msg]
  (js/console.log "Got a message: " msg)
  (let [msg (-> msg .-data edn/read-string)]
    (case (keyword (:type msg))
      :offer     (handle-receive-offer (:payload msg))
      :answer    (handle-receive-answer (:payload msg))
      :candidate (handle-receive-ice-candidate (:payload msg))
      :else      (js/console.warn "Unhandled msg: " (:type msg)))))

(defn dial
  [ws-conn]
  (-> js/navigator
      .-mediaDevices
      (.getUserMedia #js {:video true :audio false})
      (.then (fn [stream]
               (set-stream (local-video) stream)
               (doall (map #(.addTrack @peer-conn %) (.getTracks stream)))))
      (.then (fn []
               (js/console.log "senders" (.getSenders @peer-conn))
               (send-offer @peer-conn @ws-conn)))

      (.catch (fn [e]
                ;; TODO: handle cases when users don't have camera on audio
                (js/console.error "Failed to start local video: " e)))))

(defn handle-on-peer-conn-state-change
  [e]
  (js/console.log "Connection state changed: " e))

(defn handle-peer-on-track
  [e]
  (js/console.log "trackne:" e)
  (set-stream (remote-video) (first (.-streams e))))

(defn handle-on-ice-candidate
  [e]
  (when  (.-candidate e)
    (send-when-connected @ws-conn {:type    :candidate
                                   :payload (.toJSON (.-candidate e))})))

(defn App
  []
  (let [ws   (js/WebSocket. "ws://localhost:3000/ws/")
        peer (js/RTCPeerConnection. ice-candidate-config)]

    (set! (.-onmessage ws) handle-ws-message)
    (set! (.-onopen ws) (fn [_e] (js/console.log "WebSocket connected!")))

    (set! (.-OnConnectionStateChange peer) handle-on-peer-conn-state-change)
    (set! (.-ontrack peer) handle-peer-on-track)
    (set! (.-onicecandidate peer) handle-on-ice-candidate)
    (reset! ws-conn ws)
    (reset! peer-conn peer)
    (r/create-class
     {:component-did-mount
      (fn [_this]
        (println "mount"))

      :reagent-render
      (fn []
        [:<>
         [:h1 "alo ha"]
         [:video {:id "local-video"
                  :class "border-2 border-black m-2"}]
         [:video {:id "remote-video"
                  :class "border-2 border-red-400 m-2"}]
         [Button {:on-click (fn [] (dial ws-conn))}
          "Call"]])})))

(defn init []
  (rd/render
   [App]
   (js/document.getElementById "root")))
