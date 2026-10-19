package io.github.hdlee73.financenewsradar.platform

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 안드로이드 WorkManager 주기 작업의 데스크톱 대역.
 * 앱이 켜져 있는 동안 이름별로 하나씩 도는 코루틴 작업을 관리한다.
 */
object Scheduler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val jobs = ConcurrentHashMap<String, Job>()

    fun isScheduled(name: String): Boolean = jobs[name]?.isActive == true

    fun cancel(name: String) {
        jobs.remove(name)?.cancel()
    }

    /** [periodMs] 간격으로 [block]을 돈다. 이미 같은 이름이 있으면 지우고 다시 시작한다. */
    fun schedulePeriodic(name: String, periodMs: Long, initialDelayMs: Long = periodMs, runImmediately: Boolean = false, block: suspend () -> Unit) {
        cancel(name)
        jobs[name] = scope.launch {
            try {
                if (runImmediately) runCatching { block() }
                var next = initialDelayMs
                while (isActive) {
                    if (next > 0) delay(next)
                    if (!isActive) break
                    runCatching { block() }
                    next = periodMs
                }
            } finally {
                jobs.remove(name)
            }
        }
    }

    fun scheduleOnce(name: String, delayMs: Long, block: suspend () -> Unit) {
        cancel(name)
        jobs[name] = scope.launch {
            try {
                if (delayMs > 0) delay(delayMs)
                runCatching { block() }
            } finally {
                jobs.remove(name)
            }
        }
    }

    /** 매일 정해진 시각에 돌도록 다음 발생 시각(ms)을 기다렸다 그 뒤 하루 간격으로 돈다. */
    fun scheduleDaily(name: String, initialDelayMs: Long, block: suspend () -> Unit) {
        schedulePeriodic(name, periodMs = 24L * 60 * 60 * 1000, initialDelayMs = initialDelayMs, block = block)
    }
}
