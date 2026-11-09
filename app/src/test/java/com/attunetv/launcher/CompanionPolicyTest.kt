package com.attunetv.launcher

import org.junit.Assert.*
import org.junit.Test

class CompanionPolicyTest {
    @Test fun onlyExactStreamingPackagesCanShow() {
        assertTrue(CompanionPolicy.canShow("com.amazon.firebat"))
        assertTrue(CompanionPolicy.canShow("com.netflix.ninja"))
        assertTrue(CompanionPolicy.canShow("com.apple.atve.amazon.appletv"))
        assertFalse(CompanionPolicy.canShow("com.amazon.tv.launcher"))
        assertFalse(CompanionPolicy.canShow("com.amazon.firebat.fake"))
    }
    @Test fun switchingAppsClosesPanelButItsOwnWindowDoesNot() {
        assertTrue(CompanionPolicy.shouldHide("com.netflix.ninja", "com.amazon.tv.launcher"))
        assertTrue(CompanionPolicy.shouldHide("com.netflix.ninja", "com.amazon.firebat"))
        assertFalse(CompanionPolicy.shouldHide("com.netflix.ninja", "com.netflix.ninja"))
        assertFalse(CompanionPolicy.shouldHide("com.netflix.ninja", "com.attunetv.launcher"))
        assertFalse(CompanionPolicy.shouldHide(null, "com.netflix.ninja"))
    }
}
