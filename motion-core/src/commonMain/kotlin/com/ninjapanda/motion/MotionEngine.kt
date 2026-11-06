package com.ninjapanda.motion

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.concurrent.Volatile

/**
 * What games talk to. A platform pose source calls [submit] for each camera frame;
 * games read [snapshot] every render frame (newest wins) and collect [events].
 *
 * [submit] must always be called from the same thread (the pose source's callback thread).
 */
class MotionEngine(config: MotionConfig = MotionConfig()) {
    private val processor = MotionProcessor(config)

    private val _snapshot = MutableStateFlow(MotionSnapshot.Empty)
    val snapshot: StateFlow<MotionSnapshot> = _snapshot.asStateFlow()

    private val _events = MutableSharedFlow<MotionEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<MotionEvent> = _events.asSharedFlow()

    @Volatile
    private var recalibrateRequested = false

    fun submit(frame: MotionFrame) {
        if (recalibrateRequested) {
            recalibrateRequested = false
            processor.recalibrate()
        }
        val result = processor.process(frame)
        _snapshot.value = result
        result.events.forEach { _events.tryEmit(it) }
    }

    /** Safe to call from any thread; takes effect on the next frame. */
    fun recalibrate() {
        recalibrateRequested = true
    }
}
