# One row per lifecycle notification sent. Read by the frequency cap, and by hand to
# see whether a campaign led to anything.
class NotificationDelivery < ApplicationRecord
  belongs_to :user
  belongs_to :recipe, optional: true
  belongs_to :cookbook, optional: true

  scope :sent_since, ->(time) { where(sent_at: time..) }
end
