package com.player.tv.data

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XtreamClientTest {

    @Test
    fun normalizeServerUrl_acceptsCommonInputs() {
        assertEquals("http://line.tv:8080", XtreamClient.normalizeServerUrl("line.tv:8080"))
        assertEquals("http://line.tv:8080", XtreamClient.normalizeServerUrl(" http://line.tv:8080/ "))
        assertEquals("https://line.tv", XtreamClient.normalizeServerUrl("https://line.tv/player_api.php?username=a&password=b"))
        assertEquals("http://line.tv:8000/sub", XtreamClient.normalizeServerUrl("http://line.tv:8000/sub/get.php?username=a&password=b&type=m3u"))
        assertNull(XtreamClient.normalizeServerUrl(""))
    }

    @Test
    fun jsonHelpers_toleratePanelInconsistencies() {
        val o = parseJson("""{"a":"12","b":12,"c":"","d":null,"e":false,"f":"7.5","g":["","x.jpg"],"h":"y.jpg"}""") as JsonObject
        assertEquals(12, o.int("a"))
        assertEquals(12, o.int("b"))
        assertNull(o.str("c"))
        assertNull(o.str("d"))
        assertEquals(7, o.int("f"))
        assertEquals("x.jpg", o.firstString("g"))
        assertEquals("y.jpg", o.firstString("h"))
    }
}
