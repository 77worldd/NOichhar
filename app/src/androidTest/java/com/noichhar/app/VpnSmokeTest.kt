package com.noichhar.app

import android.content.Context
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.noichhar.app.service.NOichharVpnService
import com.noichhar.app.ui.activity.MainActivity
import com.noichhar.app.util.Constants
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VpnSmokeTest {
    @Test fun startsAndStopsTheRealLocalVpn() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = context as NOichharApp
        // Keep the emulator test independent of external shield-domain DNS lookups.
        app.preferenceManager.advancedProtection = false
        app.preferenceManager.autoUpdateBlocklists = false
        val originalMode = app.preferenceManager.protectionMode
        app.preferenceManager.protectionMode = Constants.ProtectionMode.BOTH
        app.scheduleBlocklistUpdates()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val result = instrumentation.uiAutomation.executeShellCommand("appops set ${context.packageName} ACTIVATE_VPN allow")
        ParcelFileDescriptor.AutoCloseInputStream(result).bufferedReader().use { it.readText() }
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                onView(withId(R.id.shield)).perform(click())
                onView(withText(R.string.continue_label)).perform(click())
                if (VpnService.prepare(context) != null) {
                    val device = UiDevice.getInstance(instrumentation)
                    (device.wait(Until.findObject(By.res("android", "button1")), 5_000)
                        ?: device.wait(Until.findObject(By.text("OK")), 2_000))?.click()
                }
                val deadline = SystemClock.elapsedRealtime() + 40_000
                while (!NOichharVpnService.isActive && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(200)
                assertTrue("VPN service did not become active", NOichharVpnService.isActive)

                // Send real port-53 packets through Android's VPN route, without
                // relying on an external resolver or any synthetic UI counters.
                val domain = "arena-smoke.noichhar.invalid"
                runBlocking { assertTrue(app.blocklistManager.addCustomDomain(domain)) }
                try {
                    // Instrumentation runs in the VPN app's own UID, which the
                    // service excludes to keep its upstream sockets off the VPN.
                    // app_process starts this helper as the separate shell UID.
                    val apk = instrumentation.context.packageCodePath
                    val command = "CLASSPATH='$apk' app_process /system/bin " +
                        "com.noichhar.app.DnsProbe ${Constants.VPN_DNS_ADDRESS} $domain 2>&1"
                    val fd = instrumentation.uiAutomation.executeShellCommand(command)
                    val executor = Executors.newSingleThreadExecutor()
                    val output = try {
                        executor.submit<String> {
                            ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readText() }
                        }.get(25, TimeUnit.SECONDS)
                    } finally {
                        runCatching { fd.close() }
                        executor.shutdownNow()
                    }
                    Log.e("NOICHAR_TEST_FAILURE", "DNS probe output: ${output.take(3000)}")
                    assertTrue("UDP probe failed: $output", output.contains("UDP_BLOCKED_OK"))
                    assertTrue("TCP probe failed: $output", output.contains("TCP_BLOCKED_OK"))
                } finally {
                    runBlocking { app.blocklistManager.removeDomain(domain) }
                }

                onView(withId(R.id.shield)).perform(click())
                val stopDeadline = SystemClock.elapsedRealtime() + 10_000
                while (NOichharVpnService.isActive && SystemClock.elapsedRealtime() < stopDeadline) SystemClock.sleep(100)
                assertFalse("VPN service did not stop", NOichharVpnService.isActive)
            }
        } catch (failure: Throwable) {
            Log.e("NOICHAR_TEST_FAILURE", "VPN smoke test failed", failure)
            throw failure
        } finally {
            NOichharVpnService.instance?.stopVpn()
            app.preferenceManager.protectionMode = originalMode
        }
    }
}
