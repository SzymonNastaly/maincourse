# SQLite's LOWER() and LIKE only fold ASCII, so "śmietana" never matched
# "Śmietana" and "apfel" never matched "Äpfel". Web search compares both sides
# through this fold instead: accents stripped, Unicode case folding (ß → ss),
# and ł → l, which has no decomposition to strip.
module SearchFold
  def self.call(value)
    value&.unicode_normalize(:nfkd)&.gsub(/\p{Mn}/, "")&.downcase(:fold)&.tr("ł", "l")
  end
end
