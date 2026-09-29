# SQLite's LOWER() and LIKE only fold ASCII, so "śmietana" never matched
# "Śmietana" and "apfel" never matched "Äpfel". Web search compares both sides
# through this fold instead: accents stripped, Unicode case folding (ß → ss),
# and ł → l, which has no decomposition to strip.
module SearchFold
  def self.call(value)
    value&.unicode_normalize(:nfkd)&.gsub(/\p{Mn}/, "")&.downcase(:fold)&.tr("ł", "l")
  end

  # Registers search_fold(text) on every SQLite connection Rails opens.
  module Registration
    private
      def configure_connection
        super
        @raw_connection.create_function("search_fold", 1) { |function, value| function.result = SearchFold.call(value) }
      end
  end
end

ActiveSupport.on_load(:active_record_sqlite3adapter) { prepend SearchFold::Registration }
