require "test_helper"

class WebTranslationsTest < ActiveSupport::TestCase
  test "catalogs contain the same messages and interpolation parameters with every plural form" do
    catalogs = %w[en de pl].to_h do |locale|
      [ locale, YAML.load_file(Rails.root.join("config/locales/#{locale}.yml")).fetch(locale) ]
    end
    source = messages(catalogs.fetch("en"))
    catalogs.each do |locale, catalog|
      translated = messages(catalog)
      assert_equal source.keys.sort, translated.keys.sort, locale
      source.each do |key, value|
        translations = translated.fetch(key)
        if value.is_a?(Hash)
          expected_forms = locale == "pl" ? %w[few many one other] : %w[one other]
          assert_equal expected_forms, translations.keys.sort, "#{locale}: #{key}"
          value = value.fetch("other")
        else
          translations = { "value" => translations }
        end
        translations.each_value do |text|
          assert text.present?, "#{locale}: #{key}"
          assert_equal value.scan(/%\{\w+\}/).sort, text.scan(/%\{\w+\}/).sort, "#{locale}: #{key}"
        end
      end
    end
  end

  test "Polish counts use one few and many including teens and larger counts" do
    { 0 => "0 przepisów", 1 => "1 przepis", 2 => "2 przepisy", 5 => "5 przepisów",
      12 => "12 przepisów", 22 => "22 przepisy", 101 => "101 przepisów" }.each do |count, text|
      assert_equal text, I18n.t("web.recipe_count", locale: :pl, count: count)
    end
  end

  test "ingredient decimals localize without changing units or persisted shopping detail formatting" do
    helper = ApplicationController.helpers
    ingredient = Ingredient.new(amount: 1.2, amount_max: 2.4, unit: "tbsp")
    I18n.with_locale(:pl) do
      assert_equal "1,2–2,4 tbsp", helper.format_quantity(ingredient)
      assert_equal "1.2–2.4 tbsp", helper.format_quantity(ingredient, locale: :en)
      assert_equal "½", helper.format_amount(0.5)
    end
  end

  test "browser language does not leak into English communication templates" do
    I18n.with_locale(:pl) do
      mail = PasswordsMailer.reset(users(:one)).message
      assert_includes mail.html_part.body.decoded, "15 minutes"
      assert_includes mail.text_part.body.decoded, "15 minutes"
      assert_equal :pl, I18n.locale
    end
  end

  private

  def messages(tree, prefix = nil)
    tree.each_with_object({}) do |(key, value), result|
      path = [ prefix, key ].compact.join(".")
      if value.is_a?(Hash) && !value.key?("other")
        result.merge!(messages(value, path))
      else
        result[path] = value
      end
    end
  end
end
