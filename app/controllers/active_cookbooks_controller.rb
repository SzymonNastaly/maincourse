class ActiveCookbooksController < ApplicationController
  # PATCH /active_cookbook
  def update
    cookbook = available_cookbooks.find { |candidate| candidate.id == params[:cookbook_id].to_i }

    if cookbook.nil?
      return redirect_to cookbooks_path, alert: "That cookbook is not available."
    end

    switch_cookbook(cookbook)
    redirect_to destination_after_switch, notice: "Now showing #{cookbook.name}."
  end

  private

  # Records are scoped per cookbook, so returning to the exact previous page
  # would often 404. Go back to the equivalent list instead, like the iOS app.
  def destination_after_switch
    path = URI.parse(request.referer.to_s).path.to_s

    if path.start_with?("/shopping_list")
      shopping_list_items_path
    elsif path.start_with?("/cookbooks")
      cookbooks_path
    else
      recipes_path
    end
  rescue URI::InvalidURIError
    recipes_path
  end
end
