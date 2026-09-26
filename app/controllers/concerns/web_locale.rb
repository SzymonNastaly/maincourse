module WebLocale
  extend ActiveSupport::Concern

  included do
    prepend_around_action :with_web_locale
  end

  private

  def with_web_locale(&action)
    locale = cookies[:web_locale].presence_in(I18n.available_locales.map(&:to_s)) ||
      browser_locale ||
      I18n.default_locale

    I18n.with_locale(locale) do
      response.set_header("Content-Language", I18n.locale.to_s)
      response.set_header("Vary", [ response.get_header("Vary"), "Accept-Language", "Cookie" ].compact.join(", "))
      action.call
    end
  end

  def browser_locale
    # Rack parses the weights; validate each range first and ignore exclusions.
    ranges = request.headers["Accept-Language"].to_s.split(",").select do |range|
      range.strip.match?(/\A(?:[a-z]{2,8}(?:-[a-z0-9]{1,8})*|\*)(?:\s*;\s*q=(?:0(?:\.\d{0,3})?|1(?:\.0{0,3})?))?\z/i)
    end
    Rack::Utils.q_values(ranges.join(",")).each_with_index.sort_by { |(_, quality), index| [ -quality, index ] }.each do |(range, quality), _|
      language = range.downcase.split("-").first
      return language if quality.positive? && I18n.available_locales.map(&:to_s).include?(language)
    end
    nil
  end
end
