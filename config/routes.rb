Rails.application.routes.draw do
  mount_avo
  namespace :api do
    namespace :v1 do
      resource :registration, only: [ :create ]
      resource :session, only: [ :create, :destroy ]
      resource :account, only: [ :update, :destroy ]
      resource :onboarding_response, only: [ :create ]
      resources :device_tokens, only: [ :create, :destroy ], param: :token, constraints: { token: /[^\/]+/ }
      resources :recipe_views, only: [ :create ]
      resources :notification_deliveries, only: [] do
        post :opened, on: :member
      end
      resources :shopping_list_items, only: [ :index, :create, :update, :destroy ] do
        collection do
          delete :destroy_all
        end
      end
      resources :recipes, only: [ :index, :show, :update, :destroy ] do
        resource :favorite, only: [ :update, :destroy ]
        collection do
          get :batch
          post :import
          post :import_with_content
          post :extract_from_text
          post :extract_from_image
        end
      end

      resources :cookbooks, only: [ :index, :create, :destroy ] do
        post :leave, on: :member
        resources :invitations, controller: "cookbook_invitations", only: [ :create ]
        resources :meal_plans, only: [ :index ] do
          collection do
            post ":date/entries", to: "meal_plan_entries#create", as: :date_entries
            patch ":date/select", to: "meal_plan_selections#update", as: :date_select
            delete ":date/select", to: "meal_plan_selections#destroy"
          end
        end
      end
      resources :meal_plan_entries, only: [ :destroy ] do
        resource :vote, controller: "meal_plan_votes", only: [ :create, :destroy ]
      end
      resources :invitations, controller: "cookbook_invitations", only: [ :show ], param: :token do
        member do
          post :accept
          post :reject
        end
      end

      namespace :webhooks do
        post "revenuecat", to: "revenuecat#create"
      end
    end
  end

  resources :recipes do
    member do
      patch :toggle_favorite
    end
    collection do
      get "new/form", to: "recipes#new_form", as: :new_form
      get "new/import", to: "recipes#new_import", as: :new_import
      post "import", to: "recipes#import", as: :import
    end
  end
  get "invite/:token", to: "invitations#show", as: :invite

  root to: redirect("/recipes")
  resource :session
  resources :passwords, param: :token

  resource :registration, only: [ :new, :create ]
  resource :settings, only: [ :edit, :update ]
  # Define your application routes per the DSL in https://guides.rubyonrails.org/routing.html

  # Reveal health status on /up that returns 200 if the app boots with no exceptions, otherwise 500.
  # Can be used by load balancers and uptime monitors to verify that the app is live.
  get "up" => "rails/health#show", as: :rails_health_check
end
