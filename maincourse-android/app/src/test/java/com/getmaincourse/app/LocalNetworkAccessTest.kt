package com.getmaincourse.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetworkAccessTest {
    @Test
    fun debugBuildOnAndroid17RequestsMissingLocalNetworkPermission() {
        assertTrue(shouldRequestLocalNetworkAccess(isDebugBuild = true, sdkInt = 37, permissionGranted = false))
    }

    @Test
    fun grantedPermissionIsNotRequestedAgain() {
        assertFalse(shouldRequestLocalNetworkAccess(isDebugBuild = true, sdkInt = 37, permissionGranted = true))
    }

    @Test
    fun releaseAndEarlierAndroidVersionsDoNotRequestDebugLocalNetworkAccess() {
        assertFalse(shouldRequestLocalNetworkAccess(isDebugBuild = false, sdkInt = 37, permissionGranted = false))
        assertFalse(shouldRequestLocalNetworkAccess(isDebugBuild = true, sdkInt = 36, permissionGranted = false))
    }
}
