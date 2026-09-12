package com.github.andreyasadchy.xtra.ui.stats

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.FragmentStatsBinding
import com.github.andreyasadchy.xtra.model.stats.CategoryWatchTime
import com.github.andreyasadchy.xtra.model.stats.HourlyWatchTime
import com.github.andreyasadchy.xtra.model.stats.ScreenTime
import com.github.andreyasadchy.xtra.model.stats.StreamWatchStats
import com.github.andreyasadchy.xtra.model.stats.StreamerLoyalty
import com.github.andreyasadchy.xtra.model.stats.WatchStreak
import com.github.andreyasadchy.xtra.ui.adaptive.AdaptiveWindowInfo
import com.github.andreyasadchy.xtra.ui.adaptive.WidthTier
import com.github.andreyasadchy.xtra.ui.common.Scrollable
import com.github.andreyasadchy.xtra.ui.login.LoginActivity
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.settings.SettingsActivity
import com.github.andreyasadchy.xtra.ui.view.DailyBarChartView
import com.github.andreyasadchy.xtra.ui.view.DashboardSpacingItemDecoration
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.tokenPrefs
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@AndroidEntryPoint
class StatsFragment : Fragment(R.layout.fragment_stats), Scrollable {

    private val viewModel: StatsViewModel by viewModels()
    private var binding: FragmentStatsBinding? = null
    private lateinit var dashboardAdapter: StatsDashboardAdapter
    private lateinit var widthTier: WidthTier
    private var statsScrollListener: RecyclerView.OnScrollListener? = null
    private var statsLayoutChangeListener: View.OnLayoutChangeListener? = null

    private var screenTimeCard = StatsDashboardItem.ScreenTime(
        chartData = emptyList(),
        dailyAverageText = "0m",
        weekChangeText = "",
        todayTimeText = "0m",
        rangeTotalLabelText = "",
        weekTotalText = "0m",
    )
    private var streakCard = StatsDashboardItem.Streak(
        currentStreakText = "0",
        longestStreakText = "0",
    )
    private var categoriesCard = StatsDashboardItem.Categories(emptyList())
    private var heatmapCard = StatsDashboardItem.Heatmap(emptyList())
    private var favoriteChannelsCard = StatsDashboardItem.FavoriteChannels(emptyList())
    private var topStreams: List<StreamWatchStats> = emptyList()
    private var streamerLoyalty: List<StreamerLoyalty> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentStatsBinding.bind(view)
        this.binding = binding

