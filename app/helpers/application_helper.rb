module ApplicationHelper
  # Inline lucide icon at the design's 1.9 stroke. `size` is in px, matching
  # the numbers in docs/web-design/parts.mjs.
  def icon(name, size: 16, stroke: 1.9, **options)
    options[:class] = [ "shrink-0", options[:class] ].compact.join(" ")
    lucide_icon(name.to_s, size: size, "stroke-width" => stroke, **options)
  end

  # The MainCourse mark — the same leather cookbook used as the iOS app icon and
  # in the getmaincourse.com header. Transparent PNG, sized by height like the
  # landing site does, so the book's own proportions are kept.
  def brand_mark(size: 22)
    image_tag "logo.png", alt: "", height: size,
              class: "w-auto shrink-0",
              style: "height: #{size}px"
  end

  # Two-letter avatar fallback: initials from the name, else the email.
  def user_initials(user)
    return "?" if user.blank?

    parts = user.name.to_s.split(/\s+/).reject(&:blank?)
    return parts.first(2).map { |p| p[0] }.join.upcase if parts.any?

    user.email_address.to_s[0, 2].upcase
  end

  # Numerics — times, servings, counts, prices, IDs — are set in IBM Plex Mono.
  def mono(value, **options)
    options[:class] = [ "font-mono", options[:class] ].compact.join(" ")
    tag.span(value, **options)
  end

  def page_title(title)
    content_for(:title) { title }
  end

  # Flash keys Rails uses map onto two visual treatments.
  def flash_styles(kind)
    case kind.to_s
    when "alert", "error"
      { container: "border-danger-line bg-danger-tint text-danger", icon: "triangle-alert" }
    else
      { container: "border-accent-line bg-accent-tint text-accent", icon: "circle-check" }
    end
  end
end
