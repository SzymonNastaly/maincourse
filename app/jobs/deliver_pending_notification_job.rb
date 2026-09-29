class DeliverPendingNotificationJob < ApplicationJob
  queue_as :default

  def perform(pending_notification_id)
    snapshot = PendingNotification.transaction do
      pending = PendingNotification.lock.find_by(id: pending_notification_id)
      next nil if pending.nil?

      data = {
        payload: pending.payload || [],
        cookbook: pending.cookbook,
        actor: pending.actor,
        recipient: pending.recipient,
        category: pending.category
      }
      pending.destroy!
      data
    end

    return if snapshot.nil? || snapshot[:payload].empty?

    custom = { cookbook_id: snapshot[:cookbook].id, category: snapshot[:category] }

    Push::Fanout.call(device_tokens: snapshot[:recipient].device_tokens.active, custom: custom) do
      build_alert(category: snapshot[:category], cookbook: snapshot[:cookbook], actor: snapshot[:actor], events: snapshot[:payload])
    end
  end

  private

  def build_alert(category:, cookbook:, actor:, events:)
    title = build_title(actor, cookbook)
    body = build_body(category, events)
    { title: title, body: body }
  end

  def build_title(actor, cookbook)
    name = actor.name.to_s.strip
    if name.empty?
      I18n.t("push.shared_cookbook.title", cookbook: cookbook.display_name)
    else
      I18n.t("push.shared_cookbook.title_with_actor", actor: name, cookbook: cookbook.display_name)
    end
  end

  def build_body(category, events)
    case category
    when "shopping_list"
      I18n.t("push.shared_cookbook.shopping_list_added", count: events.size)
    when "meal_plan_activity"
      adds = events.count { |e| e["kind"] == "entry_added" }
      votes = events.count { |e| e["kind"] == "vote" }
      if adds.positive? && votes.positive?
        I18n.t("push.shared_cookbook.meal_plan_added_and_voted", count: adds, votes: votes)
      elsif adds.positive?
        I18n.t("push.shared_cookbook.meal_plan_added", count: adds)
      elsif votes.positive?
        I18n.t("push.shared_cookbook.meal_plan_voted", count: votes)
      else
        I18n.t("push.shared_cookbook.meal_plan_updated")
      end
    else
      I18n.t("push.shared_cookbook.activity")
    end
  end
end
