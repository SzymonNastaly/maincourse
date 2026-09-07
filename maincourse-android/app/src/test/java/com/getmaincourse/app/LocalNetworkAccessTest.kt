package com.getmaincourse.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetworkAccessTest {
    @Test
    fun debugBuildOnAndroid17RequestsMissingLocalNetworkPermission() {
        assertTrue(
            shouldRequestLocalNetworkAccess(
                isDebugBuild = true,
                sdkInt = 37,
                permissionGranted = false,
                apiHost = "10.0.2.2",
            ),
        )
    }

    @Test
    fun grantedPermissionIsNotRequestedAgain() {
        assertFalse(
            shouldRequestLocalNetworkAccess(
                isDebugBuild = true,
                sdkInt = 37,
                permissionGranted = true,
                apiHost = "10.0.2.2",
            ),
        )
    }

    @Test
    fun releaseAndEarlierAndroidVersionsDoNotRequestDebugLocalNetworkAccess() {
        assertFalse(
            shouldRequestLocalNetworkAccess(
                isDebugBuild = false,
                sdkInt = 37,
                permissionGranted = false,
                apiHost = "10.0.2.2",
            ),
        )
        assertFalse(
            shouldRequestLocalNetworkAccess(
                isDebugBuild = true,
                sdkInt = 36,
                permissionGranted = false,
                apiHost = "10.0.2.2",
            ),
        )
    }

    @Test
    fun publicHttpsDebugOverrideDoesNotRequestLocalNetworkAccess() {
        assertFalse(
            shouldRequestLocalNetworkAccess(
                isDebugBuild = true,
                sdkInt = 37,
                permissionGranted = false,
                apiHost = "staging.example.test",
            ),
        )
    }
}
