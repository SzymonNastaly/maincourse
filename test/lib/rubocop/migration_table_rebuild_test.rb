require "test_helper"
require "rubocop"
require Rails.root.join("lib/rubocop/cop/maincourse/migration_table_rebuild")

class MigrationTableRebuildTest < ActiveSupport::TestCase
  REBUILDS = [
    "change_column :users, :name, :text",
    "change_column_null :users, :password_digest, true",
    "change_column_default :users, :role, 0",
    "remove_column :users, :nickname",
    "remove_columns :users, :a, :b",
    "rename_column :users, :name, :full_name",
    "add_timestamps :users",
    "remove_timestamps :users",
    "add_foreign_key :recipes, :users",
    "remove_foreign_key :recipes, :users",
    "add_check_constraint :users, \"length(name) > 0\"",
    "remove_check_constraint :users, \"length(name) > 0\"",
    "remove_reference :recipes, :user",
    "remove_belongs_to :recipes, :user"
  ].freeze

  REBUILDS.each do |statement|
    test "flags #{statement[/\A\w+/]}" do
      assert_offense migration(statement)
    end

    test "accepts #{statement[/\A\w+/]} when the DDL transaction is disabled" do
      assert_no_offense migration(statement, disabled: true)
    end
  end

  # The case the old regex-based guard could not see.
  test "flags rebuilding calls inside a change_table block" do
    assert_offense migration(<<~RUBY)
      change_table :users do |t|
        t.change :password_digest, :string, null: true
      end
    RUBY
  end

  test "flags every change_table block form that rebuilds" do
    %w[change change_default change_null rename remove foreign_key
       remove_foreign_key check_constraint remove_check_constraint
       remove_references remove_belongs_to timestamps].each do |method|
      assert_offense migration("change_table(:users) { |t| t.#{method} :thing }"),
                     "expected t.#{method} to be flagged"
    end
  end

  # create_table builds a new table, so none of this applies inside it.
  test "ignores create_table blocks" do
    assert_no_offense migration(<<~RUBY)
      create_table :users do |t|
        t.references :account, foreign_key: true
        t.string :email, null: false
        t.timestamps
      end
    RUBY
  end

  test "flags add_reference that adds a foreign key" do
    assert_offense migration("add_reference :recipes, :user, foreign_key: true")
  end

  test "accepts add_reference without a foreign key or null constraint" do
    assert_no_offense migration("add_reference :recipes, :user, index: true")
  end

  test "flags a NOT NULL column with no default, which SQLite cannot append" do
    assert_offense migration("add_column :users, :locale, :string, null: false")
  end

  test "accepts a NOT NULL column that supplies a default" do
    assert_no_offense migration("add_column :users, :locale, :string, null: false, default: \"de\"")
  end

  test "accepts a plain added column" do
    assert_no_offense migration("add_column :users, :locale, :string")
  end

  test "accepts a table creation on its own" do
    assert_no_offense migration("create_table(:widgets) { |t| t.string :name }")
  end

  private
    def migration(body, disabled: false)
      <<~RUBY
        class TestMigration < ActiveRecord::Migration[8.1]
          #{"disable_ddl_transaction!" if disabled}

          def change
            #{body.gsub("\n", "\n    ")}
          end
        end
      RUBY
    end

    def offenses(source)
      config = RuboCop::Config.new({ "Maincourse/MigrationTableRebuild" => { "Enabled" => true } }, "/")
      cop = RuboCop::Cop::Maincourse::MigrationTableRebuild.new(config)
      processed = RuboCop::ProcessedSource.new(source, RUBY_VERSION.to_f, "db/migrate/20260101000000_test.rb")

      RuboCop::Cop::Commissioner.new([ cop ], [], raise_error: true).investigate(processed).offenses
    end

    def assert_offense(source, message = "expected an offense")
      assert_not_empty offenses(source), "#{message}:\n#{source}"
    end

    def assert_no_offense(source)
      found = offenses(source)

      assert_empty found.map(&:message), "unexpected offense:\n#{source}"
    end
end
