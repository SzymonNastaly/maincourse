require "test_helper"

class WebLocalizationTest < ActionDispatch::IntegrationTest
  test "browser preferences negotiate supported base languages and restore the request locale" do
    {
      "de-CH,de;q=0.9,en;q=0.8" => "de",
      "fr,pl-PL;q=0.8,de;q=0.5" => "pl",
      "de;q=0,pl;q=0.5" => "pl",
      "de;q=bogus,pl;q=0.5" => "pl",
      "pl;q=1.5,de;q=0.5" => "de",
      "fr,*;q=0.5" => "en",
      "../../de" => "en",
      "" => "en"
    }.each do |header, language|
      get new_session_path, headers: { "Accept-Language" => header }
      assert_response :success
      assert_select "html[lang='#{language}']"
      assert_equal language, response.headers["Content-Language"]
      assert_equal :en, I18n.locale
    end
  end

  test "explicit language persists through sign in and can return to browser defaults" do
    patch locale_path, params: { locale: "pl" }, headers: { "Referer" => new_session_url }
    assert_redirected_to new_session_path
    post session_path, params: { email_address: users(:one).email_address, password: "password" }
    get recipes_path, headers: { "Accept-Language" => "de" }
    assert_select "html[lang=pl]"
    assert_select "h1", text: "Wszystkie przepisy"

    patch locale_path, params: { locale: "auto" }, headers: { "Referer" => edit_settings_url }
    get edit_settings_path, headers: { "Accept-Language" => "de" }
    assert_select "html[lang=de]"
    assert_select "h1", text: "Einstellungen"
  end

  test "unsupported preferences and external redirect targets are ignored" do
    cookies[:web_locale] = "unsupported"
    get new_session_path, headers: { "Accept-Language" => "pl" }
    assert_select "html[lang=pl]"

    [ "fr", "../../pl", [ "pl" ], { language: "pl" } ].each do |invalid|
      patch locale_path, params: { locale: invalid, return_to: "https://other.example/sign_in" }, headers: { "Referer" => "https://other.example/sign_in" }
      assert_redirected_to root_path
      assert_equal "unsupported", cookies[:web_locale]
    end
  end

  test "language changes preserve deep links even without a referrer" do
    path = edit_password_path(users(:one).password_reset_token)
    patch locale_path, params: { locale: "pl", return_to: path }
    assert_redirected_to path
    follow_redirect!
    assert_select "html[lang=pl]"
    assert_select "h1", text: "Ustaw nowe hasło"
  end

  test "every web screen renders in each locale with user content preserved" do
    sign_in_as users(:one)
    recipe = recipes(:one)
    shared = create_shared_cookbook_for(users(:one))
    invitation = shared.cookbook_invitations.create!(inviter: users(:one))
    paths = [ recipes_path, recipe_path(recipe), new_recipe_path, edit_recipe_path(recipe),
              search_path(q: recipe.name), shopping_list_items_path, cookbooks_path,
              edit_settings_path, account_path, pro_path, invite_path(invitation.token),
              invite_path("missing"), new_session_path, new_registration_path,
              new_password_path, edit_password_path(users(:one).password_reset_token),
              confirm_apple_account_creation_path ]

    %w[en de pl].each do |locale|
      paths.each do |path|
        get path, headers: { "Accept-Language" => locale }
        assert_response :success, "#{locale}: #{path}"
        assert_select "html[lang='#{locale}']"
        assert_select ".translation_missing", count: 0
      end
      get recipe_path(recipe), headers: { "Accept-Language" => locale }
      assert_select "h1", text: recipe.name
      assert_equal "My Recipes", users(:one).personal_cookbook.name
    end
  end

  test "web validation and flash messages translate while API compatibility prose stays English" do
    sign_in_as users(:one)
    post recipes_path, params: { recipe: { name: "" } }, headers: { "Accept-Language" => "pl" }
    assert_response :unprocessable_entity
    assert_select "[data-testid=form-errors]", text: /Nazwa.*puste/

    post session_path, params: { email_address: "nobody@example.com", password: "wrong" }, headers: { "Accept-Language" => "de" }
    assert_equal "Versuche eine andere E-Mail-Adresse oder ein anderes Passwort.", flash[:alert]
    post api_v1_session_path, params: { email_address: "nobody@example.com", password: "wrong" }, as: :json,
      headers: { "Accept-Language" => "pl" }
    assert_response :unauthorized
    assert_equal "invalid_credentials", response.parsed_body["error_code"]
    assert_equal :en, I18n.locale
  end

  test "translated interpolation escapes user data and failed imports use codes" do
    sign_in_as users(:one)
    cookbook = users(:one).personal_cookbook
    cookbook.update!(name: '<script>alert("cookbook")</script>')
    cookbook.recipes.create!(name: "Importing…", user: users(:one), import_status: :failed,
      import_error_code: "no_recipe_in_photo", error_message: "Legacy English failure")

    get recipes_path, headers: { "Accept-Language" => "pl" }
    assert_select "script", text: /alert\("cookbook"\)/, count: 0
    assert_includes response.body, "&lt;script&gt;"
    assert_select "[data-testid=failed-import]", text: /Na tym zdjęciu nie ma przepisu/
    assert_not_includes response.body, "Legacy English failure"
  end
end
