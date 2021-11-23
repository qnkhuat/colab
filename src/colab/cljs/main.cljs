(ns colab.cljs.main
  (:require [reagent.dom :as rd]
            [colab.cljs.components.mui :refer [Button]]
            [colab.cljs.lib.webrtc :as webrtc]))


(defn local-video []
  (js/document.getElementById "local-video"))

(defn remote-video []
  (js/document.getElementById "remote-video"))

(defn get-video
  []
  (.then (.getUserMedia (.-mediaDevices js/navigator) (clj->js {:audio true :video true}))
         (fn [stream] (set! (.-srcObject (local-video)) stream))))


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
