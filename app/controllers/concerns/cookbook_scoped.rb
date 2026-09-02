# Resolves the cookbook the web UI is currently looking at.
#
# Mirrors the iOS CookbookContext: the selection is remembered (in the session),
# and when there is no valid selection we prefer the shared cookbook and fall
# back to the personal one. Every web screen except search is scoped to it.
module CookbookScoped
  extend ActiveSupport::Concern

  included do
    before_action :set_current_cookbook
    helper_method :current_cookbook, :available_cookbooks
  end

  private

  def set_current_cookbook
    Current.cookbook = resolve_current_cookbook
  end

  def resolve_current_cookbook
    return nil if Current.user.blank?

    selected = available_cookbooks.find { |cookbook| cookbook.id == session[:cookbook_id] }
    selected || default_cookbook
  end

  # Shared first, personal second — the same preference the iOS app applies on login.
  def default_cookbook
    available_cookbooks.find { |cookbook| !cookbook.personal? } || available_cookbooks.first
  end

  def available_cookbooks
    return [] if Current.user.blank?

    @available_cookbooks ||= Current.user.cookbooks.order(:personal, :created_at).to_a
  end

  def current_cookbook
    Current.cookbook
  end

  def switch_cookbook(cookbook)
    session[:cookbook_id] = cookbook.id
    Current.cookbook = cookbook
    @available_cookbooks = nil
  end

  # Guards controllers that cannot function without a cookbook.
  def require_cookbook
    return if current_cookbook

    redirect_to cookbooks_path, alert: "Pick a cookbook first."
  end
end
