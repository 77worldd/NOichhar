package com.noichhar.app

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.core.app.ApplicationProvider
import com.noichhar.app.util.Constants
import android.widget.RadioButton
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.noichhar.app.ui.activity.MainActivity
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationSmokeTest {
    @Test fun opensAllFiveTabs() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.shield)).check(matches(isDisplayed()))
            onView(withContentDescription("Stats")).perform(click())
            onView(withId(R.id.time_selector)).check(matches(isDisplayed()))
            onView(withContentDescription("RPN")).perform(click())
            onView(withId(R.id.btn_update_lists)).check(matches(isDisplayed()))
            onView(withContentDescription("Configure")).perform(click())
            onView(withId(R.id.sw_boot)).check(matches(isDisplayed()))
            onView(withContentDescription("About")).perform(click())
            onView(withId(R.id.tv_version)).check(matches(isDisplayed()))
            onView(withContentDescription("Home")).perform(click())
            onView(withId(R.id.shield)).check(matches(isDisplayed()))
        }
    }
    @Test fun modeAndSettingsPersistWhenChangingTabs() {
        val app = ApplicationProvider.getApplicationContext<NOichharApp>()
        val oldMode = app.preferenceManager.protectionMode
        val oldBoot = app.preferenceManager.startOnBoot
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                onView(withContentDescription("RPN")).perform(click())
                scenario.onActivity { it.findViewById<RadioButton>(R.id.rb_dns).performClick() }
                org.junit.Assert.assertEquals(Constants.ProtectionMode.DNS_ONLY, app.preferenceManager.protectionMode)
                onView(withContentDescription("Configure")).perform(click())
                onView(withId(R.id.sw_boot)).perform(click())
                org.junit.Assert.assertEquals(!oldBoot, app.preferenceManager.startOnBoot)
                onView(withContentDescription("RPN")).perform(click())
                onView(withId(R.id.rb_dns)).check(matches(isChecked()))
            }
        } finally {
            app.preferenceManager.protectionMode = oldMode
            app.preferenceManager.startOnBoot = oldBoot
        }
    }

}
