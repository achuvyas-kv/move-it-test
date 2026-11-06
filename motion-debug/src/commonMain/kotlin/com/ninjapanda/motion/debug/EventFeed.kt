package com.ninjapanda.motion.debug

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.ninjapanda.motion.MotionEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

/** Recent motion events plus the one to flash on screen right now. */
@Stable
class EventFeed {
    val recent = mutableStateListOf<MotionEvent>()
    var flash by mutableStateOf<MotionEvent?>(null)
        internal set
}

@Composable
fun rememberEventFeed(events: Flow<MotionEvent>): EventFeed {
    val feed = remember { EventFeed() }
    LaunchedEffect(events) {
        events.collect { event ->
            feed.recent.add(event)
            if (feed.recent.size > 20) feed.recent.removeAt(0)
            if (event !is MotionEvent.DuckEnd) feed.flash = event
        }
    }
    LaunchedEffect(feed.flash) {
        if (feed.flash != null) {
            delay(700)
            feed.flash = null
        }
    }
    return feed
}

/** Big centred text for the latest move (SLASH, JUMP, …). */
@Composable
fun EventFlash(feed: EventFeed, modifier: Modifier = Modifier, fontSize: TextUnit = 40.sp) {
    val event = feed.flash ?: return
    Text(describe(event), modifier = modifier, color = Color.White, fontSize = fontSize, fontWeight = FontWeight.Black)
}
