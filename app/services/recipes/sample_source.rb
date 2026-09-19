module Recipes
  class SampleSource
    Source = Data.define(:content, :source_type, :source_key, :image_path, :attribution)

    SAMPLE_DIRECTORY = Rails.root.join("config/starter_recipes")
    ALLOWED_KEYS = {
      "tomato-orzo-v1" => "tomato-orzo-v1.json"
    }.freeze

    class UnknownKey < StandardError; end

    def self.resolve(key:)
      filename = ALLOWED_KEYS[key]
      raise UnknownKey, "Unknown sample" unless filename

      content = JSON.parse(SAMPLE_DIRECTORY.join(filename).read)
      raise UnknownKey, "Invalid sample" unless content.fetch("key") == key

      image_name = content.fetch("image_name")
      raise UnknownKey, "Invalid sample image" unless File.basename(image_name) == image_name

      Source.new(
        content: content,
        source_type: "sample",
        source_key: key,
        image_path: SAMPLE_DIRECTORY.join(image_name),
        attribution: "An original MainCourse example recipe."
      )
    rescue JSON::ParserError, KeyError
      raise UnknownKey, "Invalid sample"
    end
  end
end
