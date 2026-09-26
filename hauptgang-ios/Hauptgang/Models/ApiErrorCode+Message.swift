import Foundation

// Keep the wire-code mapping flat and exhaustive so new codes require a decision.

extension ApiErrorCode {
    // swiftlint:disable:next function_body_length cyclomatic_complexity
    func message(count: Int?, bundle: Bundle, locale: Locale) -> String {
        switch self {
        case .name_required: return String(localized: "Name is required", bundle: bundle, locale: locale)
        case .url_required: return String(localized: "Enter a recipe URL.", bundle: bundle, locale: locale)
        case .invalid_import_url:
            return String(
                localized: "This URL cannot be imported. Check the address and try a public recipe page.",
                bundle: bundle,
                locale: locale
            )
        case .text_required: return String(localized: "Paste some recipe text first.", bundle: bundle, locale: locale)
        case .text_too_long:
            if let count, count > 0 {
                return String(
                    format: String(
                        localized: "Recipe text exceeds the character limit (%lld).",
                        bundle: bundle,
                        locale: locale
                    ),
                    locale: locale,
                    count
                )
            }
            return String(
                localized: "Recipe text is too long. Try a shorter selection.",
                bundle: bundle,
                locale: locale
            )
        case .image_required: return String(localized: "Choose a recipe photo first.", bundle: bundle, locale: locale)
        case .invalid_image:
            return String(
                localized: "This file is not a supported image. Choose another photo.",
                bundle: bundle,
                locale: locale
            )
        case .image_too_large:
            return String(localized: "Image is too large. Please try a smaller photo.", bundle: bundle, locale: locale)
        case .content_too_large:
            return String(
                localized: "The recipe content is too large. Try importing the link instead.",
                bundle: bundle,
                locale: locale
            )
        case .cookbook_unavailable:
            return String(
                localized: "That cookbook is no longer available to you. Choose another cookbook.",
                bundle: bundle,
                locale: locale
            )
        case .shared_cookbook_exists:
            return String(localized: "You already have a shared cookbook.", bundle: bundle, locale: locale)
        case .personal_cookbook_required:
            return String(
                localized: "Your personal cookbook cannot be deleted or left.",
                bundle: bundle,
                locale: locale
            )
        case .personal_cookbook_invitation:
            return String(localized: "Create a shared cookbook to invite other people.", bundle: bundle, locale: locale)
        case .owner_required: return String(
                localized: "Only the cookbook owner can do this.",
                bundle: bundle,
                locale: locale
            )
        case .owner_cannot_leave:
            return String(
                localized: "You own this cookbook. Delete it instead of leaving.",
                bundle: bundle,
                locale: locale
            )
        case .already_cookbook_member:
            return String(localized: "You are already a member of this cookbook.", bundle: bundle, locale: locale)
        case .invitation_unavailable:
            return String(
                localized: "This invitation is no longer available. It may have expired.",
                bundle: bundle,
                locale: locale
            )
        case .meal_plan_finalized:
            return String(localized: "This meal plan has already been finalized.", bundle: bundle, locale: locale)
        case .meal_plan_selection_conflict:
            return String(
                localized: "A different recipe has already been selected for this date.",
                bundle: bundle,
                locale: locale
            )
        case .invalid_recipe_save:
            return String(
                localized: "This recipe could not be saved. Please start again from the example.",
                bundle: bundle,
                locale: locale
            )
        case .invalid_shopping_items:
            return String(
                localized: "Some shopping list items could not be saved. Check their names and try again.",
                bundle: bundle,
                locale: locale
            )
        case .delete_failed: return String(
                localized: "Could not delete this item. Please try again.",
                bundle: bundle,
                locale: locale
            )
        case .update_failed: return String(
                localized: "Could not save your changes. Please try again.",
                bundle: bundle,
                locale: locale
            )
        case .app_update_required:
            return String(
                localized: "Update MainCourse to continue, or sign in using your previous method.",
                bundle: bundle,
                locale: locale
            )
        case .rate_limited: return String(
                localized: "Too many requests. Please wait a moment and try again.",
                bundle: bundle,
                locale: locale
            )
        case .import_limit_reached:
            if let count, count > 0 {
                return String(
                    format: String(
                        // swiftlint:disable:next line_length
                        localized: "You've reached your monthly recipe import limit (%lld). Upgrade to Pro for unlimited imports.",
                        bundle: bundle,
                        locale: locale
                    ),
                    locale: locale,
                    count
                )
            }
            return String(
                localized: "You've reached your monthly recipe import limit. Upgrade to Pro for unlimited imports.",
                bundle: bundle,
                locale: locale
            )
        case .invalid_request, .validation_failed:
            return String(
                localized: "Could not complete this request. Check your information and try again.",
                bundle: bundle,
                locale: locale
            )
        case .unauthorized: return String(
                localized: "Your session has expired. Please sign in again.",
                bundle: bundle,
                locale: locale
            )
        case .forbidden: return String(
                localized: "You don't have permission to perform this action",
                bundle: bundle,
                locale: locale
            )
        case .not_found: return String(
                localized: "The requested resource was not found",
                bundle: bundle,
                locale: locale
            )
        case .invalid_credentials: return String(localized: "Invalid email or password", bundle: bundle, locale: locale)
        case .oauth_failed: return String(
                localized: "Could not sign in with that provider. Please try again.",
                bundle: bundle,
                locale: locale
            )
        case .oauth_unavailable:
            return String(
                localized: "That sign-in provider is temporarily unavailable. Please try again later.",
                bundle: bundle,
                locale: locale
            )
        case .account_link_required:
            return String(
                localized: "An account already exists for this email. Sign in with your password instead.",
                bundle: bundle,
                locale: locale
            )
        case .apple_account_creation_confirmation_required:
            return String(
                localized: "Confirm that you want to create a separate MainCourse account with Apple.",
                bundle: bundle,
                locale: locale
            )
        case .recipe_save_conflict:
            return String(
                localized: "This save request no longer matches. Please start again from the example.",
                bundle: bundle,
                locale: locale
            )
        case .recipe_save_gone:
            return String(
                localized: "That saved recipe is no longer available. You can save a new copy from the example.",
                bundle: bundle,
                locale: locale
            )
        }
    }
}
