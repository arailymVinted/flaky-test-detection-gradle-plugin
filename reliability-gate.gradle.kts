// reliability-gate.gradle.kts - A simple implementation of the reliability gate

// This task runs new test methods multiple times to detect flaky tests
tasks.register("reliabilityGate") {
    description = "Run new test methods multiple times to detect flaky tests"
    group = "verification"

    doLast {
        // Configuration with defaults
        val repeatCount = project.findProperty("reliabilityGate.repeatCount")?.toString()?.toInt() ?: 5
        val baseBranch = project.findProperty("reliabilityGate.baseBranch")?.toString() ?: "origin/main"
        val skipTestsWithPrefix = project.findProperty("reliabilityGate.skipTestsWithPrefix")?.toString()?.split(",") ?: listOf()

        // Find modified test files
        val modifiedFiles = executeCommand("git diff --name-only $baseBranch -- src/test")
            .split("\n")
            .filter { it.isNotEmpty() && (it.endsWith(".kt") || it.endsWith(".java")) }

        logger.lifecycle("Found ${modifiedFiles.size} modified test files")

        // Find new test methods in these files
        val newTestMethods = mutableListOf<String>()

        modifiedFiles.forEach { filePath ->
            // Get current file content
            val file = file(filePath)
            if (!file.exists()) {
                logger.warn("File not found: $filePath")
                return@forEach
            }

            val currentContent = file.readText()

            // Get file content from base branch (if exists)
            val baseContent = try {
                executeCommand("git show $baseBranch:$filePath")
            } catch (e: Exception) {
                "" // File might be new
            }

            // Extract package and class name
            var packageName = extractPattern(currentContent, "package\\s+([\\w.]+)")

            // Extract class name from file name and content
            val classNameFromFile = filePath.substringAfterLast("/").removeSuffix(".kt").removeSuffix(".java")
            val classNameFromContent = extractPattern(currentContent, "class\\s+(\\w+)")
            val className = classNameFromContent.ifEmpty { classNameFromFile }

            logger.lifecycle("File: $filePath, Package: $packageName, Class: $className")

            // Find test methods in current and base content
            val currentTestMethods = findTestMethods(currentContent)
            val baseTestMethods = findTestMethods(baseContent)

            logger.lifecycle("Current test methods: $currentTestMethods")
            logger.lifecycle("Base test methods: $baseTestMethods")

            val newMethods = currentTestMethods - baseTestMethods

            // Add test methods with fully qualified name
            newMethods.forEach { method ->
                // Construct fully qualified test name
                val fullName = if (packageName.isEmpty()) {
                    "$className.$method"
                } else {
                    "$packageName.$className.$method"
                }
                newTestMethods.add(fullName)
            }
        }

        // Filter out tests to skip
        val filteredTestMethods = newTestMethods.filter { testMethod ->
            !skipTestsWithPrefix.any { prefix -> testMethod.contains(prefix) }
        }

        if (filteredTestMethods.isEmpty()) {
            logger.lifecycle("No new test methods found. Reliability gate passed!")
            return@doLast
        }

        logger.lifecycle("Found ${filteredTestMethods.size} new test methods:")
        filteredTestMethods.forEach { logger.lifecycle("- $it") }

        // Run each new test multiple times
        var anyTestFailed = false

        filteredTestMethods.forEach { testMethod ->
            logger.lifecycle("Testing reliability of: $testMethod")
            var failCount = 0
            var passCount = 0

            for (i in 1..repeatCount) {
                logger.lifecycle("Run $i/$repeatCount")

                // Use the fully qualified test name for the Gradle test task
                val gradleTestFilter = testMethod

                try {
                    val result = project.exec {
                        commandLine("./gradlew", "test", "--tests", gradleTestFilter)
                        isIgnoreExitValue = true
                    }

                    if (result.exitValue == 0) {
                        logger.lifecycle("✓ Run $i passed")
                        passCount++
                    } else {
                        logger.lifecycle("✗ Run $i failed")
                        failCount++
                        anyTestFailed = true
                    }
                } catch (e: Exception) {
                    logger.lifecycle("✗ Run $i failed with exception: ${e.message}")
                    failCount++
                    anyTestFailed = true
                }
            }

            if (failCount > 0) {
                logger.warn("Test $testMethod failed $failCount out of $repeatCount times (passed $passCount times)")
            } else {
                logger.lifecycle("Test $testMethod passed all $repeatCount runs")

                // For tests that contain "Failing" in the name but passed all runs
                if (testMethod.contains("Failing")) {
                    logger.warn("⚠️ Warning: Test '$testMethod' has 'Failing' in its name but passed all runs. This might indicate a problem.")
                }
            }
        }

        // Report results
        if (anyTestFailed) {
            throw GradleException("Reliability gate failed! Some tests showed flaky behavior.")
        } else {
            logger.lifecycle("Reliability gate passed! All new tests passed $repeatCount consecutive runs.")

            // Check if any "Failing" tests unexpectedly passed
            val unexpectedlyPassingTests = filteredTestMethods.filter { it.contains("Failing") }
            if (unexpectedlyPassingTests.isNotEmpty()) {
                logger.warn("⚠️ Warning: ${unexpectedlyPassingTests.size} tests with 'Failing' in their name passed all runs:")
                unexpectedlyPassingTests.forEach { logger.warn("  - $it") }
                logger.warn("This might indicate issues with your test execution. These tests were expected to fail but didn't.")
            }
        }
    }
}

// Helper function to execute a command and return its output
fun executeCommand(command: String): String {
    val process = ProcessBuilder(*command.split(" ").toTypedArray())
        .redirectErrorStream(true)
        .start()

    val output = process.inputStream.bufferedReader().readText()
    val exitCode = process.waitFor()

    if (exitCode != 0) {
        throw RuntimeException("Command failed with exit code $exitCode: $command\nOutput: $output")
    }

    return output
}

// Helper function to extract a pattern from text
fun extractPattern(text: String, pattern: String): String {
    val regex = pattern.toRegex()
    val match = regex.find(text)
    return match?.groupValues?.get(1) ?: ""
}

// Helper function to find test methods in file content
fun findTestMethods(content: String): Set<String> {
    val methods = mutableSetOf<String>()

    // Find @Test methods in Kotlin
    val kotlinPattern = "@Test\\s+(?:@[\\w\\d]+\\s+)*fun\\s+(\\w+)\\s*\\(".toRegex()
    kotlinPattern.findAll(content).forEach { match ->
        methods.add(match.groupValues[1])
    }

    // Find @Test methods in Java
    val javaPattern = "@Test\\s+(?:@[\\w\\d]+\\s+)*(?:public\\s+)?(?:void|[\\w<>\\[\\]]+)\\s+(\\w+)\\s*\\(".toRegex()
    javaPattern.findAll(content).forEach { match ->
        methods.add(match.groupValues[1])
    }

    return methods
}