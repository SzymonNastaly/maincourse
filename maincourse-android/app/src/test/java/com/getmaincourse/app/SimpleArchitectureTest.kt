package com.getmaincourse.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimpleArchitectureTest {
    @Test
    fun removedProductionFilesAreAbsent() {
        productionSourceRoots().forEach { sourceRoot ->
            FORBIDDEN_SOURCE_PATHS.forEach { relativePath ->
                assertFalse(
                    "Found removed production file: ${sourceRoot.resolve(relativePath)}",
                    sourceRoot.resolve(relativePath).exists(),
                )
            }
        }
    }

    @Test
    fun removedCoordinationDeclarationsAreAbsent() {
        productionKotlinFiles().forEach { file ->
            val source = file.readText()
            FORBIDDEN_DECLARATIONS.forEach { (description, declaration) ->
                assertFalse(
                    "Found removed $description declaration in $file",
                    declaration.containsMatchIn(source),
                )
            }
        }
    }

    private fun productionKotlinFiles(): Sequence<File> =
        productionSourceRoots().asSequence().flatMap { sourceRoot ->
            sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }
        }

    private fun productionSourceRoots(): List<File> {
        val sourceSets = File(androidAppDirectory(), "src").listFiles().orEmpty()
            .filter { it.isDirectory && !it.name.contains("test", ignoreCase = true) }
        val roots = sourceSets.flatMap { sourceSet ->
            listOf(File(sourceSet, "java"), File(sourceSet, "kotlin")).filter(File::isDirectory)
        }.distinctBy(File::getCanonicalPath)

        assertTrue("No production Kotlin source roots found", roots.isNotEmpty())
        return roots
    }

    private fun androidAppDirectory(): File {
        val workingDirectory = File(checkNotNull(System.getProperty("user.dir"))).canonicalFile
        return generateSequence(workingDirectory, File::getParentFile)
            .flatMap { directory ->
                sequenceOf(directory, File(directory, "app"), File(directory, "maincourse-android/app"))
            }
            .firstOrNull { File(it, "build.gradle.kts").isFile && File(it, "src/main").isDirectory }
            ?: error("Could not find the Android app module from $workingDirectory")
    }

    private companion object {
        val FORBIDDEN_SOURCE_PATHS = listOf(
            "com/getmaincourse/app/data/cache/CatalogStore.kt",
            "com/getmaincourse/app/data/cache/RoomCatalogStore.kt",
            "com/getmaincourse/app/data/images/PreparedRecipeImage.kt",
            "com/getmaincourse/app/data/images/RecipeImageOwner.kt",
            "com/getmaincourse/app/data/images/RecipeImagePreparer.kt",
            "com/getmaincourse/app/data/model/AppleAuthenticationModels.kt",
            "com/getmaincourse/app/data/model/GoogleSignInRequest.kt",
            "com/getmaincourse/app/data/model/OnboardingModels.kt",
            "com/getmaincourse/app/data/model/RecipeBatchResponse.kt",
            "com/getmaincourse/app/data/network/MainCourseApi.kt",
            "com/getmaincourse/app/data/network/RetrofitMainCourseApi.kt",
            "com/getmaincourse/app/data/network/RetrofitMainCourseService.kt",
            "com/getmaincourse/app/data/onboarding/AtomicOnboardingStore.kt",
            "com/getmaincourse/app/data/onboarding/OnboardingStore.kt",
            "com/getmaincourse/app/features/auth/AndroidGoogleCredentialProvider.kt",
            "com/getmaincourse/app/features/auth/AppleAuthenticationCallback.kt",
            "com/getmaincourse/app/features/auth/ApplePkce.kt",
            "com/getmaincourse/app/features/auth/AppleSignInButton.kt",
            "com/getmaincourse/app/features/auth/GoogleAuthenticationLauncher.kt",
            "com/getmaincourse/app/features/auth/GoogleCredentialProvider.kt",
            "com/getmaincourse/app/features/auth/GoogleCredentialSessionCleaner.kt",
            "com/getmaincourse/app/features/auth/GoogleNonce.kt",
            "com/getmaincourse/app/features/auth/GoogleSignInButton.kt",
            "com/getmaincourse/app/features/auth/GoogleSignInConfiguration.kt",
            "com/getmaincourse/app/features/designsystem/DesignSystemScreen.kt",
            "com/getmaincourse/app/features/onboarding/OnboardingController.kt",
            "com/getmaincourse/app/features/onboarding/OnboardingScreen.kt",
            "com/getmaincourse/app/features/onboarding/OnboardingState.kt",
            "com/getmaincourse/app/features/recipes/RecipeActionController.kt",
            "com/getmaincourse/app/features/recipes/RecipeActionState.kt",
            "com/getmaincourse/app/features/recipes/RecipeEditDraft.kt",
            "com/getmaincourse/app/features/recipes/RecipeImagePreparationState.kt",
            "com/getmaincourse/app/features/recipes/RecipeUiSavedState.kt",
            "com/getmaincourse/app/features/search/RecipeSearchDocument.kt",
            "com/getmaincourse/app/features/search/RecipeSearchEngine.kt",
            "com/getmaincourse/app/features/search/RecipeSearchScreen.kt",
            "com/getmaincourse/app/features/search/RecipeSearchState.kt",
            "com/getmaincourse/app/features/session/CatalogRepository.kt",
            "com/getmaincourse/app/features/session/RecipeHydrator.kt",
            "com/getmaincourse/app/features/session/RecipeReadMutationBarrier.kt",
            "com/getmaincourse/app/features/session/SessionController.kt",
            "com/getmaincourse/app/features/session/SessionState.kt",
            "com/getmaincourse/app/features/settings/AccountState.kt",
        )

        val FORBIDDEN_DECLARATIONS = mapOf(
            "coordination type" to Regex(
                """\b(?:class|interface|object|typealias)\s+(?:SessionController|RecipeActionController|RecipeHydrator|RecipeSearchCoordinator|OnboardingController|GoogleCredentialProvider|ApplePkce|RecipeImageOwner)\b""",
            ),
            "coordination property" to Regex(
                """\b(?:val|var)\s+(?:authenticatedJobs|cookbookGeneration|detailGeneration)\b""",
            ),
        )
    }
}
