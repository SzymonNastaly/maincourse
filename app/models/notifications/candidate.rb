module Notifications
  # What a campaign returns when it has something to say to a user. `recipe` is nil for
  # campaigns that target the shopping list rather than a specific recipe.
  Candidate = Data.define(:campaign, :recipe, :cookbook, :title, :body)
end