        configureToolbar(binding)
        initializeDefaultCards()
        configureDashboard(binding)
        renderDashboard()
        viewModel.refresh()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.screenTime.collectLatest { screenTimes ->
                        screenTimeCard = buildScreenTimeCard(screenTimes, viewModel.timeRange.value)
                        renderDashboard()
                    }
                }
                launch {
                    viewModel.topStreams.collectLatest { streams ->
                        topStreams = streams
                        favoriteChannelsCard = buildFavoriteChannelsCard()
                        renderDashboard()
                    }
                }
                launch {
                    viewModel.watchStreak.collectLatest { streak ->
                        streakCard = buildStreakCard(streak)
                        renderDashboard()
                    }
                }
                launch {
                    viewModel.categoryBreakdown.collectLatest { categories ->
                        categoriesCard = StatsDashboardItem.Categories(categories)
                        renderDashboard()
                    }
                }
                launch {
                    viewModel.hourlyBreakdown.collectLatest { hourly ->
                        heatmapCard = StatsDashboardItem.Heatmap(hourly)
                        renderDashboard()
                    }
                }
                launch {
                    viewModel.streamerLoyalty.collectLatest { loyalty ->
                        streamerLoyalty = loyalty
                        favoriteChannelsCard = buildFavoriteChannelsCard()
                        renderDashboard()
                    }
                }
                launch {
                    viewModel.timeRange.collectLatest { timeRange ->
                        binding.syncRangeChip(timeRange)
                        screenTimeCard = buildScreenTimeCard(viewModel.screenTime.value, timeRange)
                        favoriteChannelsCard = buildFavoriteChannelsCard()
                        renderDashboard()
                    }
                }
            }
        }
    }

    private fun configureToolbar(binding: FragmentStatsBinding) {
        val activity = requireActivity() as MainActivity
        val isLoggedIn = !TwitchApiHelper.getGQLHeaders(requireContext(), true)[C.HEADER_TOKEN].isNullOrBlank() ||
            !TwitchApiHelper.getHelixHeaders(requireContext())[C.HEADER_TOKEN].isNullOrBlank()
        val navController = findNavController()
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.rootGamesFragment,
                R.id.rootTopFragment,
                R.id.followPagerFragment,
                R.id.followMediaFragment,
                R.id.savedPagerFragment,
                R.id.savedMediaFragment,
                R.id.statsFragment,
            ),
        )

        with(binding) {
            toolbar.setupWithNavController(navController, appBarConfiguration)
            toolbar.menu.findItem(R.id.login).title = if (isLoggedIn) getString(R.string.log_out) else getString(R.string.log_in)
            toolbar.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.settings -> {
                        activity.settingsResultLauncher?.launch(Intent(activity, SettingsActivity::class.java))
                        true
                    }
                    R.id.login -> {
                        if (isLoggedIn) {
                            activity.getAlertDialogBuilder().apply {
                                setTitle(getString(R.string.logout_title))
                                requireContext().tokenPrefs().getString(C.USERNAME, null)?.let {
                                    setMessage(getString(R.string.logout_msg, it))
                                }
                                setNegativeButton(getString(R.string.no), null)
                                setPositiveButton(getString(R.string.yes)) { _, _ ->
                                    activity.logoutResultLauncher?.launch(Intent(activity, LoginActivity::class.java))
                                }
                            }.show()
                        } else {
                            activity.loginResultLauncher?.launch(Intent(activity, LoginActivity::class.java))
                        }
                        true
                    }
                    else -> false
                }
            }
            range7Days.setOnClickListener { viewModel.setTimeRange(StatsTimeRange.LAST_7_DAYS) }
            range30Days.setOnClickListener { viewModel.setTimeRange(StatsTimeRange.LAST_30_DAYS) }
            rangeAllTime.setOnClickListener { viewModel.setTimeRange(StatsTimeRange.ALL_TIME) }

            if (requireContext().prefs().getBoolean(C.UI_THEME_APPBAR_LIFT, true)) {
                appBar.setLiftOnScrollTargetView(statsRecyclerView)
                val scrollListener = object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        super.onScrolled(recyclerView, dx, dy)
                        appBar.isLifted = recyclerView.canScrollVertically(-1)
                    }
                }
                statsScrollListener = scrollListener
                statsRecyclerView.addOnScrollListener(scrollListener)
                val layoutChangeListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                    appBar.isLifted = statsRecyclerView.canScrollVertically(-1)
                }
                statsLayoutChangeListener = layoutChangeListener
                statsRecyclerView.addOnLayoutChangeListener(layoutChangeListener)
            } else {
                appBar.setLiftable(false)
                appBar.background = null
            }

            ViewCompat.setOnApplyWindowInsetsListener(root) { _, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                toolbar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    topMargin = insets.top
                }
                if (activity.findViewById<LinearLayout>(R.id.navBarContainer)?.isVisible == false) {
                    val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                    statsRecyclerView.updatePadding(bottom = resources.getDimensionPixelSize(R.dimen.stats_page_padding) + systemBars.bottom)
                }
                WindowInsetsCompat.CONSUMED
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        binding?.let { binding ->
            configureDashboard(binding)
            renderDashboard()
        }
    }

    private fun renderDashboard() {
        dashboardAdapter.submitList(
            listOf(
                screenTimeCard,
                streakCard,
                categoriesCard,
                heatmapCard,
                favoriteChannelsCard,
            ),
        )
    }

    private fun configureDashboard(binding: FragmentStatsBinding) {
        widthTier = AdaptiveWindowInfo.widthTierFor(requireContext())
        dashboardAdapter = StatsDashboardAdapter()

        val configuration = resources.configuration
        val spanCount = StatsDashboardSpanPolicy.spanCountFor(
            widthTier = widthTier,
            isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
            screenHeightDp = configuration.screenHeightDp,
        )

        binding.statsRecyclerView.layoutManager = GridLayoutManager(requireContext(), spanCount).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    val item = dashboardAdapter.currentList.getOrNull(position) ?: return spanCount
                    return StatsDashboardSpanPolicy.spanSizeFor(widthTier, item.cardType, spanCount)
                }
            }
        }
        binding.statsRecyclerView.adapter = dashboardAdapter
        (binding.statsRecyclerView.itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)
            ?.supportsChangeAnimations = false
        binding.statsRecyclerView.setHasFixedSize(false)
        replaceDashboardSpacingDecoration(binding.statsRecyclerView)
    }

    private fun replaceDashboardSpacingDecoration(recyclerView: RecyclerView) {
        while (recyclerView.itemDecorationCount > 0) {
            recyclerView.removeItemDecorationAt(0)
        }
        recyclerView.addItemDecoration(
            DashboardSpacingItemDecoration(
                resources.getDimensionPixelSize(R.dimen.stats_dashboard_item_spacing),
            ),
        )
    }

    private fun initializeDefaultCards() {
        screenTimeCard = StatsDashboardItem.ScreenTime(
            chartData = emptyList(),
            dailyAverageText = "0m",
            weekChangeText = getString(R.string.stats_week_trend_none),
            todayTimeText = "0m",
            rangeTotalLabelText = getString(R.string.last_7_days),
            weekTotalText = "0m",
        )
        streakCard = StatsDashboardItem.Streak("0", "0")
        categoriesCard = StatsDashboardItem.Categories(emptyList())
        heatmapCard = StatsDashboardItem.Heatmap(emptyList())
        favoriteChannelsCard = StatsDashboardItem.FavoriteChannels(emptyList())
        topStreams = emptyList()
        streamerLoyalty = emptyList()
    }

    private fun buildFavoriteChannelsCard(): StatsDashboardItem.FavoriteChannels {
        if (viewModel.timeRange.value != StatsTimeRange.ALL_TIME) {
            val maxWatchSeconds = streamerLoyalty.maxOfOrNull { it.totalWatchSeconds } ?: 0L
            return StatsDashboardItem.FavoriteChannels(
                channels = streamerLoyalty.mapNotNull { loyalty ->
                    val channelId = loyalty.channelId ?: return@mapNotNull null
                    val channelName = loyalty.channelName ?: loyalty.channelLogin ?: return@mapNotNull null
                    FavoriteChannelRow(
                        channelId = channelId,
                        channelName = channelName,
                        totalSecondsWatched = loyalty.totalWatchSeconds,
                        sessionCount = loyalty.sessionCount,
                        loyaltyScore = loyalty.loyaltyScore.toInt(),
                        watchTimeProgress = if (maxWatchSeconds > 0L) {
                            loyalty.totalWatchSeconds.toFloat() / maxWatchSeconds.toFloat()
                        } else {
                            0f
                        },
                    )
                },
            )
        }
        return StatsDashboardItem.FavoriteChannels(
            channels = StatsDataHelper.buildFavoriteChannels(
                topStreams = topStreams,
                loyalty = streamerLoyalty,
            ),
        )
    }

    private fun buildScreenTimeCard(screenTimes: List<ScreenTime>, timeRange: StatsTimeRange): StatsDashboardItem.ScreenTime {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = sdf.format(Date())
        val timeMap = screenTimes.associateBy { it.date }
        val calendar = Calendar.getInstance()
        val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val chartData = mutableListOf<DailyBarChartView.DayData>()

        val days = timeRange.days
        var rangeTotalSeconds = 0L
        val rangeDays = days ?: screenTimes.map { it.date }.distinct().size.coerceAtLeast(1)

        if (days != null) {
            calendar.add(Calendar.DAY_OF_YEAR, -(days - 1))
            repeat(days) { index ->
                val dateStr = sdf.format(calendar.time)
                val seconds = timeMap[dateStr]?.totalSeconds ?: 0L
                rangeTotalSeconds += seconds
                val label = buildChartLabel(dateStr, today, calendar, dayFormat, index, days, timeRange)
                chartData.add(DailyBarChartView.DayData(label, seconds))
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
        } else {
            screenTimes.sortedBy { it.date }.forEachIndexed { index, screenTime ->
                rangeTotalSeconds += screenTime.totalSeconds
                val parsedDate = runCatching { sdf.parse(screenTime.date) }.getOrNull()
                calendar.time = parsedDate ?: Date()
                chartData.add(
                    DailyBarChartView.DayData(
                        label = buildChartLabel(screenTime.date, today, calendar, dayFormat, index, screenTimes.size, timeRange),
                        seconds = screenTime.totalSeconds,
                    ),
                )
            }
        }

        var previousWeekTotalSeconds = 0L
        calendar.time = Date()
        calendar.add(Calendar.DAY_OF_YEAR, -13)

        repeat(7) {
            val dateStr = sdf.format(calendar.time)
            previousWeekTotalSeconds += timeMap[dateStr]?.totalSeconds ?: 0L
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        val avgSeconds = if (rangeDays > 0) rangeTotalSeconds / rangeDays else 0L
        val todaySeconds = timeMap[today]?.totalSeconds ?: 0L

        return StatsDashboardItem.ScreenTime(
            chartData = chartData,
            dailyAverageText = formatDurationShort(avgSeconds),
            weekChangeText = if (timeRange == StatsTimeRange.LAST_7_DAYS) {
                buildWeekTrendText(
                    currentWeekSeconds = rangeTotalSeconds,
                    previousWeekSeconds = previousWeekTotalSeconds,
                )
            } else {
                getRangeLabel(timeRange)
            },
            todayTimeText = formatDurationShort(todaySeconds),
            rangeTotalLabelText = getRangeLabel(timeRange),
            weekTotalText = formatDurationShort(rangeTotalSeconds),
        )
    }

    private fun buildChartLabel(
        date: String,
        today: String,
        calendar: Calendar,
        dayFormat: SimpleDateFormat,
        index: Int,
        count: Int,
        timeRange: StatsTimeRange,
    ): String {
        return when {
            count <= 7 && date == today -> getString(R.string.today)
            count <= 7 -> dayFormat.format(calendar.time)
            timeRange == StatsTimeRange.LAST_30_DAYS && (index == 0 || index == count / 2 || index == count - 1) ->
                SimpleDateFormat("M/d", Locale.getDefault()).format(calendar.time)
            timeRange == StatsTimeRange.ALL_TIME && (index == 0 || index == count / 2 || index == count - 1) ->
                SimpleDateFormat("M/d", Locale.getDefault()).format(calendar.time)
            else -> ""
        }
    }

    private fun getRangeLabel(timeRange: StatsTimeRange): String {
        return when (timeRange) {
            StatsTimeRange.LAST_7_DAYS -> getString(R.string.last_7_days)
            StatsTimeRange.LAST_30_DAYS -> getString(R.string.last_30_days)
            StatsTimeRange.ALL_TIME -> getString(R.string.stats_all_time)
        }
    }

    private fun FragmentStatsBinding.syncRangeChip(timeRange: StatsTimeRange) {
        val checkedButtonId = when (timeRange) {
            StatsTimeRange.LAST_7_DAYS -> R.id.range7Days
            StatsTimeRange.LAST_30_DAYS -> R.id.range30Days
            StatsTimeRange.ALL_TIME -> R.id.rangeAllTime
        }
        if (rangeButtonGroup.checkedButtonId != checkedButtonId) {
            rangeButtonGroup.check(checkedButtonId)
        }
    }

    private fun buildStreakCard(streak: WatchStreak?): StatsDashboardItem.Streak {
        return StatsDashboardItem.Streak(
            currentStreakText = (streak?.currentStreakDays ?: 0).toString(),
            longestStreakText = (streak?.longestStreakDays ?: 0).toString(),
        )
    }

    private fun buildWeekTrendText(currentWeekSeconds: Long, previousWeekSeconds: Long): String {
        return when {
            currentWeekSeconds == 0L && previousWeekSeconds == 0L ->
                getString(R.string.stats_week_trend_none)

            previousWeekSeconds == 0L ->
                getString(R.string.stats_week_trend_new)

            currentWeekSeconds == previousWeekSeconds ->
                getString(R.string.stats_week_trend_flat)

            currentWeekSeconds > previousWeekSeconds -> {
                val percentage = (
                    (currentWeekSeconds - previousWeekSeconds).toDouble() /
                        previousWeekSeconds * 100
                    ).roundToInt()
                getString(R.string.stats_week_trend_up, percentage)
            }

            else -> {
                val percentage = (
                    (previousWeekSeconds - currentWeekSeconds).toDouble() /
                        previousWeekSeconds * 100
                    ).roundToInt()
                getString(R.string.stats_week_trend_down, percentage)
            }
        }
    }

    private fun formatDurationShort(totalSeconds: Long): String {
        val safeSeconds = abs(totalSeconds)
        val hours = safeSeconds / 3600
        val minutes = (safeSeconds % 3600) / 60

        return buildString {
            if (hours > 0) append("${hours}h")
            if (hours > 0 && minutes > 0) append(" ")
            if (minutes > 0 || hours == 0L) append("${minutes}m")
        }.trim()
    }

    override fun scrollToTop() {
        binding?.let {
            it.appBar.setExpanded(true, true)
            it.statsRecyclerView.scrollToPosition(0)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding?.let { binding ->
            statsScrollListener?.let { binding.statsRecyclerView.removeOnScrollListener(it) }
            statsLayoutChangeListener?.let { binding.statsRecyclerView.removeOnLayoutChangeListener(it) }
            binding.range7Days.setOnClickListener(null)
            binding.range30Days.setOnClickListener(null)
            binding.rangeAllTime.setOnClickListener(null)
            binding.toolbar.setOnMenuItemClickListener(null)
            binding.statsRecyclerView.adapter = null
        }
        statsScrollListener = null
        statsLayoutChangeListener = null
        binding = null
    }
}
