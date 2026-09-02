# Payments live in the iOS app (RevenueCat). The web only explains what Pro is
# and points at the App Store; users.pro is set by the RevenueCat webhook.
class ProController < ApplicationController
  # "MainCourse: Recipes & Cookbook", App Store Connect app id 6758990872.
  APP_STORE_URL = "https://apps.apple.com/app/id6758990872".freeze

  def show
    @user = Current.user
    @remaining_imports = @user.remaining_imports
    @app_store_url = APP_STORE_URL
  end
end
