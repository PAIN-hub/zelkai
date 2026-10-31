package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.DeviceProfiler
import com.example.domain.ModelRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Nexus Agent v5", appName)
    }

    @Test
    fun `measure device profile and load model registry from assets`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val profile = DeviceProfiler.measure(context)
        assertTrue(profile.totalRamBytes > 0L)
        assertTrue(profile.cpuTopology.totalCores >= 1)

        val models = ModelRegistry.loadModels(context)
        assertTrue(models.size >= 15)
    }
}
