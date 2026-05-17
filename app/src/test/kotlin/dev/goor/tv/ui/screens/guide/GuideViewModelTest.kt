package dev.goor.tv.ui.screens.guide

import dev.goor.tv.data.db.dao.ChannelDao
import dev.goor.tv.data.db.dao.ProgrammeDao
import dev.goor.tv.data.db.dao.SourceDao
import dev.goor.tv.data.model.SourceType
import dev.goor.tv.util.MainDispatcherRule
import dev.goor.tv.util.testChannel
import dev.goor.tv.util.testProgramme
import dev.goor.tv.util.testSource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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

    private fun makeVm() = GuideViewModel(sourceDao, channelDao, programmeDao)

    private fun stubSources(sources: List<dev.goor.tv.data.model.Source>) {
        every { sourceDao.getAll() } returns flowOf(sources)
    }

    @Test
    fun `rows include only channels with a tvgChannelId`() = runTest {
        stubSources(listOf(testSource(epgUrl = "http://x/epg", lastEpgSyncedAt = 1L)))
        val withId = testChannel(id = 1L, sourceId = 1L, tvgChannelId = "bbc.uk")
        val withoutId = testChannel(id = 2L, sourceId = 1L, tvgChannelId = null)
        every { channelDao.getAllVisible() } returns flowOf(listOf(withId, withoutId))
        every { programmeDao.observeWindowAll(any(), any()) } returns flowOf(emptyList())

        val vm = makeVm()
        backgroundScope.launch { vm.rows.collect {} }
        runCurrent()

        assertEquals(1, vm.rows.value.size)
        assertEquals("bbc.uk", vm.rows.value[0].channel.tvgChannelId)
    }

    @Test
    fun `programmes are grouped onto the matching channel row`() = runTest {
        stubSources(listOf(testSource(epgUrl = "http://x/epg", lastEpgSyncedAt = 1L)))
        val a = testChannel(id = 1L, sourceId = 1L, tvgChannelId = "a.tv", name = "A")
        val b = testChannel(id = 2L, sourceId = 1L, tvgChannelId = "b.tv", name = "B")
        val pa = testProgramme(sourceId = 1L, tvgChannelId = "a.tv", title = "Showa")
        val pb1 = testProgramme(sourceId = 1L, tvgChannelId = "b.tv", title = "Showb1")
        val pb2 = testProgramme(sourceId = 1L, tvgChannelId = "b.tv", startMs = 7_200_000L, endMs = 10_800_000L, title = "Showb2")
        every { channelDao.getAllVisible() } returns flowOf(listOf(a, b))
        every { programmeDao.observeWindowAll(any(), any()) } returns flowOf(listOf(pa, pb1, pb2))

        val vm = makeVm()
        backgroundScope.launch { vm.rows.collect {} }
        runCurrent()

        val rowsByName = vm.rows.value.associateBy { it.channel.name }
        assertEquals(listOf("Showa"), rowsByName["A"]?.programmes?.map { it.title })
        assertEquals(listOf("Showb1", "Showb2"), rowsByName["B"]?.programmes?.map { it.title })
    }

    @Test
    fun `state is Empty NoSources when no source is EPG-eligible`() = runTest {
        // M3U without epgUrl is ineligible
        stubSources(listOf(testSource(type = SourceType.M3U, epgUrl = null)))
        every { channelDao.getAllVisible() } returns flowOf(emptyList())
        every { programmeDao.observeWindowAll(any(), any()) } returns flowOf(emptyList())

        val vm = makeVm()
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()

        assertEquals(GuideState.Empty(GuideEmptyReason.NoSources), vm.state.value)
    }

    @Test
    fun `state is Empty Fetching when eligible source has not synced yet`() = runTest {
        stubSources(listOf(testSource(epgUrl = "http://x/epg", lastEpgSyncedAt = null)))
        every { channelDao.getAllVisible() } returns flowOf(emptyList())
        every { programmeDao.observeWindowAll(any(), any()) } returns flowOf(emptyList())

        val vm = makeVm()
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()

        assertEquals(GuideState.Empty(GuideEmptyReason.Fetching), vm.state.value)
    }

    @Test
    fun `state is Empty NoTvgIds when sources synced but no channel has tvgChannelId`() = runTest {
        stubSources(listOf(testSource(epgUrl = "http://x/epg", lastEpgSyncedAt = 1L)))
        every { channelDao.getAllVisible() } returns flowOf(listOf(
            testChannel(id = 1L, tvgChannelId = null),
        ))
        every { programmeDao.observeWindowAll(any(), any()) } returns flowOf(emptyList())

        val vm = makeVm()
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()

        assertEquals(GuideState.Empty(GuideEmptyReason.NoTvgIds), vm.state.value)
    }

    @Test
    fun `state is Empty NoProgrammes when channels have tvgIds but window has no matches`() = runTest {
        stubSources(listOf(testSource(epgUrl = "http://x/epg", lastEpgSyncedAt = 1L)))
        every { channelDao.getAllVisible() } returns flowOf(listOf(
            testChannel(id = 1L, sourceId = 1L, tvgChannelId = "a.tv"),
        ))
        every { programmeDao.observeWindowAll(any(), any()) } returns flowOf(emptyList())

        val vm = makeVm()
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()

        assertEquals(GuideState.Empty(GuideEmptyReason.NoProgrammes), vm.state.value)
    }

    @Test
    fun `state is Ready when at least one row carries programmes`() = runTest {
        stubSources(listOf(testSource(epgUrl = "http://x/epg", lastEpgSyncedAt = 1L)))
        val ch = testChannel(id = 1L, sourceId = 1L, tvgChannelId = "a.tv")
        val p = testProgramme(sourceId = 1L, tvgChannelId = "a.tv")
        every { channelDao.getAllVisible() } returns flowOf(listOf(ch))
        every { programmeDao.observeWindowAll(any(), any()) } returns flowOf(listOf(p))

        val vm = makeVm()
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()

        val state = vm.state.value
        assertTrue(state is GuideState.Ready)
        assertEquals(1, (state as GuideState.Ready).rows.size)
    }
}
