require "yaml"

module ApiErrorContract
  PATH = File.expand_path("../config/api_errors.yml", __dir__)
  DEFINITIONS = YAML.safe_load_file(PATH).freeze

  def self.validate!(group, code, params = {})
    definition = DEFINITIONS.fetch(group).fetch(code.to_s) { raise ArgumentError, "Unknown API #{group} code: #{code}" }
    params.each do |key, value|
      type = definition.fetch("params").fetch(key.to_s) { raise ArgumentError, "Unknown parameter #{code}.#{key}" }
      raise ArgumentError, "Invalid parameter #{code}.#{key}" unless type == "integer" && value.is_a?(Integer)
    end
    code.to_s
  end

  def self.validation_details(errors)
    errors.map do |error|
      field = validate!("fields", error.attribute)
      rule = error.type.is_a?(Symbol) ? error.type : :invalid
      params = error.options.slice(:count)
      { field: field, code: validate!("rules", rule, params), params: params }
    end
  end
end
