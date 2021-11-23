(ns colab.cljs.rtc)

(def ice-candidate-config (clj->js {:iceServers            [{:urls ["stun:stun1.l.google.com:19302"
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

(defn connect
  []
  (let [peer-connection (js/RTCPeerConnection. ice-candidate-config)]
    (register-peer-connection-listeners peer-connection)))
