require_relative "../../../api_error_contract"

module RuboCop
  module Cop
    module Maincourse
      class ApiErrorContract < Base
        extend AutoCorrector
        MSG = "Use render_api_error with a code from config/api_errors.yml; do not render error JSON directly."

        def on_pair(node)
          key, value = *node
          return unless key.sym_type? || key.str_type?

          if key.value.to_s == "error_code" && processed_source.file_path.include?("/controllers/")
            add_offense(node, message: MSG) unless key.sym_type? && direct_error_render?(node)
          elsif key.value.to_s == "import_error_code" && (value.str_type? || value.sym_type?)
            check_code(value, "imports")
          elsif key.value.to_s == "json" && value.hash_type?
            keys = value.pairs.map { |pair| pair.key.value if pair.key.sym_type? || pair.key.str_type? }
            if ((keys & %i[error errors]).any? || (keys & %w[error errors]).any?) && !keys.include?(:error_code)
              add_offense(node, message: MSG)
            end
          end
        end

        def on_send(node)
          if node.method?(:render)
            convert_error_render(node)
            return
          end
          if node.method?(:head) && error_status?(node.first_argument)
            add_offense(node, message: MSG)
          end
          return unless node.method?(:render_api_error)
          code = node.first_argument
          if code && (code.str_type? || code.sym_type?)
            check_code(code, "errors")
          else
            add_offense(node, message: "Use a literal contract code so CI can verify every API error.")
          end
        end

        private

        def direct_error_render?(node)
          node.ancestors.any? { |ancestor| ancestor.send_type? && ancestor.method?(:render) }
        end

        def convert_error_render(node)
          options = node.arguments.find(&:hash_type?)
          return unless options
          status = options.pairs.find { |pair| pair.key.sym_type? && pair.key.value == :status }&.value
          json = options.pairs.find { |pair| pair.key.sym_type? && pair.key.value == :json }&.value
          code = json.pairs.find { |pair| pair.key.sym_type? && pair.key.value == :error_code } if json&.hash_type?
          unless code
            add_offense(node, message: MSG) if error_status?(status)
            return
          end
          fields = json.pairs.reject { |pair| pair == code }.map(&:source)
          fields += options.pairs.reject { |pair| pair.key.sym_type? && pair.key.value == :json }.map(&:source)
          add_offense(node, message: MSG) do |corrector|
            corrector.replace(node, "render_api_error #{code.value.source}, #{fields.join(', ')}")
          end
        end

        def error_status?(node)
          return false unless node
          return error_status?(node.if_branch) || error_status?(node.else_branch) if node.if_type?
          return error_status?(node.children.last) if node.begin_type?
          return node.value >= 400 if node.int_type?
          return !%i[ok created accepted no_content reset_content partial_content not_modified moved_permanently found see_other temporary_redirect permanent_redirect].include?(node.value) if node.sym_type?
          true
        end

        def check_code(node, group)
          return if ::ApiErrorContract::DEFINITIONS.fetch(group).key?(node.value.to_s)
          add_offense(node, message: "Unknown #{group} code #{node.value.inspect}; add it to config/api_errors.yml and both clients.")
        end
      end
    end
  end
end
