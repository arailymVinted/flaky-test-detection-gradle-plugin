import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.RepeatedTest
import java.util.concurrent.ThreadLocalRandom
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * This test class contains:
 * - 18 consistently passing tests (testPassingXX)
 * - 17 consistently failing tests (testFailingXX)
 * - 15 flaky tests with varying degrees of flakiness:
 *   - 5 with low flakiness (10-30% failure rate) (testLowFlakyXX)
 *   - 5 with medium flakiness (40-60% failure rate) (testMediumFlakyXX)
 *   - 5 with high flakiness (70-90% failure rate) (testHighFlakyXX)
 */
class SampleFlakyTests {
    // ==================== CONSISTENTLY PASSING TESTS ====================

    @Test
    fun testPassing01_simpleAddition() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testPassing02_stringEquality() {
        val text = "Hello new World"
        assertEquals("Hello World", text)
    }

    @Test
    fun testPassing03_listContains() {
        val list = listOf(1, 2, 3, 4, 5)
        assertTrue(list.contains(3))
    }

    @Test
    fun testPassing04_mapAccess() {
        val map = mapOf("one" to 1, "two" to 2)
        assertEquals(2, map["two"])
    }

    @Test
    fun testPassing05_booleanLogic() {
        assertTrue(true && true)
        assertFalse(true && false)
    }

    @Test
    fun testPassing06_nullCheck() {
        val value: String? = null
        assertNull(value)
    }

    @Test
    fun testPassing07_notNullCheck() {
        val value = "not null"
        assertNotNull(value)
    }

    @Test
    fun testPassing08_stringLength() {
        assertEquals(5, "hello".length)
    }

    @Test
    fun testPassing09_arraySize() {
        val array = arrayOf(1, 2, 3)
        assertEquals(3, array.size)
    }

    @Test
    fun testPassing10_stringStartsWith() {
        assertTrue("Hello World".startsWith("Hello"))
    }

    @Test
    fun testPassing11_simpleSubtraction() {
        assertEquals(5, 10 - 5)
    }

    @Test
    fun testPassing12_divisionResult() {
        assertEquals(4, 16 / 4)
    }

    @Test
    fun testPassing13_moduloOperation() {
        assertEquals(1, 10 % 3)
    }

    @Test
    fun testPassing14_stringConcatenation() {
        assertEquals("HelloWorld", "Hello" + "World")
    }

    @Test
    fun testPassing15_listFilter() {
        val list = listOf(1, 2, 3, 4, 5)
        val filtered = list.filter { it > 3 }
        assertEquals(2, filtered.size)
    }

    @Test
    fun testPassing16_charToInt() {
        assertEquals(97, 'a'.code)
    }

    @Test
    fun testPassing17_listSum() {
        val list = listOf(1, 2, 3, 4, 5)
        assertEquals(15, list.sum())
    }

    @Test
    fun testPassing18_stringTrimming() {
        assertEquals("hello", "  hello  ".trim())
    }

    // ==================== CONSISTENTLY FAILING TESTS ====================

    @Test
    fun testFailing01_wrongAddition() {
        assertEquals(5, 2 + 2, "2 + 2 should equal 4, not 5")
    }

    @Test
    fun testFailing02_incorrectString() {
        val text = "Hello World"
        assertEquals("Hello Universe", text, "Strings don't match")
    }

    @Test
    fun testFailing03_missingElement() {
        val list = listOf(1, 2, 3, 4, 5)
        assertTrue(list.contains(6), "List doesn't contain 6")
    }

    @Test
    fun testFailing04_invalidMapKey() {
        val map = mapOf("one" to 1, "two" to 2)
        assertEquals(3, map["three"], "Key 'three' doesn't exist")
    }

    @Test
    fun testFailing05_oppositeBooleanLogic() {
        assertTrue(false, "False should be true")
    }

    @Test
    fun testFailing06_nullNotExpected() {
        val value: String? = null
        assertNotNull(value, "Value shouldn't be null")
    }

    @Test
    fun testFailing07_expectNull() {
        val value = "not null"
        assertNull(value, "Value should be null")
    }

    @Test
    fun testFailing08_wrongLength() {
        assertEquals(10, "hello".length, "Length of 'hello' is 5, not 10")
    }

    @Test
    fun testFailing09_wrongArraySize() {
        val array = arrayOf(1, 2, 3)
        assertEquals(5, array.size, "Array size is 3, not 5")
    }

    @Test
    fun testFailing10_wrongStartsWith() {
        assertTrue("Hello World".startsWith("Goodbye"), "String doesn't start with 'Goodbye'")
    }

    @Test
    fun testFailing11_dividedByZero() {
        assertEquals(0, 1 / 0, "Should cause ArithmeticException")
    }

    @Test
    fun testFailing12_indexOutOfBounds() {
        val list = listOf(1, 2, 3)
        assertEquals(4, list[5], "Index 5 is out of bounds")
    }

    @Test
    fun testFailing13_incorrectClassCast() {
        val any: Any = "string"
        val number = any as Int
        assertEquals(10, number, "Cannot cast String to Int")
    }

    @Test
    fun testFailing14_wrongEquality() {
        val obj1 = Object()
        val obj2 = Object()
        assertEquals(obj1, obj2, "Different objects are not equal")
    }

    @Test
    fun testFailing15_incorrectMath() {
        assertEquals(10, 3 * 3, "3 * 3 is 9, not 10")
    }

    @Test
    fun testFailing16_stringComparisonCase() {
        assertEquals("hello", "HELLO", "Case-sensitive comparison fails")
    }

    @Test
    fun testFailing17_wrongAssertion() {
        assertTrue("abc" == "def", "Strings are not equal")
    }

