package dev.goor.tv.data.preferences

import androidx.annotation.StringRes
import dev.goor.tv.R

enum class SortOrder(@StringRes val displayNameRes: Int) {
    BY_GROUP(R.string.home_sort_by_group),
    BY_NAME(R.string.home_sort_by_name),
    BY_LAST_WATCHED(R.string.home_sort_by_last_watched),
}
