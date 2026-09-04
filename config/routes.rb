Rails.application.routes.draw do
  mount_avo
  namespace :api do
    namespace :v1 do
      resource :registration, only: [ :create ]
      resource :session, only: [ :create, :destroy ]
      resource :oauth_session, only: [ :create ]
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

  # --- Web (HTML) ---------------------------------------------------------
  root to: redirect("/recipes")

  resource :session, only: [ :new, :create, :destroy ]
  resource :registration, only: [ :new, :create ]
  resources :passwords, param: :token
  get "auth/google_oauth2/callback", to: "omniauth_callbacks#create"
  post "auth/apple/callback", to: "omniauth_callbacks#create"
  get "auth/failure", to: "omniauth_callbacks#failure"

  # The cookbook the web UI is scoped to. See CookbookScoped.
  resource :active_cookbook, only: [ :update ]

  resources :recipes do
    member do
      patch :move
    end
    collection do
      post :import
      post :import_photo
    end
    scope module: :recipes do
      resources :shopping_list_items, only: [ :create ]
    end
  end

  get "search", to: "searches#show", as: :search

  resources :shopping_list_items, path: "shopping_list", only: [ :index, :create, :destroy ] do
    member do
      patch :toggle
    end
    collection do
      delete :destroy_all
    end
  end

  resources :cookbooks, only: [ :index, :create, :destroy ] do
    member do
      post :leave
    end
    resource :invitation, only: [ :create ], module: :cookbooks
  end

  get "invite/:token", to: "invitations#show", as: :invite
  post "invite/:token/accept", to: "invitations#accept", as: :accept_invite
  post "invite/:token/reject", to: "invitations#reject", as: :reject_invite

  resource :settings, only: [ :edit, :update ]
  resource :account, only: [ :show, :destroy ]
  get "pro", to: "pro#show", as: :pro

  # Reveal health status on /up that returns 200 if the app boots with no exceptions, otherwise 500.
  # Can be used by load balancers and uptime monitors to verify that the app is live.
  get "up" => "rails/health#show", as: :rails_health_check
end
