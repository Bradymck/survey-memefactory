(ns survey-memefactory.ui.home.page
  (:require
    [cljs-web3.core :as web3]
    [district.format :as format]
    [district.ui.component.page :refer [page]]
    [district.ui.graphql.subs :as gql]
    [district.ui.web3-accounts.subs :as accounts-subs]
    [medley.core :as medley]
    [re-frame.core :refer [subscribe dispatch]]
    [reagent.core :as r]
    [survey-memefactory.shared.surveys :refer [surveys]]
    [district.ui.web3-tx-id.subs :as tx-id-subs]
    [survey-memefactory.ui.components.app-layout :refer [app-layout]]
    [cljs-time.core :as t]
    [print.foo :include-macros true]))

(defn options [{:keys [:survey/options :survey/end-date :survey/address] :as args}]
  [:div.options
   (doall
     (for [{:keys [:option/id :option/text :option/image :option/total-votes :option/voter-voted?]} options]
       (let [percentage (format/format-percentage total-votes (:survey/total-votes args))
             pending? @(subscribe [::tx-id-subs/tx-pending? {:survey/address address :option/id id}])]
         [:div.option
          {:key (str address id)}
          [:div.option-row
           (if text
             [:div.option.text id ". " text]
             [:div.option.image image])
           (if (t/after? end-date (t/now))
             [:button.vote
              {:on-click (fn []
                           (when address
                             (dispatch [:vote {:option/id id :survey/address address}])))
               :class (when (or (not address)
                                pending?
                                voter-voted?
                                (not @(subscribe [::accounts-subs/active-account])))
                        "disabled")}
              (cond
                pending? "Voting..."
                voter-voted? "Voted"
                :else "Vote")])]
          [:div.votes-bar-body
           [:div.bar [:span.bar-index
                      {:style {:width percentage}}]]
           [:div.percentage
            (format/format-number (web3/from-wei total-votes :ether))
            " (" percentage ")"]]])))])


(defn survey [{:keys [:survey/title :key :survey/total-votes :survey/voter-votes] :as args}]
  [:div.survey
   [:div.title key ". " title]
   [:div.total-votes
    [:span.label "Start Date: "]
    (format/format-date (:survey/start-date args))]
   [:div.total-votes
    [:span.label "Total Votes: "]
    (format/format-token (web3/from-wei total-votes :ether)
                         {:token "DNT"})]
   [:div.total-votes
    [:span.label "You Voted: "]
    (format/format-token (web3/from-wei voter-votes :ether)
                         {:token "DNT"})
    " (" (format/format-percentage voter-votes total-votes) ")"]
   [:div.survey-address
    [:span.label "Contract Address: "
     (if (:survey/addressr args)
       [:a {:href (str "https://etherscan.io/address/" (:survey/address args))
            :target :_blank}
        (:survey/address args)]
       "Not deployed yet")]]
   [options args]])


(defn calculate-total [surveys key]
  (-> (reduce (fn [acc item]
                (+ acc (or (get item key) 0)))
              0
              (print.foo/look surveys))
    (web3/from-wei :ether)))


(defn total-stats [{:keys [:surveys]}]
  (let [total-votes (calculate-total surveys :survey/total-votes)
        voter-votes (calculate-total surveys :survey/voter-votes)
        percentage (format/format-percentage voter-votes total-votes)]
    [:div.total-stats
     [:div "Total Votes: " (format/format-token total-votes {:token "DNT"})]
     [:div "You Voted: " (format/format-token voter-votes {:token "DNT"})]
     [:div "You are eligible to obtain "
      (format/format-token (* (/ voter-votes total-votes) 1000000000)
                           {:token "DANK"})
      " (" percentage ")"]]))


(defmethod page :route/home []
  (let [active-account (subscribe [::accounts-subs/active-account])]
    (fn []
      (let [query @(subscribe [::gql/query
                               {:queries [[:surveys
                                           [:survey/address
                                            :survey/total-votes
                                            [:survey/voter-votes {:voter @active-account}]
                                            [:survey/options [:option/id
                                                              :option/text
                                                              :option/total-votes
                                                              [:option/voter-voted?
                                                               {:voter @active-account}]]]]]]}
                               {:refetch-on #{:vote-success :auto-refresh}}])]
        [app-layout
         {:meta {:title "survey-memefactory"
                 :description "Description"}}
         [:div.home
          [:h2.title "MemeFactory Survey"]
          [:div "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum."]
          [total-stats
           {:surveys (:surveys query)}]
          [:div.surveys
           (doall
             (for [[i s] (medley/indexed surveys)]
               [survey (merge s
                              {:key (inc i)}
                              (get-in query [:surveys i]))]))]]
         ]))))