(ns chessground.ui
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [chessground.data :as data]
            [chessground.chess :as chess]
            [chessground.common :as common :refer [pp]]
            [cljs.core.async :as a])
  (:require-macros [cljs.core.async.macros :as am]))

(def board-class "cg-board")
(def square-class "cg-square")
(def piece-class "cg-piece")
(def last-move-class "last-move")
(def check-class "check")
(def dest-class "dest")

(defn- classes [cs] (clojure.string/join " " (filter identity cs)))

(defn square-view [square owner]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:className (classes [square-class
                                         (when (:check? square) check-class)
                                         (when (:last-move? square) last-move-class)
                                         (when (:dest? square) dest-class)])
                    :data-key (:key square)}
               (when-let [piece (:piece square)]
                 (dom/div #js {:className (classes [piece-class (:color piece) (:role piece)])}))))))

(defn- api-handler [app chan]
  (am/go
    (while true
      (let [[function data] (a/<! chan)]
        (case function
          :set-orientation (om/transact! app :orientation #(data/set-orientation % data))
          :toggle-orientation (om/transact! app :orientation data/toggle-orientation)
          :get-orientation (a/>! data (:orientation @app))
          :get-position (a/>! data (chess/get-pieces (:chess @app)))
          :set-fen (om/update! app :chess (chess/make (or data "start")))
          :clear (om/update! app :chess chess/clear)
          :api-move (om/transact! app :chess #(chess/move-piece % data))
          :set-last-move (om/transact! app :chess #(chess/set-last-move % data))
          :set-check (om/transact! app :chess #(chess/set-check % data))
          :set-pieces (om/transact! app :chess #(chess/set-pieces % data))
          :set-dests (om/transact! app :chess #(chess/set-dests % data)))))))

(defn board-view [app owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (api-handler app (om/get-shared owner :api-chan)))
    om/IRender
    (render [_]
      (let [white (= (:orientation app) "white")]
        (apply dom/div #js {:className board-class}
               (for [rank (range 1 9)
                     file-n (range 1 9)
                     :let [file (get "abcdefgh" (dec file-n))
                           key (str file rank)
                           pos {(if white "left" "right") (str (* (dec file-n) 12.5) "%")
                                (if white "bottom" "top") (str (* (dec rank) 12.5) "%")}
                           coord-x (when (= rank (if white 1 8)) file)
                           coord-y (when (= file-n (if white 8 1)) rank)]]
                 (dom/div (clj->js (cond-> {:style pos}
                                     coord-x (merge {:data-coord-x coord-x})
                                     coord-y (merge {:data-coord-y coord-y})))
                          (om/build
                            square-view
                            (get-in app [:chess key])
                            {:react-key key}))))))))