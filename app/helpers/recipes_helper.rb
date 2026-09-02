module RecipesHelper
  # "1h 25m · serves 4" — the mono meta line under a card title.
  def recipe_meta(recipe)
    total = recipe.prep_time.to_i + recipe.cook_time.to_i
    parts = []
    parts << format_duration(total) if total.positive?
    parts << "serves #{recipe.servings}" if recipe.servings.to_i.positive?
    parts.join(" · ")
  end

  # 85 => "1h 25m", 35 => "35m", 240 => "4h"
  def format_duration(minutes)
    minutes = minutes.to_i
    return "" unless minutes.positive?

    hours, remainder = minutes.divmod(60)
    return "#{remainder}m" if hours.zero?
    return "#{hours}h" if remainder.zero?

    "#{hours}h #{remainder}m"
  end

  # Recipes without a cover image get one of the mockup's food gradients,
  # picked deterministically so a recipe always looks the same.
  PLACEHOLDER_GRADIENTS = [
    "linear-gradient(150deg, #C9B08F 0%, #A2794F 100%)",
    "linear-gradient(150deg, #BFCFA8 0%, #7E9560 100%)",
    "linear-gradient(150deg, #DEC0A0 0%, #B8834F 100%)",
    "linear-gradient(150deg, #C8BBA6 0%, #94795C 100%)",
    "linear-gradient(150deg, #DCBCAD 0%, #A96450 100%)",
    "linear-gradient(150deg, #D0CABB 0%, #99907C 100%)",
    "linear-gradient(150deg, #DED6C2 0%, #ADA283 100%)",
    "linear-gradient(150deg, #C6A8B4 0%, #8E5F72 100%)",
    "linear-gradient(150deg, #E3CE9C 0%, #C09A44 100%)",
    "linear-gradient(150deg, #B7C9A4 0%, #6F8757 100%)",
    "linear-gradient(150deg, #D2AE9A 0%, #9C6248 100%)",
    "linear-gradient(150deg, #E0CDAC 0%, #BE9A62 100%)"
  ].freeze

  def recipe_placeholder_gradient(recipe)
    key = recipe.id || recipe.name.to_s
    PLACEHOLDER_GRADIENTS[Digest::MD5.hexdigest(key.to_s).to_i(16) % PLACEHOLDER_GRADIENTS.size]
  end

  # A recipe's source_url is user-supplied (the form lets you type one), so only
  # link it when it is really an http(s) URL — never `javascript:` and friends.
  def safe_source_url(recipe)
    return nil if recipe.source_url.blank?

    uri = URI.parse(recipe.source_url)
    return nil unless uri.is_a?(URI::HTTP) && uri.host.present?

    uri.to_s
  rescue URI::InvalidURIError
    nil
  end

  # The host shown next to the external-link icon on a recipe.
  def source_domain(recipe)
    url = safe_source_url(recipe)
    return nil if url.nil?

    URI.parse(url).host.sub(/\Awww\./, "")
  end

  # Format an Ingredient's quantity for display.
  #
  # Examples:
  #   amount=200, unit="g"            => "200 g"
  #   amount=0.5, unit="tsp"          => "½ tsp"
  #   amount=2, amount_max=3          => "2–3"
  #   amount=200, amount_max=250, "g" => "200–250 g"
  #   amount=nil,  unit="pinch"       => "pinch"
  #   amount=nil,  unit=nil           => ""
  def format_quantity(ingredient)
    amount = ingredient.amount
    amount_max = ingredient.amount_max
    unit = ingredient.unit.presence

    quantity =
      if amount.present? && amount_max.present?
        "#{format_amount(amount)}\u2013#{format_amount(amount_max)}"
      elsif amount.present?
        format_amount(amount)
      else
        nil
      end

    [ quantity, unit ].compact.join(" ")
  end

  # Format an amount for scaled display, given a numeric value.
  # Used by the portion scaler controller for the initial render parity check
  # and is the canonical formatter for client-side scaling output.
  def format_amount(value)
    return "" if value.nil?

    decimal = value.is_a?(BigDecimal) ? value : BigDecimal(value.to_s)

    if (fraction = unicode_fraction(decimal))
      return fraction
    end

    rounded = decimal.round(2)
    string = rounded.to_s("F")
    string.sub(/\.?0+\z/, "")
  end

  UNICODE_FRACTIONS = {
    BigDecimal("0.25")    => "\u00BC",
    BigDecimal("0.5")     => "\u00BD",
    BigDecimal("0.75")    => "\u00BE",
    BigDecimal("0.3333")  => "\u2153",
    BigDecimal("0.6667")  => "\u2154",
    BigDecimal("0.125")   => "\u215B",
    BigDecimal("0.375")   => "\u215C",
    BigDecimal("0.625")   => "\u215D",
    BigDecimal("0.875")   => "\u215E"
  }.freeze

  def unicode_fraction(decimal)
    UNICODE_FRACTIONS.each do |key, glyph|
      return glyph if (decimal - key).abs < BigDecimal("0.005")
    end
    nil
  end
end
