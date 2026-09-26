module ApiErrorRendering
  private

  def render_api_error(code, status:, **legacy)
    code = ApiErrorContract.validate!("errors", code, legacy.fetch(:error_params, {}))
    render json: legacy.merge(error_code: code), status: status
  end
end
