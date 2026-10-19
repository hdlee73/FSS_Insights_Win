package io.github.hdlee73.financenewsradar.data

import android.content.Context
import io.github.hdlee73.financenewsradar.model.AgencyId
import io.github.hdlee73.financenewsradar.model.TimeRange
import io.github.hdlee73.financenewsradar.platform.Scheduler

/** 키워드별 새 기사 알림 방식. 데스크톱에서도 같은 주기로 확인한다. */
enum class KeywordAlertMode(val label: String) {
    OFF("끔"),
    HOURLY("1시간"),
    FAST("약 15분")
}

/** 알릴 항목 하나(기사 또는 보도자료). [link]가 같은 항목은 같은 키워드로 두 번 알리지 않는다. */
data class AlertHit(val title: String, val link: String, val source: String)

/** 키워드 알림의 판단 로직(네트워크·알림과 분리해 테스트한다). */
object KeywordAlertLogic {
    /**
     * [seen]이 null이면 이 키워드를 처음 보는 것이므로 지금 있는 항목을 '이미 본 것'으로만 기록하고 알리지 않는다
     * (키워드를 추가하거나 알림을 켠 순간 묵은 기사가 한꺼번에 울리지 않게).
     * 반환: (알릴 항목, 저장할 seen 목록). 확인에 실패해 [hits]가 null이면 아무것도 바꾸지 않는다.
     */
    fun diff(hits: List<AlertHit>?, seen: List<String>?): Pair<List<AlertHit>, List<String>?> {
        if (hits == null) return emptyList<AlertHit>() to seen
        val distinct = hits.distinctBy { it.link }
        if (seen == null) return emptyList<AlertHit>() to distinct.map { it.link }
        val known = seen.toHashSet()
        val fresh = distinct.filter { it.link !in known }
        return fresh to (seen + fresh.map { it.link })
    }

    /** 보도자료 제목이 키워드(OR 조합 포함)에 해당하고 제외어가 없는지. */
    fun titleMatches(title: String, keyword: String): Boolean {
        val plan = runCatching { SearchQueryParser.parse(keyword) }.getOrNull() ?: return false
        if (SearchQueryParser.isExcluded(title, plan.excludedTerms)) return false
        return plan.terms.any { title.contains(it, ignoreCase = true) }
    }
}

object KeywordAlerts {
    private const val WORK_HOURLY = "keyword_alerts"
    private const val WORK_FAST = "keyword_alerts_fast"
    private const val FAST_MINUTES = 15L
    private const val HOURLY_MINUTES = 60L
    private val PRESS_AGENCIES = listOf(AgencyId.FSS, AgencyId.FSC)

    fun needsNotificationPermission(context: Context): Boolean = UpdateChecker.needsNotificationPermission(context)

    /**
     * 저장된 설정에 맞춰 주기 확인을 등록·취소한다. 앱을 열 때와 설정을 바꿀 때 부른다.
     * 15분 작업은 '약 15분' 키워드가 있을 때만, 1시간 작업은 '1시간' 키워드나 보도자료·연구자료 알림이 있을 때만 돈다.
     */
    fun reschedule(context: Context) {
        val app = context.applicationContext
        val store = SettingsStore(app)
        val modes = store.loadSettings().keywords.map(store::keywordAlertMode)
        val fast = KeywordAlertMode.FAST in modes
        val hourly = KeywordAlertMode.HOURLY in modes || NewMaterialAlerts.anyEnabled(store) || store.libraryAlertEnabled()
        enqueue(app, WORK_FAST, KeywordAlertMode.FAST, FAST_MINUTES, fast)
        enqueue(app, WORK_HOURLY, KeywordAlertMode.HOURLY, HOURLY_MINUTES, hourly)
    }

    private fun enqueue(context: Context, name: String, mode: KeywordAlertMode, minutes: Long, enabled: Boolean) {
        if (!enabled) {
            Scheduler.cancel(name)
            return
        }
        Scheduler.schedulePeriodic(name, periodMs = minutes * 60_000L) {
            check(context, mode)
            if (mode == KeywordAlertMode.HOURLY) {
                runCatching { NewMaterialAlerts.check(context) }
                runCatching { LibraryAlerts.check(context) }
            }
        }
    }

    /** [mode]로 설정된 키워드마다 새 기사·보도자료를 확인해 알린다. */
    internal suspend fun check(context: Context, mode: KeywordAlertMode) {
        val store = SettingsStore(context)
        if (needsNotificationPermission(context)) return
        val settings = store.loadSettings()
        val keywords = settings.keywords.filter { store.keywordAlertMode(it) == mode }
        if (keywords.isEmpty()) return
        val credentials = store.loadCredentials()
        val news = NewsRepository()
        val agencies = AgencyRepository(context)
        val releases = PRESS_AGENCIES.associateWith { agency ->
            runCatching { agencies.latest(agency, 20) }.getOrNull()
        }
        for (keyword in keywords) {
            val newsHits = runCatching {
                news.search(keyword, settings.copy(timeRange = TimeRange.DAY), credentials).articles
                    .map { AlertHit(it.title, it.link, it.source) }
            }.getOrNull()
            val pressHits = releases.mapNotNull { (agency, items) ->
                items?.filter { KeywordAlertLogic.titleMatches(it.title, keyword) }
                    ?.map { AlertHit(it.title, it.link, agency.shortLabel) }
            }.flatten()
            // 기사 조회가 실패하면 이번에는 건너뛰고(기록 없음), 다음 주기에 다시 본다.
            val hits = newsHits?.plus(pressHits)
            val key = "alert_$keyword"
            val (fresh, seen) = KeywordAlertLogic.diff(hits, store.seenLinks(key))
            if (seen != null) store.saveSeenLinks(key, seen)
            if (fresh.isNotEmpty()) {
                AlertNotifier.post(
                    context, "keyword_alerts", "키워드 알림", 2000 + (keyword.hashCode() and 0xFFFF),
                    "‘$keyword’ 새 소식 ${fresh.size}건", fresh.map { "[${it.source}] ${it.title}" }
                )
            }
        }
    }
}
