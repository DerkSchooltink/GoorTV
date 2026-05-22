package dev.goor.tv.ui.screens.guide

import dev.goor.tv.data.db.dao.ChannelDao
import dev.goor.tv.data.db.dao.ProgrammeDao
import dev.goor.tv.data.db.dao.SourceDao
import dev.goor.tv.data.model.Source
import dev.goor.tv.data.model.SourceType
import dev.goor.tv.util.FakeTimeProvider
import dev.goor.tv.util.MainDispatcherRule
import dev.goor.tv.util.testChannel
import dev.goor.tv.util.testProgramme
import dev.goor.tv.util.testSource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GuideViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sourceDao = mockk<SourceDao>()
    private val channelDao = mockk<ChannelDao>()
    private val programmeDao = mockk<ProgrammeDao>()

    private fun makeVm() = GuideViewModel(sourceDao, channelDao, programmeDao, FakeTimeProvider())

    /** Eligible M3U source toggling on three knobs the reducer cares about. */
    private fun eligibleSource(
        id: Long = 1L,
        name: String = "Source",
        synced: Boolean = true,
        error: String? = null,
    ): Source = testSource(
        id = id,
        name = name,
        type = SourceType.M3U,
        epgUrl = "http://example.com/epg.xml",
        lastEpgSyncedAt = if (synced) 1L else null,
        epgLastError = error,
    )

    private fun stubSources(sources: List<Source>) {
        every { sourceDao.getAll() } returns flowOf(sources)
    }

    private fun stubChannelsAndProgrammes(
        channels: List<dev.goor.tv.data.model.Channel> = emptyList(),
        programmes: List<dev.goor.tv.data.model.Programme> = emptyList(),
    ) {
        every { channelDao.getVisibleWithTvgId() } returns flowOf(channels)
        every { programmeDao.observeWindowForChannels(any(), any(), any(), any()) } returns flowOf(programmes)
    }

    @Test
    fun `state is Empty NoSources when no source is EPG-eligible`() = runTest {
        stubSources(listOf(testSource(type = SourceType.M3U, epgUrl = null)))
        stubChannelsAndProgrammes()

        val vm = makeVm()
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()

        assertEquals(GuideState.Empty(GuideEmptyReason.NoSources), vm.state.value)
    }

    @Test
    fun `state is Empty Fetching when eligible source has not synced yet and no error`() = runTest {
        stubSources(listOf(eligibleSource(synced = false)))
        stubChannelsAndProgrammes()

        val vm = makeVm()
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()

        assertEquals(GuideState.Empty(GuideEmptyReason.Fetching), vm.state.value)
    }

    @Test
    fun `state is Empty EpgError when first-ever sync errored`() = runTest {
        stubSources(listOf(eligibleSource(name = "Provider X", synced = false, error = "HTTP 404")))
        stubChannelsAndProgrammes()

        val vm = makeVm()
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()

        assertEquals(
            GuideState.Empty(GuideEmptyReason.EpgError("Provider X", "HTTP 404")),
            vm.state.value,
        )
    }

    @Test
    fun `state is Empty NoTvgIds when sources synced but no channel has tvgChannelId`() = runTest {
        stubSources(listOf(eligibleSource()))
        // getVisibleWithTvgId() filters in SQL, so "no channels with tvg-id" presents as an empty list.
        stubChannelsAndProgrammes(channels = emptyList())

        val vm = makeVm()
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()

        assertEquals(GuideState.Empty(GuideEmptyReason.NoTvgIds), vm.state.value)
    }

    @Test
    fun `state is Empty NoProgrammes when channels have tvgIds but window has no matches`() = runTest {
        stubSources(listOf(eligibleSource()))
        stubChannelsAndProgrammes(channels = listOf(testChannel(id = 1L, sourceId = 1L, tvgChannelId = "a.tv")))

        val vm = makeVm()
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()

        assertEquals(GuideState.Empty(GuideEmptyReason.NoProgrammes), vm.state.value)
    }

    @Test
    fun `state prefers EpgError over NoProgrammes when sync failed after a success`() = runTest {
        stubSources(listOf(eligibleSource(name = "Provider Y", synced = true, error = "Timeout")))
        stubChannelsAndProgrammes(channels = listOf(testChannel(id = 1L, sourceId = 1L, tvgChannelId = "a.tv")))

        val vm = makeVm()
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()

        assertEquals(
            GuideState.Empty(GuideEmptyReason.EpgError("Provider Y", "Timeout")),
            vm.state.value,
        )
    }

    @Test
    fun `state is Ready when at least one row carries programmes`() = runTest {
        stubSources(listOf(eligibleSource()))
        stubChannelsAndProgrammes(
            channels = listOf(testChannel(id = 1L, sourceId = 1L, tvgChannelId = "a.tv")),
            programmes = listOf(testProgramme(sourceId = 1L, tvgChannelId = "a.tv")),
        )

        val vm = makeVm()
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()

        val state = vm.state.value
        assertTrue(state is GuideState.Ready)
        assertEquals(1, (state as GuideState.Ready).rows.size)
    }

    @Test
    fun `Ready groups programmes onto matching channel rows`() = runTest {
        stubSources(listOf(eligibleSource()))
        val a = testChannel(id = 1L, sourceId = 1L, tvgChannelId = "a.tv", name = "A")
        val b = testChannel(id = 2L, sourceId = 1L, tvgChannelId = "b.tv", name = "B")
        val pa = testProgramme(sourceId = 1L, tvgChannelId = "a.tv", title = "Showa")
        val pb1 = testProgramme(sourceId = 1L, tvgChannelId = "b.tv", title = "Showb1")
        val pb2 = testProgramme(sourceId = 1L, tvgChannelId = "b.tv", startMs = 7_200_000L, endMs = 10_800_000L, title = "Showb2")
        stubChannelsAndProgrammes(channels = listOf(a, b), programmes = listOf(pa, pb1, pb2))

        val vm = makeVm()
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()

        val rowsByName = (vm.state.value as GuideState.Ready).rows.associateBy { it.channel.name }
        assertEquals(listOf("Showa"), rowsByName["A"]?.programmes?.map { it.title })
        assertEquals(listOf("Showb1", "Showb2"), rowsByName["B"]?.programmes?.map { it.title })
    }

    @Test
    fun `window is slot-aligned — minute ticks within the same slot don't change windowStartMs`() = runTest {
        stubSources(emptyList())
        stubChannelsAndProgrammes()
        val slotMs = GuideViewModel.SLOT_MS
        // Start at an arbitrary offset inside a slot (slot boundary + 7 min).
        val slotBoundary = 10L * slotMs
        val clock = FakeTimeProvider(initial = slotBoundary + 7L * 60_000L)
        val vm = GuideViewModel(sourceDao, channelDao, programmeDao, clock)
        backgroundScope.launch { vm.windowStartMs.collect {} }
        backgroundScope.launch { vm.windowEndMs.collect {} }
        runCurrent()

        val startBefore = vm.windowStartMs.value
        val endBefore = vm.windowEndMs.value
        // Tick by 1 min — still inside the same 30-min slot.
        clock.tick(slotBoundary + 8L * 60_000L)
        runCurrent()

        assertEquals(startBefore, vm.windowStartMs.value)
        assertEquals(endBefore, vm.windowEndMs.value)
        // And the window really is aligned to the slot floor.
        assertEquals(0L, vm.windowStartMs.value % slotMs)
    }

    @Test
    fun `window shifts when the clock crosses a slot boundary`() = runTest {
        stubSources(emptyList())
        stubChannelsAndProgrammes()
        val slotMs = GuideViewModel.SLOT_MS
        val slotBoundary = 10L * slotMs
        val clock = FakeTimeProvider(initial = slotBoundary + 25L * 60_000L)
        val vm = GuideViewModel(sourceDao, channelDao, programmeDao, clock)
        backgroundScope.launch { vm.windowStartMs.collect {} }
        runCurrent()

        val startBefore = vm.windowStartMs.value
        // Tick past the next slot boundary.
        clock.tick(slotBoundary + 32L * 60_000L)
        runCurrent()

        assertEquals(startBefore + slotMs, vm.windowStartMs.value)
    }

    @Test
    fun `rapid per-source programme bursts collapse to a single Ready emission`() = runTest {
        // Two sources, two channels — set up so the inner combine fans-in across both.
        stubSources(listOf(eligibleSource(id = 1L), eligibleSource(id = 2L)))
        val chA = testChannel(id = 1L, sourceId = 1L, tvgChannelId = "a.tv", name = "A")
        val chB = testChannel(id = 2L, sourceId = 2L, tvgChannelId = "b.tv", name = "B")
        every { channelDao.getVisibleWithTvgId() } returns flowOf(listOf(chA, chB))

        val progsA = MutableStateFlow(listOf(testProgramme(sourceId = 1L, tvgChannelId = "a.tv", title = "A1")))
        val progsB = MutableStateFlow(listOf(testProgramme(sourceId = 2L, tvgChannelId = "b.tv", title = "B1")))
        every { programmeDao.observeWindowForChannels(1L, any(), any(), any()) } returns progsA
        every { programmeDao.observeWindowForChannels(2L, any(), any(), any()) } returns progsB

        val vm = makeVm()
        val readyTitles = mutableListOf<List<String>>()
        backgroundScope.launch {
            vm.state.collect { s ->
                if (s is GuideState.Ready) {
                    readyTitles += s.rows.flatMap { it.programmes.map { p -> p.title } }
                }
            }
        }
        runCurrent()
        advanceTimeBy(200) // let the initial debounce window settle
        runCurrent()
        val baseline = readyTitles.size

        // Burst: 4 rapid mutations across both sources within one debounce window.
        progsA.value = listOf(testProgramme(sourceId = 1L, tvgChannelId = "a.tv", title = "A2"))
        progsB.value = listOf(testProgramme(sourceId = 2L, tvgChannelId = "b.tv", title = "B2"))
        progsA.value = listOf(testProgramme(sourceId = 1L, tvgChannelId = "a.tv", title = "A3"))
        progsB.value = listOf(testProgramme(sourceId = 2L, tvgChannelId = "b.tv", title = "B3"))
        // Burst happens inside the debounce window — no Ready emission yet.
        advanceTimeBy(50)
        runCurrent()
        assertEquals(baseline, readyTitles.size)

        // Let the debounce expire — exactly one new Ready emission with the latest data.
        advanceTimeBy(100)
        runCurrent()
        assertEquals(baseline + 1, readyTitles.size)
        assertEquals(listOf("A3", "B3"), readyTitles.last())
    }

    @Test
    fun `Fetching transitions to Ready when source completes sync`() = runTest {
        val sources = MutableStateFlow(listOf(eligibleSource(synced = false)))
        every { sourceDao.getAll() } returns sources
        stubChannelsAndProgrammes(
            channels = listOf(testChannel(id = 1L, sourceId = 1L, tvgChannelId = "a.tv")),
            programmes = listOf(testProgramme(sourceId = 1L, tvgChannelId = "a.tv")),
        )

        val vm = makeVm()
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        assertEquals(GuideState.Empty(GuideEmptyReason.Fetching), vm.state.value)

        sources.value = listOf(eligibleSource(synced = true))
        runCurrent()

        assertTrue(vm.state.value is GuideState.Ready)
    }
}
