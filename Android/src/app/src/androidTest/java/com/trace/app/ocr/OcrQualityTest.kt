package com.trace.app.ocr

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.trace.app.test.R
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import android.util.Log

@RunWith(AndroidJUnit4::class)
class OcrQualityTest {

    @Test
    fun runOcrQualityEvaluation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testContext = InstrumentationRegistry.getInstrumentation().context

        val runner = OcrTestRunner()
        
        val cases = listOf(
            OcrTestCase(
                label = "Printed Syllabus",
                bitmap = BitmapFactory.decodeResource(testContext.resources, R.drawable.test_printed),
                expectedText = listOf("Course Syllabus", "Week 1", "Introduction", "Assignment 1", "Friday")
            ),
            OcrTestCase(
                label = "Handwritten Notes",
                bitmap = BitmapFactory.decodeResource(testContext.resources, R.drawable.test_handwritten),
                expectedText = listOf("Chemistry", "Homework", "Water", "H2O", "Carbon dioxide", "CO2")
            )
        )

        val report = runner.run(cases)
        
        Log.i("OcrQualityTest", "\n" + report.summary())
    }
}
