require "test_helper"

class Recipes::SampleSourceTest < ActiveSupport::TestCase
  test "every language version is the same recipe as the English one" do
    english = Recipes::SampleSource.resolve(key: "tomato-orzo-v1").content

    Recipes::SampleSource::ALLOWED_KEYS.each_key do |key|
      content = Recipes::SampleSource.resolve(key: key).content

      assert_equal english.keys.sort, content.keys.sort, key
      %w[prep_time cook_time servings image_name creator].each do |field|
        assert_equal english.fetch(field), content.fetch(field), "#{key} #{field}"
      end
      assert_equal english.fetch("ingredients").map { it.values_at("amount") },
        content.fetch("ingredients").map { it.values_at("amount") }, key
      assert_equal english.fetch("ingredients").first.keys, content.fetch("ingredients").first.keys, key
      assert_equal english.fetch("instructions").size, content.fetch("instructions").size, key
    end
  end

  test "language versions carry translated text and attribution" do
    { "tomato-orzo-de-v1" => :de, "tomato-orzo-pl-v1" => :pl }.each do |key, locale|
      source = Recipes::SampleSource.resolve(key: key)

      assert_not_equal Recipes::SampleSource.resolve(key: "tomato-orzo-v1").content.fetch("name"), source.content.fetch("name")
      assert_equal I18n.t("starter_recipes.attribution", locale: locale), source.attribution
      assert_equal key, source.source_key
    end
  end
end
