package com.attunetv.launcher

import org.junit.Assert.*
import org.junit.Test

class SearchTest {
    @Test fun appNamesMatchCaseAccentsAndWords() {
        assertTrue(AppSearch.matches("Prime Video", "  VIDEO prime  "))
        assertTrue(AppSearch.matches("Télévision", "tele"))
        assertTrue(AppSearch.matches("AirPlay", ""))
        assertFalse(AppSearch.matches("Netflix", "Prime"))
        assertFalse(AppSearch.matches("Prime Video", "prime music"))
    }

    @Test fun searchAndSettingsDoNotTriggerHomeRedirection() {
        val amazon = "com.amazon.tv.launcher"
        assertTrue(HomeRedirectPolicy.isHome(amazon, "$amazon.ui.HomeActivity_vNext"))
        assertTrue(HomeRedirectPolicy.isHome(amazon, "$amazon.ui.HomeActivity"))
        listOf("util.MenuNavigationHelperActivity", "deeplink.activity.TextSearchDeepLinkActivityDI",
            "ui.SearchResultsActivity", "ui.MainSettingsActivity", "ui.ProfilePickerActivity").forEach {
            assertFalse(HomeRedirectPolicy.isHome(amazon, "$amazon.$it"))
        }
        assertFalse(HomeRedirectPolicy.isHome(amazon, "android.widget.FrameLayout"))
        assertFalse(HomeRedirectPolicy.isHome("com.amazon.firebat", "$amazon.ui.HomeActivity_vNext"))
    }
}
