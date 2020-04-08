/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package io.ktor.network.selector

import io.ktor.util.collections.*
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.native.concurrent.*


public class WorkerSelectorManager : SelectorManager {
    private val selectorContext = newSingleThreadContext("WorkerSelectorManager")
    override val coroutineContext: CoroutineContext = selectorContext
    override fun notifyClosed(selectable: Selectable) {}

    private val events: LockFreeMPSCQueue<EventInfo> = LockFreeMPSCQueue()

    init {
        freeze()

        launch {
            selectHelper(events)
        }
    }

    override suspend fun select(
        selectable: Selectable,
        interest: SelectInterest
    ) {
        if (events.isClosed) return

        return suspendCancellableCoroutine { continuation ->
            require(selectable is SelectableNative)

            val selectorState = EventInfo(selectable.descriptor, interest, continuation).freeze()
            if (!events.addLast(selectorState)) {
                continuation.resumeWithException(CancellationException("Socked closed."))
            }
        }
    }

    override fun close() {
        events.close()
        selectorContext.worker.requestTermination(processScheduledJobs = true)
    }
}
