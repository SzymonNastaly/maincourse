package com.getmaincourse.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class SimpleArchitectureTest {
    @Test
    fun removedCoordinationTypesAreAbsent() {
        val source = productionKotlinFiles().joinToString("\n") { it.readText() }

        FORBIDDEN_SYMBOLS.forEach { symbol ->
            assertFalse("Found removed symbol: $symbol", source.contains(symbol))
        }
    }

    private fun productionKotlinFiles(): Sequence<File> {
        val workingDirectory = File(checkNotNull(System.getProperty("user.dir")))
        val sourceRoot = sequenceOf(
            File(workingDirectory, "src/main/java"),
            File(workingDirectory, "app/src/main/java"),
        ).firstOrNull(File::isDirectory)

        checkNotNull(sourceRoot) { "Could not find the production Kotlin source directory from $workingDirectory" }
        return sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }
    }

    private companion object {
        val FORBIDDEN_SYMBOLS = listOf(
            "SessionController",
            "RecipeActionController",
            "RecipeHydrator",
            "RecipeSearchCoordinator",
            "OnboardingController",
            "GoogleCredentialProvider",
            "ApplePkce",
            "RecipeImageOwner",
            "AtomicLong",
            "authenticatedJobs",
            "cookbookGeneration",
            "detailGeneration",
        )
    }
}
