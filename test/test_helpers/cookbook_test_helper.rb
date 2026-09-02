module CookbookTestHelper
  # Gives `user` the one shared cookbook they are allowed, as owner.
  def create_shared_cookbook_for(user, name: "Our Kitchen")
    cookbook = Cookbook.create!(name: name, personal: false)
    CookbookMembership.create!(cookbook: cookbook, user: user, role: :owner)
    cookbook
  end

  def join_cookbook(cookbook, user, role: :collaborator)
    CookbookMembership.create!(cookbook: cookbook, user: user, role: role)
  end
end

ActiveSupport.on_load(:action_dispatch_integration_test) do
  include CookbookTestHelper
end
