# This file is auto-generated from the current state of the database. Instead
# of editing this file, please use the migrations feature of Active Record to
# incrementally modify your database, and then regenerate this schema definition.
#
# This file is the source Rails uses to define your schema when running `bin/rails
# db:schema:load`. When creating a new database, `bin/rails db:schema:load` tends to
# be faster and is potentially less error prone than running all of your
# migrations from scratch. Old migrations may fail to apply correctly if those
# migrations use external dependencies or application code.
#
# It's strongly recommended that you check this file into your version control system.

ActiveRecord::Schema[8.1].define(version: 2026_08_29_065127) do
  create_table "active_storage_attachments", force: :cascade do |t|
    t.bigint "blob_id", null: false
    t.datetime "created_at", null: false
    t.string "name", null: false
    t.bigint "record_id", null: false
    t.string "record_type", null: false
    t.index ["blob_id"], name: "index_active_storage_attachments_on_blob_id"
    t.index ["record_type", "record_id", "name", "blob_id"], name: "index_active_storage_attachments_uniqueness", unique: true
  end

  create_table "active_storage_blobs", force: :cascade do |t|
    t.bigint "byte_size", null: false
    t.string "checksum"
    t.string "content_type"
    t.datetime "created_at", null: false
    t.string "filename", null: false
    t.string "key", null: false
    t.text "metadata"
    t.string "service_name", null: false
    t.index ["key"], name: "index_active_storage_blobs_on_key", unique: true
  end

  create_table "active_storage_variant_records", force: :cascade do |t|
    t.bigint "blob_id", null: false
    t.string "variation_digest", null: false
    t.index ["blob_id", "variation_digest"], name: "index_active_storage_variant_records_uniqueness", unique: true
  end

  create_table "api_tokens", force: :cascade do |t|
    t.datetime "created_at", null: false
    t.datetime "expires_at"
    t.datetime "last_used_at"
    t.string "name"
    t.datetime "revoked_at"
    t.string "token_digest", null: false
    t.datetime "updated_at", null: false
    t.integer "user_id", null: false
    t.index ["token_digest"], name: "index_api_tokens_on_token_digest", unique: true
    t.index ["user_id"], name: "index_api_tokens_on_user_id"
  end

  create_table "cookbook_invitations", force: :cascade do |t|
    t.integer "cookbook_id", null: false
    t.datetime "created_at", null: false
    t.datetime "expires_at", null: false
    t.integer "inviter_id", null: false
    t.integer "status", default: 0, null: false
    t.string "token", null: false
    t.datetime "updated_at", null: false
    t.index ["cookbook_id"], name: "index_cookbook_invitations_on_cookbook_id"
    t.index ["inviter_id"], name: "index_cookbook_invitations_on_inviter_id"
    t.index ["token"], name: "index_cookbook_invitations_on_token", unique: true
  end

  create_table "cookbook_memberships", force: :cascade do |t|
    t.integer "cookbook_id", null: false
    t.datetime "created_at", null: false
    t.integer "role", default: 0, null: false
    t.datetime "updated_at", null: false
    t.integer "user_id", null: false
    t.index ["cookbook_id", "user_id"], name: "index_cookbook_memberships_on_cookbook_id_and_user_id", unique: true
    t.index ["cookbook_id"], name: "index_cookbook_memberships_on_cookbook_id"
    t.index ["user_id"], name: "index_cookbook_memberships_on_user_id"
  end

  create_table "cookbooks", force: :cascade do |t|
    t.datetime "created_at", null: false
    t.string "name", null: false
    t.boolean "personal", default: false, null: false
    t.datetime "updated_at", null: false
  end

  create_table "device_tokens", force: :cascade do |t|
    t.datetime "created_at", null: false
    t.string "environment", default: "production", null: false
    t.datetime "last_used_at"
    t.string "token", null: false
    t.datetime "updated_at", null: false
    t.integer "user_id", null: false
    t.index ["token"], name: "index_device_tokens_on_token", unique: true
    t.index ["user_id"], name: "index_device_tokens_on_user_id"
  end

  create_table "ingredients", force: :cascade do |t|
    t.decimal "amount", precision: 10, scale: 4
    t.decimal "amount_max", precision: 10, scale: 4
    t.datetime "created_at", null: false
    t.string "name"
    t.string "note"
    t.integer "position", null: false
    t.string "raw", null: false
    t.integer "recipe_id", null: false
    t.string "unit"
    t.datetime "updated_at", null: false
    t.index ["recipe_id"], name: "index_ingredients_on_recipe_id"
  end

  create_table "meal_plan_entries", force: :cascade do |t|
    t.datetime "created_at", null: false
    t.integer "meal_plan_id", null: false
    t.integer "proposed_by_user_id"
    t.integer "recipe_id", null: false
    t.datetime "updated_at", null: false
    t.index ["meal_plan_id", "recipe_id"], name: "index_meal_plan_entries_on_meal_plan_id_and_recipe_id", unique: true
    t.index ["meal_plan_id"], name: "index_meal_plan_entries_on_meal_plan_id"
    t.index ["proposed_by_user_id"], name: "index_meal_plan_entries_on_proposed_by_user_id"
    t.index ["recipe_id"], name: "index_meal_plan_entries_on_recipe_id"
  end

  create_table "meal_plan_votes", force: :cascade do |t|
    t.datetime "created_at", null: false
    t.integer "meal_plan_entry_id", null: false
    t.datetime "updated_at", null: false
    t.integer "user_id", null: false
    t.index ["meal_plan_entry_id", "user_id"], name: "index_meal_plan_votes_on_meal_plan_entry_id_and_user_id", unique: true
    t.index ["meal_plan_entry_id"], name: "index_meal_plan_votes_on_meal_plan_entry_id"
    t.index ["user_id"], name: "index_meal_plan_votes_on_user_id"
  end

  create_table "meal_plans", force: :cascade do |t|
    t.integer "cookbook_id", null: false
    t.datetime "created_at", null: false
    t.date "date", null: false
    t.datetime "selected_at"
    t.integer "selected_by_user_id"
    t.integer "selected_entry_id"
    t.datetime "updated_at", null: false
    t.index ["cookbook_id", "date"], name: "index_meal_plans_on_cookbook_id_and_date", unique: true
    t.index ["cookbook_id"], name: "index_meal_plans_on_cookbook_id"
    t.index ["selected_by_user_id"], name: "index_meal_plans_on_selected_by_user_id"
    t.index ["selected_entry_id"], name: "index_meal_plans_on_selected_entry_id"
  end

  create_table "onboarding_responses", force: :cascade do |t|
    t.json "answers", default: {}, null: false
    t.datetime "created_at", null: false
    t.string "device_id", null: false
    t.datetime "updated_at", null: false
    t.integer "user_id"
    t.index ["device_id"], name: "index_onboarding_responses_on_device_id", unique: true
    t.index ["user_id"], name: "index_onboarding_responses_on_user_id"
  end

  create_table "pending_notifications", force: :cascade do |t|
    t.integer "actor_id", null: false
    t.string "category", null: false
    t.integer "cookbook_id", null: false
    t.datetime "created_at", null: false
    t.datetime "delivery_scheduled_at"
    t.json "payload", default: [], null: false
    t.integer "recipient_id", null: false
    t.datetime "updated_at", null: false
    t.index ["actor_id"], name: "index_pending_notifications_on_actor_id"
    t.index ["cookbook_id", "recipient_id", "actor_id", "category"], name: "index_pending_notifications_on_bucket", unique: true
    t.index ["cookbook_id"], name: "index_pending_notifications_on_cookbook_id"
    t.index ["recipient_id"], name: "index_pending_notifications_on_recipient_id"
  end

  create_table "recipe_tags", force: :cascade do |t|
    t.datetime "created_at", null: false
    t.integer "recipe_id", null: false
    t.integer "tag_id", null: false
    t.datetime "updated_at", null: false
    t.index ["recipe_id", "tag_id"], name: "index_recipe_tags_on_recipe_id_and_tag_id", unique: true
    t.index ["recipe_id"], name: "index_recipe_tags_on_recipe_id"
    t.index ["tag_id"], name: "index_recipe_tags_on_tag_id"
  end

  create_table "recipes", force: :cascade do |t|
    t.integer "cook_time"
    t.integer "cookbook_id", null: false
    t.datetime "created_at", null: false
    t.text "error_message"
    t.datetime "failed_recipe_fetched_at"
    t.boolean "favorite", default: false
    t.integer "import_status", default: 1, null: false
    t.json "instructions", default: []
    t.string "name"
    t.text "notes"
    t.integer "prep_time"
    t.integer "servings"
    t.string "source_url"
    t.datetime "updated_at", null: false
    t.integer "user_id"
    t.index ["cookbook_id", "updated_at", "id"], name: "index_recipes_on_cookbook_id_and_updated_at_and_id"
    t.index ["cookbook_id"], name: "index_recipes_on_cookbook_id"
    t.index ["user_id", "created_at"], name: "index_recipes_on_user_id_and_created_at"
    t.index ["user_id"], name: "index_recipes_on_user_id"
  end

  create_table "sessions", force: :cascade do |t|
    t.datetime "created_at", null: false
    t.string "ip_address"
    t.datetime "updated_at", null: false
    t.string "user_agent"
    t.integer "user_id", null: false
    t.index ["user_id"], name: "index_sessions_on_user_id"
  end

  create_table "shopping_list_items", force: :cascade do |t|
    t.datetime "checked_at"
    t.string "client_id", null: false
    t.integer "cookbook_id", null: false
    t.datetime "created_at", null: false
    t.string "details"
    t.string "name", null: false
    t.integer "source_recipe_id"
    t.datetime "updated_at", null: false
    t.integer "user_id"
    t.index ["cookbook_id", "client_id"], name: "index_shopping_list_items_on_cookbook_id_and_client_id", unique: true
    t.index ["cookbook_id"], name: "index_shopping_list_items_on_cookbook_id"
    t.index ["source_recipe_id"], name: "index_shopping_list_items_on_source_recipe_id"
    t.index ["user_id", "checked_at"], name: "index_shopping_list_items_on_user_id_and_checked_at"
    t.index ["user_id"], name: "index_shopping_list_items_on_user_id"
  end

  create_table "tags", force: :cascade do |t|
    t.datetime "created_at", null: false
    t.string "name", null: false
    t.string "slug", null: false
    t.datetime "updated_at", null: false
    t.index ["slug"], name: "index_tags_on_slug", unique: true
  end

  create_table "users", force: :cascade do |t|
    t.boolean "admin", default: false, null: false
    t.datetime "created_at", null: false
    t.string "email_address", null: false
    t.datetime "last_active_at"
    t.boolean "lifecycle_notifications_enabled", default: true, null: false
    t.string "name"
    t.string "password_digest", null: false
    t.boolean "pro", default: false, null: false
    t.string "time_zone", default: "UTC", null: false
    t.datetime "updated_at", null: false
    t.index ["email_address"], name: "index_users_on_email_address", unique: true
  end

  add_foreign_key "active_storage_attachments", "active_storage_blobs", column: "blob_id"
  add_foreign_key "active_storage_variant_records", "active_storage_blobs", column: "blob_id"
  add_foreign_key "api_tokens", "users"
  add_foreign_key "cookbook_invitations", "cookbooks", on_delete: :cascade
  add_foreign_key "cookbook_invitations", "users", column: "inviter_id", on_delete: :cascade
  add_foreign_key "cookbook_memberships", "cookbooks", on_delete: :cascade
  add_foreign_key "cookbook_memberships", "users", on_delete: :cascade
  add_foreign_key "device_tokens", "users", on_delete: :cascade
  add_foreign_key "ingredients", "recipes", on_delete: :cascade
  add_foreign_key "meal_plan_entries", "meal_plans", on_delete: :cascade
  add_foreign_key "meal_plan_entries", "recipes", on_delete: :restrict
  add_foreign_key "meal_plan_entries", "users", column: "proposed_by_user_id", on_delete: :nullify
  add_foreign_key "meal_plan_votes", "meal_plan_entries", on_delete: :cascade
  add_foreign_key "meal_plan_votes", "users", on_delete: :cascade
  add_foreign_key "meal_plans", "cookbooks", on_delete: :cascade
  add_foreign_key "meal_plans", "meal_plan_entries", column: "selected_entry_id", on_delete: :nullify
  add_foreign_key "meal_plans", "users", column: "selected_by_user_id", on_delete: :nullify
  add_foreign_key "onboarding_responses", "users", on_delete: :cascade
  add_foreign_key "pending_notifications", "cookbooks", on_delete: :cascade
  add_foreign_key "pending_notifications", "users", column: "actor_id", on_delete: :cascade
  add_foreign_key "pending_notifications", "users", column: "recipient_id", on_delete: :cascade
  add_foreign_key "recipe_tags", "recipes", on_delete: :cascade
  add_foreign_key "recipe_tags", "tags"
  add_foreign_key "recipes", "cookbooks", on_delete: :cascade
  add_foreign_key "recipes", "users", on_delete: :nullify
  add_foreign_key "sessions", "users"
  add_foreign_key "shopping_list_items", "cookbooks", on_delete: :cascade
  add_foreign_key "shopping_list_items", "recipes", column: "source_recipe_id", on_delete: :nullify
  add_foreign_key "shopping_list_items", "users", on_delete: :nullify
end
