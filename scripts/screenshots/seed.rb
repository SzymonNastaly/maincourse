require Rails.root.join("lib/screenshots/seed")

result = Screenshots::Seed.call
path = Rails.root.join("storage/screenshots/seed.json")
path.dirname.mkpath
path.write(JSON.pretty_generate(result) + "\n")
puts "Seeded showcase cookbook; capture selectors written to #{path}"
