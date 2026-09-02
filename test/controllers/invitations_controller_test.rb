require "test_helper"

class InvitationsControllerTest < ActionDispatch::IntegrationTest
  setup do
    @inviter = users(:two)
    @invitee = users(:one)
    @shared = create_shared_cookbook_for(@inviter, name: "Our Kitchen")
    @invitation = @shared.cookbook_invitations.create!(inviter: @inviter)
  end

  # --- Landing page --------------------------------------------------------

  test "shows the invitation to a signed-out visitor and offers sign in" do
    get invite_path(@invitation.token)

    assert_response :success
    assert_match "Our Kitchen", response.body
    assert_match @inviter.email_address, response.body
    assert_select "a[href=?]", new_session_path
  end

  test "remembers where to come back to after signing in" do
    get invite_path(@invitation.token)

    assert_equal invite_url(@invitation.token), session[:return_to_after_authenticating]
  end

  test "offers accept and decline to a signed-in visitor" do
    sign_in_as @invitee

    get invite_path(@invitation.token)

    assert_response :success
    assert_select "[data-testid=accept-invite]"
  end

  test "shows an unknown token gracefully" do
    get invite_path("nope")

    assert_response :success
    assert_match "This invitation doesn't exist", response.body
  end

  test "shows an expired invitation" do
    @invitation.update!(expires_at: 1.day.ago)

    get invite_path(@invitation.token)

    assert_match "expired", response.body
  end

  test "blocks joining when the user already has a shared cookbook" do
    create_shared_cookbook_for(@invitee, name: "My Other Kitchen")
    sign_in_as @invitee

    get invite_path(@invitation.token)

    assert_match "already have a shared cookbook", response.body
    assert_select "[data-testid=accept-invite]", 0
  end

  # --- Accept --------------------------------------------------------------

  test "accepting joins the cookbook as a collaborator and switches to it" do
    sign_in_as @invitee

    assert_difference "CookbookMembership.count", 1 do
      post accept_invite_path(@invitation.token)
    end

    membership = @shared.cookbook_memberships.find_by(user: @invitee)
    assert membership.collaborator?
    assert @invitation.reload.accepted?
    assert_equal @shared.id, session[:cookbook_id]
    assert_redirected_to recipes_path
  end

  test "accepting requires signing in first" do
    post accept_invite_path(@invitation.token)

    assert_redirected_to new_session_path
  end

  test "accepting refuses when the user already has a shared cookbook" do
    create_shared_cookbook_for(@invitee, name: "My Other Kitchen")
    sign_in_as @invitee

    assert_no_difference "CookbookMembership.count" do
      post accept_invite_path(@invitation.token)
    end

    assert_match "already have a shared cookbook", flash[:alert]
  end

  test "accepting an expired invitation is refused" do
    @invitation.update!(expires_at: 1.day.ago)
    sign_in_as @invitee

    assert_no_difference "CookbookMembership.count" do
      post accept_invite_path(@invitation.token)
    end

    assert_equal "This invitation is no longer available.", flash[:alert]
  end

  test "accepting twice just opens the cookbook" do
    sign_in_as @invitee
    post accept_invite_path(@invitation.token)

    second = @shared.cookbook_invitations.create!(inviter: @inviter)
    assert_no_difference "CookbookMembership.count" do
      post accept_invite_path(second.token)
    end

    assert_redirected_to recipes_path
  end

  # --- Reject --------------------------------------------------------------

  test "declining marks the invitation rejected" do
    sign_in_as @invitee

    post reject_invite_path(@invitation.token)

    assert @invitation.reload.rejected?
    assert_redirected_to recipes_path
  end
end
