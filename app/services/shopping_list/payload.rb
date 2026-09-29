module ShoppingList
  class Payload
    def self.normalize(params)
      if params[:items].present?
        params.require(:items).map do |item|
          item.permit(:client_id, :name, :details, :checked_at, :source_recipe_id, :category, :canonical_name).to_h.symbolize_keys
        end
      elsif params[:item].present?
        [ params.require(:item).permit(:client_id, :name, :details, :checked_at, :source_recipe_id, :category, :canonical_name).to_h.symbolize_keys ]
      else
        []
      end
    end
  end
end
