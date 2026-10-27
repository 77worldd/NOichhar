package com.noichhar.app

import android.content.Context
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.os.SystemClock
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
import com.noichhar.app.dns.DnsPacketParser
import com.noichhar.app.service.NOichharVpnService
import com.noichhar.app.ui.activity.MainActivity
import com.noichhar.app.util.Constants
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import org.junit.Assert.assertEquals
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
                    val query = dnsQuery(domain)
                    val address = InetAddress.getByName(Constants.VPN_DNS_ADDRESS)
                    DatagramSocket().use { socket ->
                        socket.soTimeout = 8_000
                        socket.send(DatagramPacket(query, query.size, address, Constants.DNS_PORT))
                        val packet = DatagramPacket(ByteArray(512), 512)
                        socket.receive(packet)
                        assertBlocked(packet.data.copyOf(packet.length))
                    }
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(address, Constants.DNS_PORT), 8_000)
                        socket.soTimeout = 8_000
                        val out = DataOutputStream(socket.getOutputStream())
                        out.writeShort(query.size); out.write(query); out.flush()
                        val input = DataInputStream(socket.getInputStream())
                        val response = ByteArray(input.readUnsignedShort())
                        input.readFully(response)
                        assertBlocked(response)
                    }
                } finally {
                    runBlocking { app.blocklistManager.removeDomain(domain) }
                }

                onView(withId(R.id.shield)).perform(click())
                val stopDeadline = SystemClock.elapsedRealtime() + 10_000
                while (NOichharVpnService.isActive && SystemClock.elapsedRealtime() < stopDeadline) SystemClock.sleep(100)
                assertFalse("VPN service did not stop", NOichharVpnService.isActive)
            }
        } finally {
            NOichharVpnService.instance?.stopVpn()
            app.preferenceManager.protectionMode = originalMode
        }
    }

    private fun dnsQuery(domain: String): ByteArray {
        val bytes = ByteArrayOutputStream()
        val out = DataOutputStream(bytes)
        out.writeShort(0xCAFE); out.writeShort(0x0100); out.writeShort(1)
        out.writeShort(0); out.writeShort(0); out.writeShort(0)
        domain.split('.').forEach { label ->
            val encoded = label.toByteArray(Charsets.US_ASCII)
            out.writeByte(encoded.size); out.write(encoded)
        }
        out.writeByte(0); out.writeShort(1); out.writeShort(1)
        return bytes.toByteArray()
    }

    private fun assertBlocked(response: ByteArray) {
        val header = requireNotNull(DnsPacketParser.parseHeader(response))
        assertEquals(0xCAFE, header.id)
        assertEquals(0x8000, header.flags and 0x8000) // response, not query
        assertEquals(3, header.flags and 0x000F) // NXDOMAIN
    }
}
