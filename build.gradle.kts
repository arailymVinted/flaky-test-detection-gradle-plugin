import kotlin.math.ln

plugins {
    kotlin("jvm") version "1.9.22"
    id("io.gitlab.arturbosch.detekt") version("1.23.8")
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    reports {
        html.required.set(true)
        xml.required.set(false)
        txt.required.set(false)
    }
}

group = "lt.vilniustech.aissayeva"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

tasks.test {
    useJUnitPlatform()
    // Always run tests, even when no inputs have changed
    outputs.upToDateWhen { false }
    ignoreFailures = false  // This will make the build fail when tests fail
    failFast = false
    reports {
        html.required.set(true)
        junitXml.required.set(true)
    }

    // Show test results in the console
    testLogging {
        events("passed", "skipped", "failed")
        // To see full exception details
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showExceptions = true
        showStackTraces = true
    }

    // Fail the build if tests fail
    ignoreFailures = false

}
kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.8.1")

}


/*Initially I moved functions and data class into separate files, but for some reason I couldn't call them in this gradle file*/
data class TestMetrics(
    val testName: String,
    val totalRuns: Int,
    val passes: Int,
    val entropy: Double,
    val flipRate: Double
)

fun calculateEntropy(passes: Int, totalRuns: Int): Double {
    if (passes == 0 || passes == totalRuns) return 0.0

    val pPass = passes.toDouble() / totalRuns
    val pFail = 1 - pPass

    // Using natural log directly
    return -(pPass * ln(pPass) / ln(2.0) +
            pFail * ln(pFail) / ln(2.0))
}

fun calculateFlipRate(results: List<Boolean>): Double {
    if (results.size <= 1) return 0.0

    var flips = 0
    for (i in 0 until results.size - 1) {
        if (results[i] != results[i + 1]) {
            flips++
        }
    }

    return flips.toDouble() / (results.size - 1)
}

tasks.test {
    useJUnitPlatform()
    ignoreFailures = true
}

// which is a custome task to run unit test multiple times, in this case we specify initially group it should be placed and description of a task
//afterwards all the iterated run results are stored in a build/flaky-test-results file. In provided code fragment unit tests are iterated 101 times to have 90% of confidence of resulted flakiness score.
// in every iteration tests located in SampleFlakyTests are executed, also if there is a need to run all the tests existing in a project filter maybe ommited and code in line X could be changed into code fragment 10.
// after running unit tests successfully test are copied into  build/flaky-test-results/run-$iteration iteration number and in the next run iteration test results will be deleted
tasks.register("runTestsMultipleTimes") {
    group = "Verification"
    description = "Runs tests multiple times and saves results"

    doLast {
        project.mkdir("build/flaky-test-results")

        repeat(101) { iteration ->
            println("\nRunning test iteration ${iteration + 1}")

            delete("build/test-results/test")
            tasks.test.get().apply {
                filter {
                    includeTestsMatching("SampleFlakyTests")
                }
                executeTests()
            }
            //tasks.test.get().executeTests()

            copy {
                from("build/test-results/test")
                into("build/flaky-test-results/run-$iteration")
            }

            println("Completed test run ${iteration + 1}")
        }
    }
}

