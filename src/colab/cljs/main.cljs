(ns colab.cljs.main
  (:require [reagent.dom :as rd]
            [colab.cljs.components.mui :refer [Button]]
            [colab.cljs.lib.webrtc :as webrtc]))


(enable-console-print!)

(defn local-video []
  (js/document.getElementById "local-video"))

(defn remote-video []
  (js/document.getElementById "remote-video"))

(defn set-stream
  [video stream]
  (set! (.-srcObject video ) stream)
  (set! (.-onloadedmetadata video) (fn [] (.play video))))

(defn get-video
  []
  (-> js/navigator
      .-mediaDevices
      (.getUserMedia #js {:video true :audio false})
      (.then (fn [stream]
               (set-stream (local-video) stream)))))



(defn Application []
  [:<>
   [:h1 "alo ha"]
   [:video {:id "local-video"
            :class "border-2 border-black m-2"}]
   [:video {:id "remote-video"
            :class "border-2 border-red-400 m-2"}]
   [Button {:on-click (fn []  (get-video))}
    "Request devices"]])

(defn init []
  (rd/render
   [Application]
   (js/document.getElementById "root")))
