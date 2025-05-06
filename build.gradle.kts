import kotlin.math.ln

plugins {
    kotlin("jvm") version "1.9.22"
}

group = "lt.vilniustech.aissayeva"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

tasks.test {
    useJUnitPlatform()
    ignoreFailures = false  // This will make the build fail when tests fail
    reports {
        html.required.set(true)
        junitXml.required.set(true)
    }
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
// Modified analyzeTestFlakiness task with built-in @Ignore support
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
                        val failed = testcase.contains("<failure") || testcase.contains("<error")
                        testResults.getOrPut(name) { mutableListOf() }.add(!failed)
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
        val testsToIgnore = mutableMapOf<String, MutableList<String>>()

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
                metric.entropy == 0.0 -> "Stable"
                metric.entropy in 0.13..0.17 -> "Slightly Flaky"
                metric.entropy in 0.18..0.48 -> "Flaky test"
                metric.entropy > 0.48 -> "Very Flaky"
                else -> "Failed to calculate flakiness for this test ${metric.testName}"
            }
            println("Flakiness Status by Entropy: $ entropyStatus ")
            val flipRateStatus = when {
                metric.flipRate == 0.0 -> "Stable"
                metric.flipRate in 0.09..0.1-> "Slightly Flaky"
                metric.flipRate in 0.11..0.31-> "Flaky test"
                metric.flipRate > 0.31-> "Very Flaky"
                else -> "Failed to calculate flakiness for this test ${metric.testName}"
            }
            println("Flakiness Status by FlipRate: $ flipRateStatus ")

            val status = when {
                metric.entropy == 0.0 && metric.flipRate == 0.0 -> "Stable"
                metric.entropy in 0.13..0.17 && metric.flipRate in 0.09..0.1-> "Slightly Flaky"
                metric.entropy in 0.18..0.48 && metric.flipRate in 0.11..0.31-> "Flaky test"
                metric.entropy > 0.48 && metric.flipRate > 0.31-> "Very Flaky"
                else -> "Failed to calculate flakiness for this test ${metric.testName}"
            }
            println("  Flakiness Status by both metrics: $status")

            // Determine if test should be ignored
            val shouldIgnore = metric.entropy > 0.13 || metric.flipRate > 0.09
            // If test is flaky, add to the ignore list
            if (shouldIgnore) {
                // Extract class name and method name from the test name
                val parts = metric.testName.split(".")
                if (parts.size >= 2) {
                    val className = parts.dropLast(1).joinToString(".")
                    val methodName = parts.last()
                    val reason = "Flaky test: Entropy=%.3f, FlipRate=%.3f".format(metric.entropy, metric.flipRate)

                    testsToIgnore.getOrPut(className) { mutableListOf() }.add(methodName)
                    println("  → WILL BE IGNORED: $methodName")
                }
            }
        }

        // Process each class that has flaky tests
        if (testsToIgnore.isNotEmpty()) {
            println("\nAdding @Ignore annotations to flaky tests...")

            // Find source files and add @Ignore annotations
            var totalAnnotated = 0
            testsToIgnore.forEach { (className, methodNames) ->
                // Look for the test file
                val testFile = project.projectDir.walk()
                    .filter {
                        it.isFile &&
                                (it.name.endsWith(".kt") || it.name.endsWith(".java")) &&
                                it.name.contains(className.substringAfterLast("."))
                    }
                    .firstOrNull()

                if (testFile != null) {
                    val isKotlin = testFile.extension == "kt"
                    val content = testFile.readText()
                    val lines = content.lines().toMutableList()

                    // Add import for @Ignore if not present
                    if (!content.contains("import org.junit.Ignore")) {
                        var lastImportIndex = -1
                        for (i in lines.indices) {
                            if (lines[i].trimStart().startsWith("import ")) {
                                lastImportIndex = i
                            }
                        }

                        if (lastImportIndex >= 0) {
                            lines.add(lastImportIndex + 1, "import org.junit.Ignore")
                        }
                    }

                    // For each method to ignore, find and annotate it
                    var linesAdded = 0
                    methodNames.forEach { methodName ->
                        // Find the method declaration
                        for (i in 0 until lines.size) {
                            val line = lines[i + linesAdded].trim()

                            // Look for @Test annotation or method declaration
                            val isTestAnnotation = line == "@Test"
                            val isMethodDeclaration = line.contains("fun $methodName") ||
                                    line.contains("void $methodName")

                            if (isTestAnnotation) {
                                // Check the next line to see if it's our target method
                                val nextLine = lines.getOrNull(i + linesAdded + 1)?.trim() ?: ""
                                if (nextLine.contains("fun $methodName") || nextLine.contains("void $methodName")) {
                                    // Insert @Ignore before @Test
                                    val indent = lines[i + linesAdded].takeWhile { it.isWhitespace() }
                                    val reason = "Flaky test detected by automated analysis"
                                    lines.add(i + linesAdded, "${indent}@Ignore(\"$reason\")")
                                    linesAdded++ // Adjust for the added line
                                    totalAnnotated++
                                    println("  Added @Ignore to $methodName in ${testFile.name}")
                                    break // Found and annotated this method
                                }
                            } else if (isMethodDeclaration) {
                                // Check if the previous line has @Test and does not have @Ignore
                                val prevLine = lines.getOrNull(i + linesAdded - 1)?.trim() ?: ""
                                if (prevLine == "@Test") {
                                    // Check if there's already an @Ignore even further back
                                    val prevPrevLine = lines.getOrNull(i + linesAdded - 2)?.trim() ?: ""
                                    if (!prevPrevLine.contains("@Ignore")) {
                                        // Insert @Ignore before @Test
                                        val indent = lines[i + linesAdded - 1].takeWhile { it.isWhitespace() }
                                        val reason = "Flaky test detected by automated analysis"
                                        lines.add(i + linesAdded - 1, "${indent}@Ignore(\"$reason\")")
                                        linesAdded++ // Adjust for the added line
                                        totalAnnotated++
                                        println("  Added @Ignore to $methodName in ${testFile.name}")
                                    }
                                }
                                break // Found this method, move to the next
                            }
                        }
                    }

                    // Write back the modified content
                    testFile.writeText(lines.joinToString("\n"))
                } else {
                    println("  WARNING: Could not find source file for class $className")
                }
            }

            println("\nSummary: Added @Ignore annotations to $totalAnnotated flaky tests")
        } else {
            println("\nNo flaky tests to ignore.")
        }
    }
}