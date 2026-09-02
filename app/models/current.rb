class Current < ActiveSupport::CurrentAttributes
  attribute :session
  # The cookbook the web UI is currently looking at. Resolved per request by
  # CookbookScoped; the API uses the X-Cookbook-Id header instead.
  attribute :cookbook
  delegate :user, to: :session, allow_nil: true
end
