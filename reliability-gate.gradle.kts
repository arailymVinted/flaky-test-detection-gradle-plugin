// reliability-gate.gradle.kts - Simplified implementation focused on core functionality

tasks.register("reliabilityGate") {
    description = "Run new test methods multiple times to detect flaky tests"
    group = "verification"

    doLast {
        // Configuration
        val repeatCount = project.findProperty("reliabilityGate.repeatCount")?.toString()?.toInt() ?: 5
        val baseBranch = project.findProperty("reliabilityGate.baseBranch")?.toString() ?: "origin/main"

        println("🔍 Reliability Gate: Checking for new tests (comparing with $baseBranch)")

        // Find new test methods
        val newTests = findNewTests(baseBranch)

        if (newTests.isEmpty()) {
            println("✅ Reliability Gate: No new test methods found. Gate passed!")
            return@doLast
        }

        println("📊 Reliability Gate: Found ${newTests.size} new test methods")

        // Track results
        val results = mutableMapOf<String, Pair<Int, Int>>() // test -> (passes, failures)
        var anyFailures = false

        // Run each test multiple times
        newTests.forEach { test ->
            println("\n🧪 Testing: $test")
            var passes = 0
            var failures = 0

            for (i in 1..repeatCount) {
                print("  Run $i/$repeatCount: ")

                try {
                    val result = project.exec {
                        commandLine("./gradlew", "test", "--tests", test)
                        isIgnoreExitValue = true
                    }

                    if (result.exitValue == 0) {
                        println("✓ PASS")
                        passes++
                    } else {
                        println("✗ FAIL")
                        failures++
                        anyFailures = true
                    }
                } catch (e: Exception) {
                    println("✗ FAIL (${e.message})")
                    failures++
                    anyFailures = true
                }
            }

            results[test] = passes to failures
        }

        // Print summary
        println("\n📋 RELIABILITY GATE SUMMARY")
        println("=======================")
        results.forEach { (test, counts) ->
            val (passes, failures) = counts
            val passRate = (passes.toDouble() / repeatCount) * 100
            val status = if (failures > 0) "❌ FLAKY" else "✅ STABLE"

            println("$status - $test (${passes}/${repeatCount} - ${passRate.toInt()}% pass rate)")
        }
        println("=======================")

        // Pass/fail the build
        if (anyFailures) {
            throw GradleException("Reliability gate failed! Some tests showed flaky behavior.")
        } else {
            println("✅ Reliability Gate passed! All new tests passed $repeatCount consecutive runs.")
        }
    }
}

// Find new test methods added compared to base branch
fun findNewTests(baseBranch: String): List<String> {
    val result = mutableListOf<String>()

    // Get modified test files
    val modifiedFiles = executeCommand("git diff --name-only $baseBranch -- src/test")
        .split("\n")
        .filter { it.isNotEmpty() && (it.endsWith(".kt") || it.endsWith(".java")) }

    modifiedFiles.forEach { filePath ->
        val file = file(filePath)
        if (!file.exists()) return@forEach

        val currentContent = file.readText()
        val baseContent = try {
            executeCommand("git show $baseBranch:$filePath")
        } catch (e: Exception) {
            "" // File might be new
        }

        // Extract class name from file
        val className = filePath.substringAfterLast("/").removeSuffix(".kt").removeSuffix(".java")

        // Find test methods in current and base content
        val currentTestMethods = findTestMethods(currentContent)
        val baseTestMethods = findTestMethods(baseContent)
        val newMethods = currentTestMethods - baseTestMethods

        // Add fully qualified test names
        newMethods.forEach { method ->
            result.add("$className.$method")
        }
    }

    return result
}

// Helper function to execute a command and return its output
fun executeCommand(command: String): String {
    val process = ProcessBuilder(*command.split(" ").toTypedArray())
        .redirectErrorStream(true)
        .start()

    val output = process.inputStream.bufferedReader().readText()
    val exitCode = process.waitFor()

    if (exitCode != 0) {
        throw RuntimeException("Command failed with exit code $exitCode: $command")
    }

    return output
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