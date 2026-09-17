package com.getmaincourse.app

import java.io.File
import java.util.Properties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseConfigurationTest {
    @Test
    fun generatedBuildConfigUsesCommittedVersionAndExpectedEnvironment() {
        val properties = Properties().apply {
            versionPropertiesFile().inputStream().use(::load)
        }
        val versionName = checkNotNull(properties.getProperty("versionName"))
        val versionCode = checkNotNull(properties.getProperty("versionCode")).toInt()

        assertTrue(versionName.matches(Regex("\\d+\\.\\d+\\.\\d+")))
        assertTrue(versionCode > 0)
        assertEquals(versionCode, BuildConfig.VERSION_CODE)

        if (BuildConfig.DEBUG) {
            assertEquals("$versionName-dev", BuildConfig.VERSION_NAME)
            assertEquals("com.getmaincourse.app.debug", BuildConfig.APPLICATION_ID)
            assertEquals("http://10.0.2.2:3000/", BuildConfig.API_BASE_URL)
        } else {
            assertEquals(versionName, BuildConfig.VERSION_NAME)
            assertEquals("com.getmaincourse.app", BuildConfig.APPLICATION_ID)
            assertEquals("https://app.getmaincourse.com/", BuildConfig.API_BASE_URL)
        }
    }

    private fun versionPropertiesFile(): File {
        val workingDirectory = File(checkNotNull(System.getProperty("user.dir"))).canonicalFile
        val androidRoot = generateSequence(workingDirectory, File::getParentFile)
            .flatMap { directory -> sequenceOf(directory, File(directory, "maincourse-android")) }
            .firstOrNull { File(it, "app/build.gradle.kts").isFile }
            ?: error("Could not find the Android project from $workingDirectory")
        return File(androidRoot, "version.properties")
    }
}
