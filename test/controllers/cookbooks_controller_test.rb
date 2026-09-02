require "test_helper"

class CookbooksControllerTest < ActionDispatch::IntegrationTest
  setup do
    @user = users(:one)
    @personal = cookbooks(:one_personal)
    sign_in_as @user
  end

  test "requires authentication" do
    sign_out
    get cookbooks_path
    assert_redirected_to new_session_path
  end

  test "index shows the create prompt when there is no shared cookbook" do
    get cookbooks_path

    assert_response :success
    assert_select "[data-testid=create-shared-cookbook]"
    assert_select "[data-testid=shared-cookbook]", 0
  end

  test "index lists members and roles of the shared cookbook" do
    shared = create_shared_cookbook_for(@user)
    join_cookbook(shared, users(:two))

    get cookbooks_path

    assert_select "[data-testid=shared-cookbook]"
    assert_match @user.email_address, response.body
    assert_match users(:two).email_address, response.body
    assert_match "Owner", response.body
    assert_match "Member", response.body
  end

  # --- Create --------------------------------------------------------------

  test "create makes the shared cookbook and switches to it" do
    assert_difference "Cookbook.count", 1 do
      post cookbooks_path, params: { cookbook: { name: "Our Kitchen", move_personal_recipes: "0" } }
    end

    shared = @user.reload.shared_cookbook
    assert_equal "Our Kitchen", shared.name
    assert shared.owner?(@user)
    assert_equal shared.id, session[:cookbook_id]
    assert_redirected_to cookbooks_path
  end

  test "create can move the personal recipes and shopping list across" do
    post cookbooks_path, params: { cookbook: { name: "Our Kitchen", move_personal_recipes: "1" } }

    shared = @user.reload.shared_cookbook
    assert_equal 0, @personal.recipes.count
    assert_equal 0, @personal.shopping_list_items.count
    assert_operator shared.recipes.count, :>, 0
    assert_operator shared.shopping_list_items.count, :>, 0
  end

  test "create leaves recipes in place when not asked to move them" do
    before = @personal.recipes.count

    post cookbooks_path, params: { cookbook: { name: "Our Kitchen", move_personal_recipes: "0" } }

    assert_equal before, @personal.reload.recipes.count
  end

  test "create refuses a blank name" do
    assert_no_difference "Cookbook.count" do
      post cookbooks_path, params: { cookbook: { name: "   " } }
    end

    assert_equal "Give your cookbook a name.", flash[:alert]
  end

  test "create refuses a second shared cookbook" do
    create_shared_cookbook_for(@user)

    assert_no_difference "Cookbook.count" do
      post cookbooks_path, params: { cookbook: { name: "Another" } }
    end

    assert_equal "You already have a shared cookbook.", flash[:alert]
  end

  # --- Destroy / leave -----------------------------------------------------

  test "the owner can delete the shared cookbook and falls back to personal" do
    shared = create_shared_cookbook_for(@user)

    assert_difference "Cookbook.count", -1 do
      delete cookbook_path(shared)
    end

    assert_equal @personal.id, session[:cookbook_id]
  end

  test "the personal cookbook cannot be deleted" do
    assert_no_difference "Cookbook.count" do
      delete cookbook_path(@personal)
    end

    assert_equal "Your personal cookbook cannot be deleted.", flash[:alert]
  end

  test "a collaborator cannot delete the cookbook" do
    shared = create_shared_cookbook_for(users(:two))
    join_cookbook(shared, @user)

    assert_no_difference "Cookbook.count" do
      delete cookbook_path(shared)
    end

    assert_equal "Only the owner can delete this cookbook.", flash[:alert]
  end

  test "a collaborator can leave" do
    shared = create_shared_cookbook_for(users(:two))
    join_cookbook(shared, @user)

    assert_difference "CookbookMembership.count", -1 do
      post leave_cookbook_path(shared)
    end

    assert Cookbook.exists?(shared.id)
    assert_equal @personal.id, session[:cookbook_id]
  end

  test "the owner cannot leave" do
    shared = create_shared_cookbook_for(@user)

    assert_no_difference "CookbookMembership.count" do
      post leave_cookbook_path(shared)
    end

    assert_equal "Owners cannot leave. Delete the cookbook instead.", flash[:alert]
  end

  test "cannot touch a cookbook the user does not belong to" do
    assert_no_difference "Cookbook.count" do
      delete cookbook_path(cookbooks(:two_personal))
    end

    assert_equal "Cookbook not found.", flash[:alert]
  end

  # --- Invitations ---------------------------------------------------------

  test "the owner can generate an invite link, expiring the previous one" do
    shared = create_shared_cookbook_for(@user)
    old = shared.cookbook_invitations.create!(inviter: @user)

    assert_difference "CookbookInvitation.count", 1 do
      post cookbook_invitation_path(shared)
    end

    assert old.reload.expired?
    assert_equal 1, shared.cookbook_invitations.active.count
    assert_equal "Invite link created.", flash[:notice]
  end

  test "a collaborator cannot generate an invite link" do
    shared = create_shared_cookbook_for(users(:two))
    join_cookbook(shared, @user)

    assert_no_difference "CookbookInvitation.count" do
      post cookbook_invitation_path(shared)
    end

    assert_equal "Only the owner can invite people.", flash[:alert]
  end

  test "the personal cookbook cannot be shared" do
    assert_no_difference "CookbookInvitation.count" do
      post cookbook_invitation_path(@personal)
    end

    assert_equal "You cannot invite anyone to your personal cookbook.", flash[:alert]
  end
end
