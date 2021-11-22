(ns colab.cljs.main
  (:require [reagent.dom :as rd]))

(defn Application []
  [:div
   [:h1 "alo ha"]])

(defn init []
  (rd/render
    [Application]
    (js/document.getElementById "root")))
