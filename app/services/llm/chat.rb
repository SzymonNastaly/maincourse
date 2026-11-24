module Llm
  # The one model every recipe LLM call uses. Haiku has no EU endpoint, so route
  # through the providers that support structured output.
  module Chat
    MODEL = "anthropic/claude-haiku-5.5"
    PROVIDERS = %w[anthropic google-vertex/global azure/global].freeze

    def self.build
      RubyLLM.chat(model: MODEL, provider: :openrouter, assume_model_exists: true)
        .with_provider_options(provider: { only: PROVIDERS })
    end
  end
end
