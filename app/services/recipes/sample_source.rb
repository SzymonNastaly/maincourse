module Recipes
  class SampleSource
    Source = Data.define(:content, :source_type, :source_key, :image_path, :attribution)

    SAMPLE_DIRECTORY = Rails.root.join("config/starter_recipes")
    # Each key is one immutable, versioned recipe. Apps preview and save the
    # version for their language; older apps only know the English one.
    ALLOWED_KEYS = {
      "tomato-orzo-v1" => { file: "tomato-orzo-v1.json", locale: :en },
      "tomato-orzo-de-v1" => { file: "tomato-orzo-de-v1.json", locale: :de },
      "tomato-orzo-pl-v1" => { file: "tomato-orzo-pl-v1.json", locale: :pl }
    }.freeze

    class UnknownKey < StandardError; end

    def self.resolve(key:)
      sample = ALLOWED_KEYS[key]
      raise UnknownKey, "Unknown sample" unless sample

      content = JSON.parse(SAMPLE_DIRECTORY.join(sample[:file]).read)
      raise UnknownKey, "Invalid sample" unless content.fetch("key") == key

      image_name = content.fetch("image_name")
      raise UnknownKey, "Invalid sample image" unless File.basename(image_name) == image_name

      Source.new(
        content: content,
        source_type: "sample",
        source_key: key,
        image_path: SAMPLE_DIRECTORY.join(image_name),
        attribution: I18n.t("starter_recipes.attribution", locale: sample[:locale])
      )
    rescue JSON::ParserError, KeyError
      raise UnknownKey, "Invalid sample"
    end
  end
end