    // ==================== LOW FLAKINESS TESTS (10-30% failure rate) ====================

    @Test
    fun testLowFlaky01_randomNumber() {
        // Target: ~97/101 passes (3-4% failure rate)
        val random = ThreadLocalRandom.current().nextInt(1, 33)
        assertFalse(random == 1, "Random number should not be 1")
    }

    @Test
    fun testLowFlaky02_timeBased() {
        // Target: ~97/101 passes (3-4% failure rate)
        val millisecond = System.currentTimeMillis() % 100
        assertFalse(millisecond < 7, "Time-based condition failed (millisecond: $millisecond)")
    }

    @Test
    fun testLowFlaky03_dateCheck() {
        // Target: ~97/101 passes (3-4% failure rate)
        val value = ThreadLocalRandom.current().nextDouble()
        assertTrue(value >= 0.02, "Random value $value should be >= 0.03")
    }

    @Test
    fun testLowFlaky04_probabilisticTest() {
        // Target: ~97/101 passes (3-4% failure rate)
        val value = ThreadLocalRandom.current().nextDouble()
        assertTrue(value >= 0.03, "Random value $value should be >= 0.03")
    }

    @Test
    fun testLowFlaky05_racyCounter() {
        // Target: ~97/101 passes (3-4% failure rate)
        var counter = 0
        val threads = List(3) {
            Thread {
                synchronized(this) {
                    counter++
                }
            }
        }
        threads.forEach { it.start() }
        Thread.sleep(5)

        // This will mostly pass but occasionally fail
        val random = ThreadLocalRandom.current().nextInt(1, 31)
        assertTrue(counter == 3 && random != 1, "Counter should be 3 but was $counter, or random condition failed")
    }

    // ==================== MEDIUM FLAKINESS TESTS (40-60% failure rate) ====================

    @Test
    fun testMediumFlaky01_coinFlip() {
        // Targeting entropy ~0.3, flip rate ~0.2
        // Need passes around 80% of the time
        val random = ThreadLocalRandom.current().nextInt(1, 6)
        assertTrue(random != 1, "Random number should not be 1")
    }

    @Test
    fun testMediumFlaky02_diceRoll() {
        // Targeting entropy ~0.3, flip rate ~0.2
        val dice = ThreadLocalRandom.current().nextInt(1, 5)
        assertTrue(dice > 1, "Dice roll $dice should be > 1")
    }

    @Test
    fun testMediumFlaky03_randomRange() {
        // Targeting entropy ~0.3, flip rate ~0.2
        val value = ThreadLocalRandom.current().nextInt(1, 100)
        assertTrue(value > 20, "Value $value should be > 20")
    }

    @Test
    fun testMediumFlaky04_timeBasedMedium() {
        // Targeting entropy ~0.3, flip rate ~0.2
        val millisecond = System.currentTimeMillis() % 5
        assertTrue(millisecond >= 1, "Millisecond $millisecond should be >= 1")
    }

    @Test
    fun testMediumFlaky05_raceCondition() {
        // Modify to achieve a more consistent ~20% failure rate
        var sharedValue = 0
        val threads = List(10) {
            Thread {
                // Controlled race condition
                if (it % 5 != 0) { // 80% of threads operate normally
                    synchronized(this) {
                        sharedValue++
                    }
                } else { // 20% of threads delay
                    Thread.sleep(5)
                    synchronized(this) {
                        sharedValue++
                    }
                }
            }
        }
        threads.forEach { it.start() }
        Thread.sleep(15)
        assertEquals(10, sharedValue, "Expected 10 but got $sharedValue")
    }

    @Test
    @RepeatedTest(10)
    fun testCoinFlip() {
        val flip = ThreadLocalRandom.current().nextBoolean()
        assertTrue(flip, "Coin flip should be head")
    }
    // ==================== HIGH FLAKINESS TESTS (70-90% failure rate) ====================

    @Test
    fun testHighFlaky01_veryRandomNumber() {
        // Targeting entropy >0.48, flip rate >0.31
        // Need ~35-40% pass rate
        val random = ThreadLocalRandom.current().nextInt(1, 10)
        assertTrue(random >= 7, "Random number should be >= 7")
    }

    @Test
    fun testHighFlaky02_narrowTimeWindow() {
        // Targeting entropy >0.48, flip rate >0.31
        val millisecond = System.currentTimeMillis() % 10
        assertTrue(millisecond >= 6, "Millisecond should be >= 6, was $millisecond")
    }

    @Test
    fun testHighFlaky03_almostAlwaysFailing() {
        // Targeting entropy >0.48, flip rate >0.31
        val value = ThreadLocalRandom.current().nextDouble()
        assertTrue(value >= 0.65, "Value $value should be >= 0.65")
    }

    @Test
    fun testHighFlaky04_racyThreads() {
        // Adjust to ensure a high flip rate but not 100% failure
        val values = mutableListOf<Int>()
        val threads = List(3) {
            Thread {
                Thread.sleep(ThreadLocalRandom.current().nextInt(1, 5).toLong())
                synchronized(values) {
                    values.add(it)
                }
            }
        }
        threads.forEach { it.start() }
        Thread.sleep(20)

        // More achievable ordering that will still fail ~65% of the time
        val expected = listOf(0, 1, 2)
        assertEquals(expected, values, "Thread execution order $values should match expected order")
    }

    @Test
    fun testHighFlaky05_extremelyUnlikely() {
        // Targeting entropy >0.48, flip rate >0.31
        val timeValue = ThreadLocalRandom.current().nextInt(1, 3)
        assertTrue(timeValue == 2, "Value should be 2, was $timeValue")
    }}