class LocalesController < ApplicationController
  allow_unauthenticated_access

  def update
    if params[:locale].is_a?(String) && I18n.available_locales.map(&:to_s).include?(params[:locale])
      cookies.permanent[:web_locale] = { value: params[:locale], httponly: true, same_site: :lax }
    elsif params[:locale] == "auto"
      cookies.delete(:web_locale)
    end

    destination = url_from(params[:return_to]) if params[:return_to].is_a?(String)
    if destination
      redirect_to destination, status: :see_other
    else
      redirect_back fallback_location: root_path, allow_other_host: false, status: :see_other
    end
  end
end
