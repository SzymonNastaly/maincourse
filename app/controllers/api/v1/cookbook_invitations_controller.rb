module Api
  module V1
    class CookbookInvitationsController < BaseController
      skip_before_action :set_current_cookbook!

      # POST /api/v1/cookbooks/:cookbook_id/invitations
      def create
        cookbook = current_user.cookbooks.find_by(id: params[:cookbook_id])
        if cookbook.nil?
          return render_api_error "not_found", error: "Cookbook not found", status: :not_found
        end

        unless cookbook.owner?(current_user)
          return render_api_error "owner_required", error: "Only the owner can create invitations", status: :forbidden
        end

        if cookbook.personal?
          return render_api_error "personal_cookbook_invitation", error: "Cannot invite to personal cookbook", status: :unprocessable_entity
        end

        # Expire previous pending invitations so only one is active at a time
        cookbook.cookbook_invitations.pending.update_all(status: :expired)

        invitation = cookbook.cookbook_invitations.create!(inviter: current_user)

        render json: {
          id: invitation.id,
          token: invitation.token,
          invite_url: InviteLink.url_for(invitation.token),
          expires_at: invitation.expires_at
        }, status: :created
      end

      # GET /api/v1/invitations/:token
      def show
        invitation = CookbookInvitation.includes(cookbook: { cookbook_memberships: :user }).find_by(token: params[:token])
        if invitation.nil?
          return render_api_error "invitation_unavailable", error: "Invitation not found", status: :not_found
        end

        render json: invitation_preview_json(invitation)
      end

      # POST /api/v1/invitations/:token/accept
      def accept
        invitation = CookbookInvitation.active.find_by(token: params[:token])
        if invitation.nil?
          return render_api_error "invitation_unavailable", error: "Invitation not found or expired", status: :not_found
        end

        if invitation.cookbook.cookbook_memberships.exists?(user: current_user)
          return render_api_error "already_cookbook_member", error: "You are already a member of this cookbook", status: :unprocessable_entity
        end

        current_user.with_lock do
          if current_user.shared_cookbook.present?
            return render_api_error "shared_cookbook_exists", error: "You already have a shared cookbook", status: :unprocessable_entity
          end

          ActiveRecord::Base.transaction do
            CookbookMembership.create!(cookbook: invitation.cookbook, user: current_user, role: :collaborator)
            invitation.accepted!
          end
        end

        render json: {
          cookbook_id: invitation.cookbook_id,
          cookbook_name: invitation.cookbook.name
        }
      end

      # POST /api/v1/invitations/:token/reject
      def reject
        invitation = CookbookInvitation.active.find_by(token: params[:token])
        if invitation.nil?
          return render_api_error "invitation_unavailable", error: "Invitation not found or expired", status: :not_found
        end

        invitation.rejected!
        head :no_content
      end

      private

      def invitation_preview_json(invitation)
        {
          cookbook_name: invitation.cookbook.name,
          inviter_email: invitation.inviter.email_address,
          expires_at: invitation.expires_at,
          status: invitation.status
        }
      end
    end
  end
end
