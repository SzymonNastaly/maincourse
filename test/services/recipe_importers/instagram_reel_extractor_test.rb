require "test_helper"

module RecipeImporters
  class InstagramReelExtractorTest < ActiveSupport::TestCase
    test "supports reel, reels and post URLs" do
      assert InstagramReelExtractor.supports_url?("https://www.instagram.com/reel/DRUf_pBiPdh/")
      assert InstagramReelExtractor.supports_url?("https://www.instagram.com/reels/DRUf_pBiPdh/")
      assert InstagramReelExtractor.supports_url?("https://instagram.com/p/DRUf_pBiPdh/?igsh=abc")
    end

    test "rejects profiles and other hosts" do
      assert_not InstagramReelExtractor.supports_url?("https://www.instagram.com/fabiwndrlnd/")
      assert_not InstagramReelExtractor.supports_url?("https://example.com/reel/DRUf_pBiPdh/")
      assert_not InstagramReelExtractor.supports_url?("https://notinstagram.com/p/DRUf_pBiPdh/")
    end
  end
end
