package com.alcedo.studio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
@LargeTest
class MainActivityInstrumentedTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun appLaunchesSuccessfully() {
        // If we get here without exception, the app launched
        activityRule.scenario.onActivity { activity ->
            assertNotNull("Activity should not be null", activity)
        }
    }

    @Test
    fun activityIsNotFinishing() {
        activityRule.scenario.onActivity { activity ->
            assertFalse("Activity should not be finishing", activity.isFinishing)
        }
    }

    @Test
    fun activityIsNotDestroyed() {
        activityRule.scenario.onActivity { activity ->
            assertFalse("Activity should not be destroyed", activity.isDestroyed)
        }
    }
}