tasks.register("analyzeTestFlakiness") {
    group = "Verification"
    description = "Analyzes test results for flakiness and adds @Ignore to flaky tests"

    doLast {
        val resultsDir = project.buildDir.resolve("flaky-test-results")
        if (!resultsDir.exists()) {
            println("No test results found. Run './gradlew runTestsMultipleTimes' first")
            return@doLast
        }

        val testResults = mutableMapOf<String, MutableList<Boolean>>()

        // Read each run's results
        resultsDir.listFiles()?.forEach { runDir ->
            runDir.walk()
                .filter { it.name.endsWith(".xml") }
                .forEach { xmlFile ->
                    val text = xmlFile.readText()
                    val testcases = text.split("<testcase")
                    testcases.drop(1).forEach { testcase ->
                        val name = testcase.substringAfter("name=\"").substringBefore("\"")
                        val className = testcase.substringAfter("classname=\"").substringBefore("\"")
                        val fullName = "$className.$name"
                        val failed = testcase.contains("<failure") || testcase.contains("<error")
                        testResults.getOrPut(fullName) { mutableListOf() }.add(!failed)
                    }
                }
        }

        // Calculate metrics for each test
        val metrics = testResults.map { (testName, results) ->
            val passes = results.count { it }
            TestMetrics(
                testName = testName,
                totalRuns = results.size,
                passes = passes,
                entropy = calculateEntropy(passes, results.size),
                flipRate = calculateFlipRate(results)
            )
        }

        // Map to store tests to ignore
        val testsToIgnore = mutableMapOf<String, MutableList<Pair<String, String>>>()

        // Print results
        println("\nTest Flakiness Analysis")
        println("=====================")
        metrics.forEach { metric ->
            println("\n${metric.testName}:")
            println("  Total Runs: ${metric.totalRuns}")
            println("  Passes: ${metric.passes}/${metric.totalRuns}")
            println("  Entropy: %.3f".format(metric.entropy))
            println("  Flip Rate: %.3f".format(metric.flipRate))


            val entropyStatus = when {
                metric.entropy == 0.0 -> "Not Flaky (NF)"
                metric.entropy in 0.13..0.17 -> "Slightly Flaky (SF)"
                metric.entropy in 0.18..0.48 -> "Flaky"
                metric.entropy >= 0.49 -> "Very Flaky (VF)"
                else -> "Unknown"
            }
            println("  Flakiness Status by Entropy: $entropyStatus")

            val flipRateStatus = when {
                metric.flipRate == 0.0 -> "Not Flaky (NF)"
                metric.flipRate in 0.09..0.10 -> "Slightly Flaky (SF)"
                metric.flipRate in 0.11..0.31 -> "Flaky"
                metric.flipRate >= 0.32 -> "Very Flaky (VF)"
                else -> "Unknown"
            }
            println("  Flakiness Status by Flip Rate: $flipRateStatus")


            // Determine if test should be ignored - any test that's not stable
            val shouldIgnore = (metric.entropy >= 0.13 || metric.flipRate >= 0.09)

            // If test is flaky, add to the ignore list
            if (shouldIgnore) {
                // Extract class name and method name from the test name
                val parts = metric.testName.split(".")
                if (parts.size >= 2) {
                    val className = parts.dropLast(1).joinToString(".")
                    val methodName = parts.last()
                    val reason = "Flaky test: Entropy=%.3f, Status: $entropyStatus, FlipRate=%.3f, Status: $flipRateStatus".format(metric.entropy, metric.flipRate)

                    testsToIgnore.getOrPut(className) { mutableListOf() }.add(methodName to reason)
                    println("  → WILL BE IGNORED: $methodName")
                }
            }
        }

        // Process each class that has flaky tests
        if (testsToIgnore.isNotEmpty()) {
            println("\nAdding @Disabled annotations to flaky tests...")

            // Find source files and add @Ignore annotations
            var totalAnnotated = 0
            testsToIgnore.forEach { (className, methodPairs) ->
                // Look for the test file
                val shortClassName = className.substringAfterLast(".")
                val testFile = project.projectDir.walk()
                    .filter {
                        it.isFile &&
                                (it.name.endsWith(".kt") || it.name.endsWith(".java")) &&
                                it.name.contains(shortClassName)
                    }
                    .firstOrNull()

                if (testFile != null) {
                    println("Found test file: ${testFile.absolutePath}")
                    val content = testFile.readText()
                    val lines = content.lines().toMutableList()

                    // Add import for @Ignore if not present
                    if (!content.contains("import org.junit.jupiter.api.Disabled")) {
                        var importInsertIndex = 0

                        // Find where to insert the import
                        for (i in lines.indices) {
                            if (lines[i].trimStart().startsWith("import ")) {
                                importInsertIndex = i + 1
                            } else if (lines[i].trimStart().startsWith("class ")) {
                                // We've hit the class declaration, so insert before this
                                break
                            }
                        }

                        // Insert the import
                        lines.add(importInsertIndex, "import org.junit.jupiter.api.Disabled")
                        println("Added import org.junit.jupiter.api.Disabled")
                    }

                    // Track how many lines we've added so far to adjust indices
                    var linesAdded = if (!content.contains("import org.junit.jupiter.api.Disabled")) 1 else 0

                    // For each method to ignore
                    methodPairs.forEach { (methodName, reason) ->
                        var methodFound = false
                        var lineIndex = 0

                        // Look through the file for the method
                        while (lineIndex < lines.size) {
                            val line = lines[lineIndex].trim()

                            // Check if this line contains a method declaration matching our method name
                            if (line.contains("fun $methodName") &&
                                (line.contains("(") || lines.getOrNull(lineIndex + 1)?.contains("(") == true)) {

                                // Look backward for the @Test annotation
                                var testAnnotationIndex = -1
                                for (i in lineIndex - 1 downTo Math.max(0, lineIndex - 5)) {
                                    if (lines[i].trim() == "@Test" || lines[i].trim().startsWith("@Test(")) {
                                        testAnnotationIndex = i
                                        break
                                    }
                                }

                                if (testAnnotationIndex != -1) {
                                    // Check if @Ignore is already present
                                    var hasIgnore = false
                                    for (i in testAnnotationIndex - 3..testAnnotationIndex - 1) {
                                        if (i >= 0 && lines[i].trim().startsWith("@Disabled")) {
                                            hasIgnore = true
                                            break
                                        }
                                    }

                                    if (!hasIgnore) {
                                        // Get the indentation from the @Test line
                                        val indent = lines[testAnnotationIndex].takeWhile { it.isWhitespace() }

                                        // Insert @Ignore annotation before @Test
                                        lines.add(testAnnotationIndex, "${indent}@Disabled(\"$reason\")")
                                        linesAdded++
                                        totalAnnotated++
                                        println("  Added @Disabled to $methodName in ${testFile.name}")
                                        methodFound = true
                                    } else {
                                        println("  $methodName already has @Disabled annotation")
                                    }
                                }

                                break // We found the method, no need to continue searching
                            }
                            lineIndex++
                        }

                        if (!methodFound) {
                            println("  WARNING: Could not find method $methodName in ${testFile.name}")
                        }
                    }

                    // Write back the modified content
                    testFile.writeText(lines.joinToString("\n"))
                } else {
                    println("  WARNING: Could not find source file for class $className")
                }
            }

            println("\nSummary: Added @Disabled annotations to $totalAnnotated flaky tests")
        } else {
            println("\nNo flaky tests to ignore.")
        }
    }
}