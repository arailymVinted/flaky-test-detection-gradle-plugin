// reliability-gate.gradle.kts - A simple implementation of the reliability gate

// This task runs new test methods multiple times to detect flaky tests
tasks.register("reliabilityGate") {
    description = "Run new test methods multiple times to detect flaky tests"
    group = "verification"

    doLast {
        // 1. Get configuration
        val repeatCount = 5
        val baseBranch = "origin/main"

        // 2. Find modified test files
        val modifiedFiles = executeCommand("git diff --name-only $baseBranch -- src/test")
            .split("\n")
            .filter { it.isNotEmpty() && (it.endsWith(".kt") || it.endsWith(".java")) }

        logger.lifecycle("Found ${modifiedFiles.size} modified test files")

        // 3. Find new test methods in these files
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

            // Log the first 200 characters of the file for debugging
            logger.lifecycle("File content preview: ${currentContent.take(200)}")

            // Extract package and class name
            var packageName = extractPattern(currentContent, "package\\s+([\\w.]+)")
            var className = extractPattern(currentContent, "class\\s+(\\w+)")

            // Special case for files without package or with different class pattern
            if (packageName.isEmpty()) {
                // Assume default package if not specified
                packageName = ""
            }

            if (className.isEmpty()) {
                // Try to extract class name from file name if not found in content
                className = filePath.substringAfterLast("/").removeSuffix(".kt").removeSuffix(".java")
                logger.lifecycle("Using filename as class name: $className")
            }

            // Find test methods in current and base content
            val currentTestMethods = findTestMethods(currentContent)
            val baseTestMethods = findTestMethods(baseContent)

            logger.lifecycle("Current test methods: $currentTestMethods")
            logger.lifecycle("Base test methods: $baseTestMethods")

            val newMethods = currentTestMethods - baseTestMethods

            // Add fully qualified names
            newMethods.forEach { method ->
                val fullName = if (packageName.isEmpty()) {
                    "$className.$method"
                } else {
                    "$packageName.$className.$method"
                }
                newTestMethods.add(fullName)
            }
        }

        if (newTestMethods.isEmpty()) {
            logger.lifecycle("No new test methods found. Reliability gate passed!")
            return@doLast
        }

        logger.lifecycle("Found ${newTestMethods.size} new test methods:")
        newTestMethods.forEach { logger.lifecycle("- $it") }

        // 4. Run each new test multiple times
        var anyTestFailed = false

        newTestMethods.forEach { testMethod ->
            logger.lifecycle("Testing reliability of: $testMethod")
            var failCount = 0

            for (i in 1..repeatCount) {
                logger.lifecycle("Run $i/$repeatCount")

                try {
                    executeCommand("./gradlew test --tests $testMethod")
                    logger.lifecycle("✓ Run $i passed")
                } catch (e: Exception) {
                    logger.lifecycle("✗ Run $i failed")
                    failCount++
                    anyTestFailed = true
                }
            }

            if (failCount > 0) {
                logger.warn("Test $testMethod failed $failCount out of $repeatCount times")
            } else {
                logger.lifecycle("Test $testMethod passed all $repeatCount runs")
            }
        }

        // 5. Report results
        if (anyTestFailed) {
            throw GradleException("Reliability gate failed! Some tests showed flaky behavior.")
        } else {
            logger.lifecycle("Reliability gate passed! All new tests passed $repeatCount consecutive runs.")
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