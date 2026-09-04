module RuboCop
  module Cop
    module Maincourse
      # Flags migrations that rebuild a table without `disable_ddl_transaction!`.
      #
      # SQLite cannot alter a column in place, so Rails implements most schema
      # changes by rebuilding the table: copy it, `DROP TABLE` the original,
      # rename the copy back. Rails guards that rebuild with
      # `PRAGMA foreign_keys = OFF`, but SQLite ignores that pragma inside a
      # transaction and `db:migrate` wraps every migration in one. Foreign keys
      # therefore stay armed, and because `DROP TABLE` performs an implicit
      # `DELETE FROM` first, it fires every `ON DELETE CASCADE` / `SET NULL`
      # pointing at the table -- emptying child tables and nulling columns,
      # while the rebuilt table itself comes through looking untouched.
      #
      # `disable_ddl_transaction!` removes the outer transaction so the pragma
      # takes effect. This is how the 2026-09-04 incident happened: a
      # `change_column_null` on `users` emptied `cookbook_memberships` and
      # nulled `recipes.user_id`.
      #
      # Only `change_table` is inspected. `create_table` builds a new table and
      # never rebuilds, so the same method names are ignored inside it.
      #
      # @example
      #   # bad
      #   class MakeDigestOptional < ActiveRecord::Migration[8.1]
      #     def change
      #       change_column_null :users, :password_digest, true
      #     end
      #   end
      #
      #   # good
      #   class MakeDigestOptional < ActiveRecord::Migration[8.1]
      #     disable_ddl_transaction!
      #
      #     def change
      #       change_column_null :users, :password_digest, true
      #     end
      #   end
      class MigrationTableRebuild < Base
        MSG = "`%<name>s` rebuilds the table on SQLite, and the DROP that " \
              "entails fires every ON DELETE CASCADE / SET NULL pointing at " \
              "it. Add `disable_ddl_transaction!` to this migration."

        # Schema statements the SQLite3 adapter implements via `alter_table`.
        # See activerecord's sqlite3_adapter.rb and sqlite3/schema_statements.rb.
        REBUILDING_METHODS = %i[
          add_check_constraint
          add_foreign_key
          add_timestamps
          change_column
          change_column_default
          change_column_null
          remove_belongs_to
          remove_check_constraint
          remove_column
          remove_columns
          remove_foreign_key
          remove_reference
          remove_timestamps
          rename_column
        ].freeze

        # Their `change_table` block equivalents.
        REBUILDING_BLOCK_METHODS = %i[
          change
          change_default
          change_null
          check_constraint
          foreign_key
          remove
          remove_belongs_to
          remove_check_constraint
          remove_foreign_key
          remove_references
          rename
          timestamps
        ].freeze

        # These rebuild only for column shapes SQLite cannot append in place --
        # see `invalid_alter_table_type?` in the adapter -- or because they add
        # a foreign key, which rebuilds in its own right.
        REFERENCE_METHODS = %i[add_reference add_belongs_to references belongs_to].freeze
        COLUMN_METHODS = %i[add_column column].freeze

        def on_new_investigation
          super
          @ddl_transaction_disabled = nil
        end

        def on_send(node)
          return if ddl_transaction_disabled?
          return unless rebuilds_table?(node)

          add_offense(node.loc.selector, message: format(MSG, name: node.method_name))
        end

        private
          def ddl_transaction_disabled?
            return @ddl_transaction_disabled unless @ddl_transaction_disabled.nil?

            ast = processed_source.ast
            @ddl_transaction_disabled = !ast.nil? && ast.each_node(:send).any? do |send_node|
              send_node.receiver.nil? && send_node.method?(:disable_ddl_transaction!)
            end
          end

          def rebuilds_table?(node)
            if node.receiver.nil?
              REBUILDING_METHODS.include?(node.method_name) || rebuilding_column?(node)
            elsif change_table_block_argument?(node)
              REBUILDING_BLOCK_METHODS.include?(node.method_name) || rebuilding_column?(node)
            else
              false
            end
          end

          # True when the receiver is the block argument of an enclosing
          # `change_table` block, which is the only block form that rebuilds.
          def change_table_block_argument?(node)
            receiver = node.receiver
            return false unless receiver.lvar_type?

            node.each_ancestor(:block).any? do |block|
              block.send_node.method?(:change_table) &&
                block.arguments.first&.name == receiver.children.first
            end
          end

          def rebuilding_column?(node)
            reference = REFERENCE_METHODS.include?(node.method_name)
            return false unless reference || COLUMN_METHODS.include?(node.method_name)

            return true if reference && truthy_option?(node, :foreign_key)
            return true if truthy_option?(node, :primary_key)

            # A NOT NULL column with no default cannot be appended in place.
            option(node, :null)&.false_type? && option(node, :default).nil?
          end

          def option(node, key)
            options = node.arguments.last
            return unless options.respond_to?(:hash_type?) && options.hash_type?

            pair = options.pairs.find { |candidate| candidate.key.sym_type? && candidate.key.value == key }
            pair&.value
          end

          def truthy_option?(node, key)
            value = option(node, key)

            !value.nil? && !value.false_type? && !value.nil_type?
          end
      end
    end
  end
end
