module Api
  module V1
    class OnboardingResponsesController < BaseController
      skip_before_action :authenticate_with_token!, only: :create
      skip_before_action :set_current_cookbook!, only: :create

      unless Rails.env.local?
        rate_limit to: 10, within: 5.minutes, only: :create, with: -> {
          render_api_error "rate_limited", error: "Too many onboarding submissions. Try again later.", status: :too_many_requests
        }
      end

      ALLOWED_SAVE_TODAY = %w[screenshots browser_bookmarks notes recipe_apps cookbooks dont_save].freeze
      ALLOWED_DIET = %w[vegetarian vegan glutenFree pescatarian halal kosher lactoseFree].freeze

      def create
        device_id = params[:device_id].to_s.strip
        if device_id.blank?
          return render_api_error "invalid_request", error: "device_id is required", status: :unprocessable_entity
        end

        raw = params[:answers]
        unless raw.is_a?(ActionController::Parameters) || raw.is_a?(Hash)
          return render_api_error "invalid_request", error: "answers must be an object", status: :unprocessable_entity
        end

        sanitized, error = sanitize_answers(raw.to_unsafe_h)
        if error
          return render_api_error "invalid_request", error: error, status: :unprocessable_entity
        end

        record = OnboardingResponse.record!(device_id: device_id, answers: sanitized)
        render json: { id: record.id, device_id: record.device_id, answers: record.answers }, status: :created
      end

      private

      def sanitize_answers(hash)
        sanitized = {}

        if hash.key?("household_size")
          value = hash["household_size"]
          unless value.is_a?(Integer) && value.between?(1, 50)
            return [ nil, "household_size must be an integer between 1 and 50" ]
          end
          sanitized["household_size"] = value
        end

        if hash.key?("save_today")
          value = hash["save_today"]
          unless value.is_a?(Array) && value.all? { |v| ALLOWED_SAVE_TODAY.include?(v) }
            return [ nil, "save_today contains invalid values" ]
          end
          sanitized["save_today"] = value.uniq
        end

        if hash.key?("diet")
          value = hash["diet"]
          unless value.is_a?(Array) && value.all? { |v| ALLOWED_DIET.include?(v) }
            return [ nil, "diet contains invalid values" ]
          end
          sanitized["diet"] = value.uniq
        end

        [ sanitized, nil ]
      end
    end
  end
end
