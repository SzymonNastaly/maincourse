module Notifications
  # What a campaign returns when it has something to say to a user. `recipe` is nil for
  # campaigns that target the shopping list rather than a specific recipe.
  #
  # The text is a translation key rather than a rendered string: Notifications::Deliver
  # renders it separately for each device's app language.
  Candidate = Data.define(:campaign, :recipe, :cookbook, :body_key, :body_params) do
    def initialize(body_params: {}, **)
      super
    end

    def title
      I18n.t("push.lifecycle.title")
    end

    def body
      I18n.t(body_key, **body_params)
    end

    def alert
      { title: title, body: body }
    end
  end
end
