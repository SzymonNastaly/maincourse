source "https://rubygems.org"

# Bundle edge Rails instead: gem "rails", github: "rails/rails", branch: "main"
gem "rails", "~> 8.1.3"
# The modern asset pipeline for Rails [https://github.com/rails/propshaft]
gem "propshaft"
# Use sqlite3 as the database for Active Record
gem "sqlite3", ">= 2.1"
# Use the Puma web server [https://github.com/puma/puma]
gem "puma", ">= 5.0"
# Use JavaScript with ESM import maps [https://github.com/rails/importmap-rails]
gem "importmap-rails"
# Hotwire's SPA-like page accelerator [https://turbo.hotwired.dev]
gem "turbo-rails"
# Hotwire's modest JavaScript framework [https://stimulus.hotwired.dev]
gem "stimulus-rails"
# Build JSON APIs with ease [https://github.com/rails/jbuilder]
gem "jbuilder"
# CORS support for API endpoints [https://github.com/cyu/rack-cors]
gem "rack-cors"
# Lucide icon library for Rails [https://github.com/heyvito/lucide-rails]
gem "lucide-rails"

# HTTP client for recipe imports [https://lostisland.github.io/faraday/]
gem "faraday"
gem "faraday-follow_redirects", require: "faraday/follow_redirects"

# LLM client for AI-powered recipe extraction [https://github.com/crmne/ruby_llm]
gem "ruby_llm"

# Use Active Model has_secure_password [https://guides.rubyonrails.org/active_model_basics.html#securepassword]
gem "bcrypt", "~> 3.1.22"

# Windows does not include zoneinfo files, so bundle the tzinfo-data gem
gem "tzinfo-data", platforms: %i[ windows jruby ]

# Use the database-backed adapters for Rails.cache, Active Job, and Action Cable
gem "solid_cache"
gem "solid_queue"
gem "solid_cable"

# Reduces boot times through caching; required in config/boot.rb
gem "bootsnap", require: false

# Deploy this application anywhere as a Docker container [https://kamal-deploy.org]
gem "kamal", require: false

# Add HTTP asset caching/compression and X-Sendfile acceleration to Puma [https://github.com/basecamp/thruster/]
gem "thruster", require: false

# Use Active Storage variants [https://guides.rubyonrails.org/active_storage_overview.html#transforming-images]
gem "image_processing", "~> 1.2"

# S3-compatible storage (Hetzner Object Storage) [https://github.com/aws/aws-sdk-ruby]
gem "aws-sdk-s3", require: false

# Error tracking and performance monitoring [https://docs.sentry.io/platforms/ruby/guides/rails/]
gem "sentry-ruby"
gem "sentry-rails"

# Tame noisy Rails logs into single-line structured output [https://github.com/roidrage/lograge]
gem "lograge"

# APNs HTTP/2 client for iOS push notifications [https://github.com/ostinelli/apnotic]
gem "apnotic"

group :development, :test do
  # See https://guides.rubyonrails.org/debugging_rails_applications.html#debugging-with-the-debug-gem
  gem "debug", platforms: %i[ mri windows ], require: "debug/prelude"

  # Audits gems for known security defects (use config/bundler-audit.yml to ignore issues)
  gem "bundler-audit", require: false

  # Static analysis for security vulnerabilities [https://brakemanscanner.org/]
  gem "brakeman", require: false

  # Omakase Ruby styling [https://github.com/rails/rubocop-rails-omakase/]
  gem "rubocop-rails-omakase", require: false
end

group :development do
  # Use console on exceptions pages [https://github.com/rails/web-console]
  gem "web-console"
end

group :test do
  # System testing [https://guides.rubyonrails.org/testing.html#system-testing]
  gem "capybara"
  # 4.45+ regressed: chromedriver launched without the Selenium Manager browser path
  gem "selenium-webdriver", "< 4.45"

  # HTTP request stubbing for testing [https://github.com/bblimke/webmock]
  gem "webmock"

  # Minitest mocking (extracted from minitest 6.0+)
  gem "minitest-mock"
end

# Admin dashboard [https://avohq.io]
gem "avo", "~> 3.31"
gem "ransack"

gem "tailwindcss-rails", "~> 4.4"
