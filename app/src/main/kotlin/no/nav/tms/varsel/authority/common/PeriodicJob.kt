package no.nav.tms.varsel.authority.common

import kotlinx.coroutines.*
import kotlinx.coroutines.CoroutineStart.LAZY
import java.time.Duration

abstract class PeriodicJob(private val interval: Duration) {

    private val scope = CoroutineScope(Dispatchers.Default + Job())

    abstract val job: Job

    protected fun initializeJob(periodicProcess: suspend () -> Unit) = scope.launch(start = LAZY) {
        while (job.isActive) {
            periodicProcess()
            delay(interval.toMillis())
        }
    }

    private fun className() = this::class.simpleName!!

    fun start() {
        job.start()
    }

    fun isCompleted(): Boolean {
        return job.isCompleted
    }

    suspend fun stop() {
        job.cancelAndJoin()
    }
}
