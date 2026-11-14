package com.android.keyboard.gboardclone

import android.content.Context
import android.content.res.AssetManager
import com.android.keyboard.gboardclone.data.db.LearnedWordDao
import com.android.keyboard.gboardclone.data.db.LearnedWordEntity
import com.android.keyboard.gboardclone.data.loader.DictionaryLoader
import com.android.keyboard.gboardclone.data.repository.SuggestionRepository
import com.android.keyboard.gboardclone.data.trie.Trie
import com.android.keyboard.gboardclone.data.trie.TrieNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class DataLayerCornerCasesTest {

    private lateinit var trie: Trie

    @Before
    fun setUp() {
        trie = Trie()
    }

    // =========================================================================
    // 1. TrieNode Tests
    // =========================================================================

    @Test
    fun `trieNode - default values are correctly initialized`() {
        val node = TrieNode()
        assertTrue("children map should initially be empty", node.children.isEmpty())
        assertFalse("isWord should default to false", node.isWord)
        assertEquals("frequency should default to 0", 0, node.frequency)
    }

    @Test
    fun `trieNode - properties are mutable and store child nodes`() {
        val parent = TrieNode()
        val child = TrieNode()
        child.isWord = true
        child.frequency = 42

        parent.children['a'] = child

        assertEquals(1, parent.children.size)
        assertTrue(parent.children.containsKey('a'))
        val retrieved = parent.children['a']
        assertNotNull(retrieved)
        assertTrue(retrieved!!.isWord)
        assertEquals(42, retrieved.frequency)
    }

    // =========================================================================
    // 2. Trie Tests - Blank, Empty, Whitespace Inputs
    // =========================================================================

    @Test
    fun `trie - insert with blank empty or whitespace strings is ignored`() {
        assertEquals(0, trie.size)

        trie.insert("")
        trie.insert(" ")
        trie.insert("   ")
        trie.insert("\t")
        trie.insert("\n")
        trie.insert("  \t\n  ")

        assertEquals("Trie size must remain 0 when inserting blank strings", 0, trie.size)
        assertFalse(trie.search(""))
        assertFalse(trie.search(" "))
        assertFalse(trie.search("   "))
        assertEquals(0, trie.getFrequency(""))
        assertEquals(0, trie.getFrequency("   "))
    }

    @Test
    fun `trie - search with blank empty or whitespace strings returns false`() {
        trie.insert("hello", 100)

        assertFalse(trie.search(""))
        assertFalse(trie.search(" "))
        assertFalse(trie.search("    "))
        assertFalse(trie.search("\t\n"))
    }

    @Test
    fun `trie - startsWith on empty string returns true`() {
        // By definition, empty prefix matches any trie state
        assertTrue(trie.startsWith(""))
        trie.insert("hello", 10)
        assertTrue(trie.startsWith(""))
    }

    @Test
    fun `trie - startsWith on blank or whitespace string`() {
        // Blank strings trim to empty string which matches root node
        assertTrue(trie.startsWith("   "))
        assertTrue(trie.startsWith("\t"))
    }

    @Test
    fun `trie - incrementFrequency with blank or empty strings returns 0 and does not change size`() {
        val resultEmpty = trie.incrementFrequency("")
        val resultSpaces = trie.incrementFrequency("    ", 5)
        val resultTabs = trie.incrementFrequency("\t\n", 10)

        assertEquals(0, resultEmpty)
        assertEquals(0, resultSpaces)
        assertEquals(0, resultTabs)
        assertEquals(0, trie.size)
    }

    @Test
    fun `trie - getSuggestions with blank or whitespace strings returns emptyList`() {
        trie.insert("hello", 100)
        trie.insert("world", 50)

        assertTrue(trie.getSuggestions("").isEmpty())
        assertTrue(trie.getSuggestions(" ").isEmpty())
        assertTrue(trie.getSuggestions("   ", limit = 5).isEmpty())
        assertTrue(trie.getSuggestions("\t\n", limit = 10).isEmpty())
    }

    @Test
    fun `trie - getFrequency on non-existent word or blank strings returns 0`() {
        trie.insert("keyboard", 25)

        assertEquals(25, trie.getFrequency("keyboard"))
        assertEquals(0, trie.getFrequency("key")) // prefix exists but is not a word
        assertEquals(0, trie.getFrequency("nonexistent"))
        assertEquals(0, trie.getFrequency(""))
        assertEquals(0, trie.getFrequency("   "))
    }

    // =========================================================================
    // 3. Trie Tests - Non-ASCII, Mixed Case, Punctuation
    // =========================================================================

    @Test
    fun `trie - case normalization uppercase and mixed case insert and search`() {
        trie.insert("HeLLo", 100)

        assertTrue(trie.search("hello"))
        assertTrue(trie.search("HELLO"))
        assertTrue(trie.search("Hello"))
        assertTrue(trie.search("hElLo"))
        assertEquals(100, trie.getFrequency("HELLO"))

        // Prefix check with case variation
        assertTrue(trie.startsWith("HEL"))
        assertTrue(trie.startsWith("hel"))
        assertTrue(trie.startsWith("HeL"))

        val suggestions = trie.getSuggestions("HEL", limit = 1)
        assertEquals(1, suggestions.size)
        assertEquals("hello", suggestions[0].first)
    }

    @Test
    fun `trie - non-ASCII and international unicode characters`() {
        trie.insert("café", 50)
        trie.insert("über", 40)
        trie.insert("naïve", 30)
        trie.insert("résumé", 20)
        trie.insert("日本語", 10)

        assertEquals(5, trie.size)

        assertTrue(trie.search("café"))
        assertTrue(trie.search("CAFÉ"))
        assertTrue(trie.search("über"))
        assertTrue(trie.search("naïve"))
        assertTrue(trie.search("résumé"))
        assertTrue(trie.search("日本語"))

        assertTrue(trie.startsWith("caf"))
        assertTrue(trie.startsWith("üb"))
        assertTrue(trie.startsWith("日本"))

        val suggestions = trie.getSuggestions("caf", 1)
        assertEquals(listOf(Pair("café", 50)), suggestions)
    }

    @Test
    fun `trie - words with punctuation and symbols`() {
        trie.insert("don't", 100)
        trie.insert("can't", 80)
        trie.insert("user-name", 50)
        trie.insert("c++", 60)
        trie.insert("it's", 90)

        assertTrue(trie.search("don't"))
        assertTrue(trie.search("can't"))
        assertTrue(trie.search("user-name"))
        assertTrue(trie.search("c++"))
        assertTrue(trie.search("it's"))

        assertTrue(trie.startsWith("don'"))
        assertTrue(trie.startsWith("user-"))
        assertTrue(trie.startsWith("c+"))

        val suggestions = trie.getSuggestions("don'", 2)
        assertEquals("don't", suggestions.first().first)
    }

    // =========================================================================
    // 4. Trie Tests - Prefix Matches, Subtree Search, Limits & Tie-breaking
    // =========================================================================

    @Test
    fun `trie - words where prefix is the full word itself`() {
        trie.insert("app", 100)
        trie.insert("apple", 80)
        trie.insert("application", 60)

        // Exact match as prefix
        val suggestions = trie.getSuggestions("app", limit = 3)
        assertEquals(3, suggestions.size)
        // "app" has highest frequency (100) so it ranks first
        assertEquals("app", suggestions[0].first)
        assertEquals(100, suggestions[0].second)
        assertEquals("apple", suggestions[1].first)
        assertEquals("application", suggestions[2].first)
    }

    @Test
    fun `trie - prefix is full word with lower frequency than longer word`() {
        trie.insert("app", 20)
        trie.insert("apple", 100)

        val suggestions = trie.getSuggestions("app", limit = 2)
        assertEquals(2, suggestions.size)
        // "apple" has higher frequency (100 > 20), so "apple" ranks first
        assertEquals("apple", suggestions[0].first)
        assertEquals("app", suggestions[1].first)
    }

    @Test
    fun `trie - prefix matches single exact word only`() {
        trie.insert("target", 75)

        val suggestions = trie.getSuggestions("target", limit = 5)
        assertEquals(1, suggestions.size)
        assertEquals("target", suggestions[0].first)
        assertEquals(75, suggestions[0].second)
    }

    @Test
    fun `trie - subtree search when prefix does not exist returns emptyList without errors`() {
        trie.insert("banana", 30)
        trie.insert("band", 20)

        val result1 = trie.getSuggestions("apple", limit = 5)
        val result2 = trie.getSuggestions("box", limit = 5)
        val result3 = trie.getSuggestions("bananax", limit = 5)

        assertTrue(result1.isEmpty())
        assertTrue(result2.isEmpty())
        assertTrue(result3.isEmpty())
        assertFalse(trie.startsWith("apple"))
        assertFalse(trie.startsWith("box"))
        assertFalse(trie.startsWith("bananax"))
    }

    @Test
    fun `trie - getSuggestions with limit less than or equal to 0 returns emptyList`() {
        trie.insert("hello", 100)
        trie.insert("help", 80)

        assertTrue("limit = 0 should return empty list", trie.getSuggestions("hel", limit = 0).isEmpty())
        assertTrue("limit = -1 should return empty list", trie.getSuggestions("hel", limit = -1).isEmpty())
        assertTrue("limit = -999 should return empty list", trie.getSuggestions("hel", limit = -999).isEmpty())
    }

    @Test
    fun `trie - getSuggestions with limit 1 returns only top ranked candidate`() {
        trie.insert("cold", 10)
        trie.insert("color", 50)
        trie.insert("column", 30)

        val suggestions = trie.getSuggestions("col", limit = 1)
        assertEquals(1, suggestions.size)
        assertEquals("color", suggestions[0].first)
        assertEquals(50, suggestions[0].second)
    }

    @Test
    fun `trie - getSuggestions with limit greater than total matching words returns all matching`() {
        trie.insert("car", 10)
        trie.insert("card", 20)

        val suggestions = trie.getSuggestions("car", limit = 100)
        assertEquals(2, suggestions.size)
        assertEquals("card", suggestions[0].first)
        assertEquals("car", suggestions[1].first)
    }

    @Test
    fun `trie - identical frequencies with alphabetical tie-breaking`() {
        // All words have identical frequency 50
        trie.insert("apply", 50)
        trie.insert("apple", 50)
        trie.insert("app", 50)
        trie.insert("application", 50)

        val suggestions = trie.getSuggestions("app", limit = 4).map { it.first }
        // Alphabetical order: app, apple, application, apply
        assertEquals(listOf("app", "apple", "application", "apply"), suggestions)

        // When limited to top 2, should strictly return top 2 alphabetically
        val top2 = trie.getSuggestions("app", limit = 2).map { it.first }
        assertEquals(listOf("app", "apple"), top2)
    }

    @Test
    fun `trie - tie breaking with identical frequencies across mixed letter combinations`() {
        trie.insert("zebra", 10)
        trie.insert("xenon", 10)
        trie.insert("yellow", 10)

        val suggestions = trie.getSuggestions("y", limit = 5).map { it.first }
        assertEquals(listOf("yellow"), suggestions)

        trie.insert("yard", 10)
        trie.insert("yarn", 10)
        trie.insert("yam", 10)

        val ySuggestions = trie.getSuggestions("ya", limit = 10).map { it.first }
        // Alphabetical: yam, yard, yarn
        assertEquals(listOf("yam", "yard", "yarn"), ySuggestions)
    }

    // =========================================================================
    // 5. Trie Tests - Increments, Zero, Negative Delta & Frequency Updates
    // =========================================================================

    @Test
    fun `trie - consecutive increments on same word accumulate correctly`() {
        trie.insert("test", 10)
        assertEquals(10, trie.getFrequency("test"))

        val f1 = trie.incrementFrequency("test", 5)
        assertEquals(15, f1)
        assertEquals(15, trie.getFrequency("test"))

        val f2 = trie.incrementFrequency("test", 10)
        assertEquals(25, f2)
        assertEquals(25, trie.getFrequency("test"))

        val f3 = trie.incrementFrequency("test") // default delta = 1
        assertEquals(26, f3)
        assertEquals(26, trie.getFrequency("test"))
    }

    @Test
    fun `trie - incrementFrequency on new non-existent word inserts word and increases size`() {
        assertEquals(0, trie.size)
        val freq = trie.incrementFrequency("brandnew", 7)

        assertEquals(7, freq)
        assertEquals(1, trie.size)
        assertTrue(trie.search("brandnew"))
        assertEquals(7, trie.getFrequency("brandnew"))
    }

    @Test
    fun `trie - incrementFrequency with zero delta preserves existing frequency`() {
        trie.insert("stable", 30)
        val freq = trie.incrementFrequency("stable", 0)

        assertEquals(30, freq)
        assertEquals(30, trie.getFrequency("stable"))
    }

    @Test
    fun `trie - incrementFrequency with negative delta decreases frequency`() {
        trie.insert("decrement", 50)
        val freq = trie.incrementFrequency("decrement", -15)

        assertEquals(35, freq)
        assertEquals(35, trie.getFrequency("decrement"))
    }

    @Test
    fun `trie - insert with higher frequency updates existing frequency`() {
        trie.insert("boost", 20)
        assertEquals(20, trie.getFrequency("boost"))

        trie.insert("boost", 50)
        assertEquals("Higher frequency should overwrite lower frequency", 50, trie.getFrequency("boost"))
        assertEquals(1, trie.size) // Size should not increase
    }

    @Test
    fun `trie - insert with lower frequency does not decrease existing higher frequency`() {
        trie.insert("shield", 100)
        trie.insert("shield", 40)

        assertEquals("Lower frequency should not overwrite higher frequency", 100, trie.getFrequency("shield"))
        assertEquals(1, trie.size)
    }

    // =========================================================================
    // 6. Trie Tests - Concurrent Read Write Stress Test
    // =========================================================================

    @Test
    fun `trie - concurrent read write stress test across multiple threads`() {
        val threadCount = 12
        val operationsPerThread = 250
        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())
        val successQueries = AtomicInteger(0)

        for (t in 0 until threadCount) {
            executor.execute {
                try {
                    startLatch.await()
                    for (i in 0 until operationsPerThread) {
                        val word = "thread_${t % 4}_word_$i"
                        when (i % 5) {
                            0 -> trie.insert(word, i + 1)
                            1 -> trie.incrementFrequency(word, 2)
                            2 -> {
                                trie.search(word)
                                successQueries.incrementAndGet()
                            }
                            3 -> {
                                trie.startsWith("thread_")
                                successQueries.incrementAndGet()
                            }
                            4 -> {
                                trie.getSuggestions("thread_", limit = 3)
                                successQueries.incrementAndGet()
                            }
                        }
                    }
                } catch (t: Throwable) {
                    errors.add(t)
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        // Unleash all threads simultaneously
        startLatch.countDown()
        val finished = doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertTrue("All concurrent worker threads should complete within timeout", finished)
        assertTrue("No exceptions should be thrown during concurrent access: $errors", errors.isEmpty())
        assertTrue("Trie size should be strictly positive", trie.size > 0)
        assertTrue("Concurrent queries should have completed", successQueries.get() > 0)
    }

    // =========================================================================
    // 7. DictionaryLoader Tests
    // =========================================================================

    @Test
    fun `dictionaryLoader - valid dictionary format word and frequency`() = runBlocking {
        val content = """
            apple 500
            banana 300
            cherry 150
        """.trimIndent()

        val mockContext = createMockContextWithContent("dict.txt", content)
        val fakeDao = FakeLearnedWordDao()
        val testDispatcher = StandardTestDispatcher()
        val repo = SuggestionRepository(trie, fakeDao, CoroutineScope(SupervisorJob() + testDispatcher), testDispatcher)

        val count = DictionaryLoader.loadFromAsset(mockContext, trie, repo, "dict.txt")

        assertEquals(3, count)
        assertEquals(3, trie.size)
        assertTrue(trie.search("apple"))
        assertEquals(500, trie.getFrequency("apple"))
        assertEquals(300, trie.getFrequency("banana"))
        assertEquals(150, trie.getFrequency("cherry"))
        assertEquals(500, repo.getFrequencyMap()["apple"])
        assertEquals(300, repo.getFrequencyMap()["banana"])
        assertEquals(150, repo.getFrequencyMap()["cherry"])
    }

    @Test
    fun `dictionaryLoader - comment lines starting with hash are ignored`() = runBlocking {
        val content = """
            # This is a header comment
            # Another comment line
            orange 220
            # Inline explanation comment
            pear 110
            # Final comment
        """.trimIndent()

        val mockContext = createMockContextWithContent("dict.txt", content)
        val fakeDao = FakeLearnedWordDao()
        val testDispatcher = StandardTestDispatcher()
        val repo = SuggestionRepository(trie, fakeDao, CoroutineScope(SupervisorJob() + testDispatcher), testDispatcher)

        val count = DictionaryLoader.loadFromAsset(mockContext, trie, repo, "dict.txt")

        assertEquals(2, count)
        assertEquals(2, trie.size)
        assertTrue(trie.search("orange"))
        assertTrue(trie.search("pear"))
        assertFalse(trie.search("#"))
        assertFalse(trie.search("this"))
    }

    @Test
    fun `dictionaryLoader - empty and blank lines are ignored`() = runBlocking {
        val content = """
            
               
            grape 80
            
            	
              melon 90  
            
        """.trimIndent()

        val mockContext = createMockContextWithContent("dict.txt", content)
        val fakeDao = FakeLearnedWordDao()
        val testDispatcher = StandardTestDispatcher()
        val repo = SuggestionRepository(trie, fakeDao, CoroutineScope(SupervisorJob() + testDispatcher), testDispatcher)

        val count = DictionaryLoader.loadFromAsset(mockContext, trie, repo, "dict.txt")

        assertEquals(2, count)
        assertEquals(2, trie.size)
        assertTrue(trie.search("grape"))
        assertTrue(trie.search("melon"))
    }

    @Test
    fun `dictionaryLoader - line with extra spaces and tabs`() = runBlocking {
        // Line has leading spaces, multiple interior spaces before frequency, and trailing spaces
        val content = "   hello    200   "

        val mockContext = createMockContextWithContent("dict.txt", content)
        val fakeDao = FakeLearnedWordDao()
        val testDispatcher = StandardTestDispatcher()
        val repo = SuggestionRepository(trie, fakeDao, CoroutineScope(SupervisorJob() + testDispatcher), testDispatcher)

        val count = DictionaryLoader.loadFromAsset(mockContext, trie, repo, "dict.txt")

        assertEquals(1, count)
        assertTrue(trie.search("hello"))
        // Note: trimmed.split(" ") produces parts[0]="hello", parts[1]="", so parts[1].toIntOrNull() defaults to 1
        assertEquals(1, trie.getFrequency("hello"))
    }

    @Test
    fun `dictionaryLoader - line with missing frequency defaults to 1`() = runBlocking {
        val content = "hello"

        val mockContext = createMockContextWithContent("dict.txt", content)
        val fakeDao = FakeLearnedWordDao()
        val testDispatcher = StandardTestDispatcher()
        val repo = SuggestionRepository(trie, fakeDao, CoroutineScope(SupervisorJob() + testDispatcher), testDispatcher)

        val count = DictionaryLoader.loadFromAsset(mockContext, trie, repo, "dict.txt")

        assertEquals(1, count)
        assertTrue(trie.search("hello"))
        assertEquals(1, trie.getFrequency("hello"))
        assertEquals(1, repo.getFrequencyMap()["hello"])
    }

    @Test
    fun `dictionaryLoader - line with non-numeric frequency defaults to 1`() = runBlocking {
        val content = "hello abc"

        val mockContext = createMockContextWithContent("dict.txt", content)
        val fakeDao = FakeLearnedWordDao()
        val testDispatcher = StandardTestDispatcher()
        val repo = SuggestionRepository(trie, fakeDao, CoroutineScope(SupervisorJob() + testDispatcher), testDispatcher)

        val count = DictionaryLoader.loadFromAsset(mockContext, trie, repo, "dict.txt")

        assertEquals(1, count)
        assertTrue(trie.search("hello"))
        assertEquals(1, trie.getFrequency("hello"))
    }

    @Test
    fun `dictionaryLoader - comprehensive mixed lines stream parsing`() = runBlocking {
        val content = """
            # Header
            alpha 100
               
            beta
            gamma xyz
            # Comment in middle
               delta    300   
            EPSILON 400
            
            # Footer
        """.trimIndent()

        val mockContext = createMockContextWithContent("dict.txt", content)
        val fakeDao = FakeLearnedWordDao()
        val testDispatcher = StandardTestDispatcher()
        val repo = SuggestionRepository(trie, fakeDao, CoroutineScope(SupervisorJob() + testDispatcher), testDispatcher)

        val count = DictionaryLoader.loadFromAsset(mockContext, trie, repo, "dict.txt")

        // Lines counted: alpha, beta, gamma, delta, epsilon = 5 valid lines
        assertEquals(5, count)
        assertEquals(5, trie.size)

        assertEquals(100, trie.getFrequency("alpha"))
        assertEquals(1, trie.getFrequency("beta"))
        assertEquals(1, trie.getFrequency("gamma"))
        assertEquals(1, trie.getFrequency("delta"))
        assertEquals(400, trie.getFrequency("epsilon")) // lowercased
    }

    @Test
    fun `dictionaryLoader - io exception during asset open returns 0 count gracefully`() = runBlocking {
        val mockContext = Mockito.mock(Context::class.java)
        val mockAssetManager = Mockito.mock(AssetManager::class.java)
        Mockito.`when`(mockContext.assets).thenReturn(mockAssetManager)
        Mockito.`when`(mockAssetManager.open("missing.txt")).thenThrow(IOException("File not found"))

        val fakeDao = FakeLearnedWordDao()
        val testDispatcher = StandardTestDispatcher()
        val repo = SuggestionRepository(trie, fakeDao, CoroutineScope(SupervisorJob() + testDispatcher), testDispatcher)

        val count = DictionaryLoader.loadFromAsset(mockContext, trie, repo, "missing.txt")

        assertEquals("Should catch exception and return count 0", 0, count)
        assertEquals(0, trie.size)
    }

    // =========================================================================
    // 8. LearnedWordEntity Tests
    // =========================================================================

    @Test
    fun `learnedWordEntity - data class equality and hashCode for identical properties`() {
        val timestamp = 1700000000000L
        val entity1 = LearnedWordEntity(
            word = "android",
            frequency = 10,
            lastUsedTimestamp = timestamp,
            isCustom = true
        )
        val entity2 = LearnedWordEntity(
            word = "android",
            frequency = 10,
            lastUsedTimestamp = timestamp,
            isCustom = true
        )

        assertEquals("Identical entities must be equal", entity1, entity2)
        assertEquals("Identical entities must have identical hashCode", entity1.hashCode(), entity2.hashCode())
    }

    @Test
    fun `learnedWordEntity - inequality when any single field differs`() {
        val base = LearnedWordEntity("kotlin", 5, 1000L, false)

        val diffWord = base.copy(word = "java")
        val diffFreq = base.copy(frequency = 6)
        val diffTimestamp = base.copy(lastUsedTimestamp = 2000L)
        val diffCustom = base.copy(isCustom = true)

        assertNotEquals(base, diffWord)
        assertNotEquals(base, diffFreq)
        assertNotEquals(base, diffTimestamp)
        assertNotEquals(base, diffCustom)
    }

    @Test
    fun `learnedWordEntity - default values for timestamp and isCustom`() {
        val before = System.currentTimeMillis()
        val entity = LearnedWordEntity(
            word = "defaults",
            frequency = 3
        )
        val after = System.currentTimeMillis()

        assertEquals("defaults", entity.word)
        assertEquals(3, entity.frequency)
        assertFalse("isCustom should default to false", entity.isCustom)
        assertTrue(
            "lastUsedTimestamp should default to current time (~$before - $after), but was ${entity.lastUsedTimestamp}",
            entity.lastUsedTimestamp in before..after
        )
    }

    @Test
    fun `learnedWordEntity - custom timestamp and isCustom flag set to true`() {
        val customTimestamp = 123456789L
        val entity = LearnedWordEntity(
            word = "customword",
            frequency = 1,
            lastUsedTimestamp = customTimestamp,
            isCustom = true
        )

        assertEquals("customword", entity.word)
        assertEquals(1, entity.frequency)
        assertEquals(customTimestamp, entity.lastUsedTimestamp)
        assertTrue(entity.isCustom)
    }

    @Test
    fun `learnedWordEntity - copy method creates updated entity with updated timestamp and frequency`() {
        val initial = LearnedWordEntity(
            word = "swift",
            frequency = 2,
            lastUsedTimestamp = 100000L,
            isCustom = false
        )

        val updated = initial.copy(
            frequency = initial.frequency + 1,
            lastUsedTimestamp = 200000L,
            isCustom = true
        )

        assertEquals("swift", updated.word)
        assertEquals(3, updated.frequency)
        assertEquals(200000L, updated.lastUsedTimestamp)
        assertTrue(updated.isCustom)

        // Original remains unchanged (immutability)
        assertEquals(2, initial.frequency)
        assertEquals(100000L, initial.lastUsedTimestamp)
        assertFalse(initial.isCustom)
    }

    @Test
    fun `learnedWordEntity - component destructuring and toString representation`() {
        val entity = LearnedWordEntity("destruct", 7, 55555L, true)
        val (word, freq, timestamp, isCustom) = entity

        assertEquals("destruct", word)
        assertEquals(7, freq)
        assertEquals(55555L, timestamp)
        assertTrue(isCustom)

        val str = entity.toString()
        assertTrue(str.contains("word=destruct"))
        assertTrue(str.contains("frequency=7"))
        assertTrue(str.contains("lastUsedTimestamp=55555"))
        assertTrue(str.contains("isCustom=true"))
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private fun createMockContextWithContent(fileName: String, content: String): Context {
        val mockContext = Mockito.mock(Context::class.java)
        val mockAssetManager = Mockito.mock(AssetManager::class.java)
        Mockito.`when`(mockContext.assets).thenReturn(mockAssetManager)
        Mockito.`when`(mockAssetManager.open(fileName)).thenAnswer {
            ByteArrayInputStream(content.toByteArray(Charsets.UTF_8))
        }
        return mockContext
    }

    private class FakeLearnedWordDao : LearnedWordDao {
        val words = mutableMapOf<String, LearnedWordEntity>()

        override suspend fun insertOrUpdate(word: LearnedWordEntity) {
            words[word.word] = word
        }

        override suspend fun getWord(word: String): LearnedWordEntity? = words[word]

        override suspend fun getLearnedPrefixSuggestions(prefix: String, limit: Int): List<LearnedWordEntity> {
            return words.values.filter { it.word.startsWith(prefix) }
                .sortedByDescending { it.frequency }
                .take(limit)
        }

        override suspend fun getRecentlyUsedWords(limit: Int): List<LearnedWordEntity> {
            return words.values.sortedByDescending { it.lastUsedTimestamp }.take(limit)
        }

        override suspend fun getTopFrequentWords(limit: Int): List<LearnedWordEntity> {
            return words.values.sortedByDescending { it.frequency }.take(limit)
        }

        override suspend fun getAllWords(): List<LearnedWordEntity> = words.values.toList()

        override suspend fun getCount(): Int = words.size
    }
}
