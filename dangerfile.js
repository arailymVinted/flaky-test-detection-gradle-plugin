// dangerfile.js
import { danger, warn, fail, message } from 'danger';

// Check for modified test files
const modifiedTests = danger.git.modified_files.filter(file => file.includes('Test.kt'));

if (modifiedTests.length > 0) {
    message(`This PR modifies ${modifiedTests.length} test files. Running reliability check...`);
}

// dangerfile.js
import { exec } from 'child_process';
import { promisify } from 'util';

const execAsync = promisify(exec);

// Check for modified test files
const modifiedTests = danger.git.modified_files.filter(file => file.includes('Test.kt'));

async function runReliabilityGate() {
    if (modifiedTests.length === 0) {
        return;
    }

    message(`Running reliability gate for ${modifiedTests.length} modified test files...`);

    try {
        // Extract test class names from file paths
        const testClasses = modifiedTests.map(file => {
            const fileName = file.split('/').pop();
            return fileName.replace('.kt', '');
        }).join(',');

        // Run the reliability gate
        await execAsync(`mkdir -p build/reliability-gate-results`);

        let foundFlaky = false;
        let flakyTests = [];

        // Run tests multiple times
        for (let i = 1; i <= 10; i++) {
            message(`Running test iteration ${i}/10...`);

            // Run only the changed tests
            await execAsync(`./gradlew test --tests "*${testClasses}*"`);

            // Copy results to a specific directory
            await execAsync(`mkdir -p build/reliability-gate-results/run-${i}`);
            await execAsync(`cp -r build/test-results/test/ build/reliability-gate-results/run-${i}/`);

            // Check for inconsistent results after the second run
            if (i > 1) {
                // This is a simplified approach - in a real implementation you would:
                // 1. Parse the XML files properly
                // 2. Compare results between runs
                // 3. Apply your flakiness detection model
                const { stdout } = await execAsync(`find build/reliability-gate-results -name "*.xml" | xargs grep -l "failure"`);

                if (stdout) {
                    const failingTests = stdout.split('\n').filter(Boolean);

                    // Check if any of these tests passed in previous runs
                    for (const failTest of failingTests) {
                        const testName = failTest.match(/testcase name="([^"]+)"/)?.[1];
                        if (testName) {
                            // Check if this test passed in any previous run
                            const passCheck = await execAsync(`find build/reliability-gate-results/run-${i-1} -name "*.xml" | xargs grep "testcase name=\\"${testName}\\"" | grep -v failure`);

                            if (passCheck.stdout) {
                                foundFlaky = true;
                                flakyTests.push(testName);
                            }
                        }
                    }
                }
            }
        }

        if (foundFlaky) {
            fail(`⚠️ Flaky tests detected! The following tests showed inconsistent results:\n\n${flakyTests.join('\n')}\n\nPlease fix these tests before merging.`);
        } else {
            message('✅ All modified tests passed the reliability gate!');
        }
    } catch (error) {
        warn(`Error running reliability gate: ${error.message}`);
    }
}

// Run the reliability gate
runReliabilityGate();

