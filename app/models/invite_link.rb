# Builds the URL a cookbook invitation is shared as.
#
# Production uses a universal link on the canonical host so the link opens the
# iOS app; other environments use the custom scheme, since a simulator has no
# associated-domain association to resolve.
class InviteLink
  def self.url_for(token)
    if Rails.env.production?
      canonical_host = Rails.application.config.x.canonical_host
      if canonical_host.blank?
        raise "config.x.canonical_host is not set; cannot build a production invite link"
      end

      "https://#{canonical_host}/invite/#{token}"
    else
      "hauptgang://invite/#{token}"
    end
  end
end
