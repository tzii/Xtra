package com.github.andreyasadchy.xtra.ui.player

import com.github.andreyasadchy.xtra.ui.player.PinchDisplayModeController.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.ln

class PinchDisplayModeControllerTest {

    private lateinit var controller: PinchDisplayModeController

    @Before
    fun setUp() {
        controller = PinchDisplayModeController()
    }

    private fun armedTarget(events: List<Event>): Event.Armed? = events.filterIsInstance<Event.Armed>().firstOrNull()

    private fun preview(events: List<Event>): Event.Preview? = events.filterIsInstance<Event.Preview>().firstOrNull()

    private fun expectedProgress(scale: Float, threshold: Float): Float {
        return (ln(scale) / ln(threshold)).coerceIn(0f, 1f)
    }

    @Test
    fun `fit outward previews fill and arms at threshold`() {
        controller.begin(PlayerDisplayMode.FIT)
        val preview = preview(controller.update(1.05f))
        assertEquals(PlayerDisplayMode.FILL, preview?.toward)
        assertEquals(expectedProgress(1.05f, PinchDisplayModeController.OUTWARD_ARM_THRESHOLD), preview?.progress ?: -1f, 0.001f)

        val armed = armedTarget(controller.update(PinchDisplayModeController.OUTWARD_ARM_THRESHOLD))
        assertEquals(PlayerDisplayMode.FILL, armed?.target)
    }

    @Test
    fun `fit fill-armed disarms only after hysteresis`() {
        controller.begin(PlayerDisplayMode.FIT)
        controller.update(PinchDisplayModeController.OUTWARD_ARM_THRESHOLD)
        var events = controller.update(
            PinchDisplayModeController.OUTWARD_ARM_THRESHOLD - PinchDisplayModeController.REVERSAL_HYSTERESIS + 0.01f
        )
        assertTrue(armedTarget(events) == null && events.filterIsInstance<Event.Disarmed>().isEmpty())
        events = controller.update(PinchDisplayModeController.OUTWARD_ARM_THRESHOLD - PinchDisplayModeController.REVERSAL_HYSTERESIS)
        assertTrue(events.filterIsInstance<Event.Disarmed>().isNotEmpty())
        assertEquals(PlayerDisplayMode.FILL, (events.filterIsInstance<Event.Disarmed>().first() as Event.Disarmed).toward)
    }

    @Test
    fun `fit fill-armed can re-arm after disarm`() {
        controller.begin(PlayerDisplayMode.FIT)
        controller.update(PinchDisplayModeController.OUTWARD_ARM_THRESHOLD)
        controller.update(PinchDisplayModeController.OUTWARD_ARM_THRESHOLD - PinchDisplayModeController.REVERSAL_HYSTERESIS)
        val events = controller.update(PinchDisplayModeController.OUTWARD_ARM_THRESHOLD + 0.01f)
        assertEquals(PlayerDisplayMode.FILL, armedTarget(events)?.target)
    }

    @Test
    fun `fit inward emits elastic deformation and never arms`() {
        controller.begin(PlayerDisplayMode.FIT)
        val elastic = controller.update(0.96f).filterIsInstance<Event.Elastic>().first()
        assertEquals(PlayerDisplayMode.FIT, elastic.from)
        assertEquals(5f / 9f, elastic.deformation, 0.001f)

        val saturated = controller.update(0.85f).filterIsInstance<Event.Elastic>().first()
        assertEquals(1f, saturated.deformation, 0.001f)

        val events = controller.update(0.6f)
        assertTrue(events.filterIsInstance<Event.Armed>().isEmpty())
        assertTrue(controller.release() is Event.Restore)
    }

    @Test
    fun `elastic deformation recedes when the pinch reverses toward neutral`() {
        controller.begin(PlayerDisplayMode.FIT)
        controller.update(0.85f)
        val receding = controller.update(0.96f).filterIsInstance<Event.Elastic>().first()
        assertEquals(5f / 9f, receding.deformation, 0.001f)
    }

    @Test
    fun `neutral before direction established emits no preview`() {
        controller.begin(PlayerDisplayMode.FIT)
        val events = controller.update(1.0002f)
        assertTrue(events.filterIsInstance<Event.NoPreview>().isNotEmpty())
        assertTrue(events.filterIsInstance<Event.Elastic>().isEmpty())
    }

    @Test
    fun `established gesture crossing neutral emits zero deformation instead of no preview`() {
        controller.begin(PlayerDisplayMode.FIT)
        controller.update(0.9f)
        val events = controller.update(1.0002f)
        assertTrue(events.filterIsInstance<Event.NoPreview>().isEmpty())
        val elastic = events.filterIsInstance<Event.Elastic>().first()
        assertEquals(PlayerDisplayMode.FIT, elastic.from)
        assertEquals(0f, elastic.deformation, 0.0001f)
    }

    @Test
    fun `begin resets establishment from a previous sequence`() {
        controller.begin(PlayerDisplayMode.FIT)
        controller.update(0.9f)
        controller.begin(PlayerDisplayMode.FIT)
        val events = controller.update(1.0002f)
        assertTrue(events.filterIsInstance<Event.NoPreview>().isNotEmpty())
    }

    @Test
    fun `fit inward through neutral to outward fill preview stays continuous`() {
        controller.begin(PlayerDisplayMode.FIT)
        controller.update(0.85f)
        controller.update(1.0002f)
        val preview = preview(controller.update(1.05f))
        assertEquals(PlayerDisplayMode.FILL, preview?.toward)
        assertEquals(expectedProgress(1.05f, PinchDisplayModeController.OUTWARD_ARM_THRESHOLD), preview?.progress ?: -1f, 0.001f)
    }

    @Test
    fun `fill inward previews fit and arms at threshold`() {
        controller.begin(PlayerDisplayMode.FILL)
        val preview = preview(controller.update(0.95f))
        assertEquals(PlayerDisplayMode.FIT, preview?.toward)
        assertEquals(expectedProgress(0.95f, PinchDisplayModeController.INWARD_ARM_THRESHOLD), preview?.progress ?: -1f, 0.001f)

        val armed = armedTarget(controller.update(PinchDisplayModeController.INWARD_ARM_THRESHOLD))
        assertEquals(PlayerDisplayMode.FIT, armed?.target)
    }

    @Test
    fun `reciprocal finger travel produces symmetric fit and fill progress`() {
        controller.begin(PlayerDisplayMode.FIT)
        val outward = preview(controller.update(1.05f))?.progress ?: -1f

        controller.begin(PlayerDisplayMode.FILL)
        val inward = preview(controller.update(1f / 1.05f))?.progress ?: -1f

        assertEquals(outward, inward, 0.001f)
    }

    @Test
    fun `fill fit-armed disarms only after hysteresis`() {
        controller.begin(PlayerDisplayMode.FILL)
        controller.update(PinchDisplayModeController.INWARD_ARM_THRESHOLD)
        var events = controller.update(
            PinchDisplayModeController.INWARD_ARM_THRESHOLD + PinchDisplayModeController.REVERSAL_HYSTERESIS - 0.01f
        )
        assertTrue(events.filterIsInstance<Event.Disarmed>().isEmpty())
        events = controller.update(PinchDisplayModeController.INWARD_ARM_THRESHOLD + PinchDisplayModeController.REVERSAL_HYSTERESIS)
        assertTrue(events.filterIsInstance<Event.Disarmed>().isNotEmpty())
    }

    @Test
    fun `fill outward emits elastic deformation and never arms`() {
        controller.begin(PlayerDisplayMode.FILL)
        val elastic = controller.update(1.04f).filterIsInstance<Event.Elastic>().first()
        assertEquals(PlayerDisplayMode.FILL, elastic.from)
        assertEquals(5f / 9f, elastic.deformation, 0.001f)

        val events = controller.update(1.2f)
        assertTrue(events.filterIsInstance<Event.Armed>().isEmpty())
        assertTrue(controller.release() is Event.Restore)
    }

    @Test
    fun `fill outward through neutral to inward fit preview stays continuous`() {
        controller.begin(PlayerDisplayMode.FILL)
        controller.update(1.2f)
        controller.update(0.9998f)
        val preview = preview(controller.update(0.95f))
        assertEquals(PlayerDisplayMode.FIT, preview?.toward)
        assertEquals(expectedProgress(0.95f, PinchDisplayModeController.INWARD_ARM_THRESHOLD), preview?.progress ?: -1f, 0.001f)
    }

    @Test
    fun `stretch always has a live target and never emits deformation`() {
        controller.begin(PlayerDisplayMode.STRETCH)
        val outward = controller.update(1.2f)
        assertTrue(outward.filterIsInstance<Event.Preview>().isNotEmpty())
        assertTrue(outward.filterIsInstance<Event.Elastic>().isEmpty())
        val inward = controller.update(0.8f)
        assertTrue(inward.filterIsInstance<Event.Preview>().isNotEmpty())
        assertTrue(inward.filterIsInstance<Event.Elastic>().isEmpty())
    }

    @Test
    fun `stretch outward previews toward fill`() {
        controller.begin(PlayerDisplayMode.STRETCH)
        val preview = preview(controller.update(1.05f))
        assertEquals(PlayerDisplayMode.STRETCH, preview?.from)
        assertEquals(PlayerDisplayMode.FILL, preview?.toward)
        assertEquals(expectedProgress(1.05f, PinchDisplayModeController.OUTWARD_ARM_THRESHOLD), preview?.progress ?: -1f, 0.001f)
    }

    @Test
    fun `stretch inward previews toward fit`() {
        controller.begin(PlayerDisplayMode.STRETCH)
        val preview = preview(controller.update(0.95f))
        assertEquals(PlayerDisplayMode.FIT, preview?.toward)
        assertEquals(expectedProgress(0.95f, PinchDisplayModeController.INWARD_ARM_THRESHOLD), preview?.progress ?: -1f, 0.001f)
    }

    @Test
    fun `stretch outward arms fill and hysteresis disarms to stretch`() {
        controller.begin(PlayerDisplayMode.STRETCH)
        assertEquals(PlayerDisplayMode.FILL, armedTarget(controller.update(PinchDisplayModeController.OUTWARD_ARM_THRESHOLD))?.target)
        val events = controller.update(PinchDisplayModeController.OUTWARD_ARM_THRESHOLD - PinchDisplayModeController.REVERSAL_HYSTERESIS)
        val disarmed = events.filterIsInstance<Event.Disarmed>().firstOrNull()
        assertEquals(PlayerDisplayMode.FILL, (disarmed as Event.Disarmed).toward)
    }

    @Test
    fun `stretch inward arms fit and hysteresis disarms to stretch`() {
        controller.begin(PlayerDisplayMode.STRETCH)
        assertEquals(PlayerDisplayMode.FIT, armedTarget(controller.update(PinchDisplayModeController.INWARD_ARM_THRESHOLD))?.target)
        val events = controller.update(PinchDisplayModeController.INWARD_ARM_THRESHOLD + PinchDisplayModeController.REVERSAL_HYSTERESIS)
        assertTrue(events.filterIsInstance<Event.Disarmed>().isNotEmpty())
    }

    @Test
    fun `release while armed commits fit or fill only`() {
        controller.begin(PlayerDisplayMode.FIT)
        controller.update(PinchDisplayModeController.OUTWARD_ARM_THRESHOLD)
        val released = controller.release()
        assertTrue(released is Event.Commit && released.mode == PlayerDisplayMode.FILL)

        controller.begin(PlayerDisplayMode.FILL)
        controller.update(PinchDisplayModeController.INWARD_ARM_THRESHOLD)
        val secondRelease = controller.release()
        assertTrue(secondRelease is Event.Commit && secondRelease.mode == PlayerDisplayMode.FIT)
    }

    @Test
    fun `release while neutral restores committed mode`() {
        controller.begin(PlayerDisplayMode.STRETCH)
        controller.update(1.05f)
        val released = controller.release()
        assertTrue(released is Event.Restore && released.mode == PlayerDisplayMode.STRETCH)
    }

    @Test
    fun `release after disarm restores committed mode`() {
        controller.begin(PlayerDisplayMode.FIT)
        controller.update(PinchDisplayModeController.OUTWARD_ARM_THRESHOLD)
        controller.update(PinchDisplayModeController.OUTWARD_ARM_THRESHOLD - PinchDisplayModeController.REVERSAL_HYSTERESIS)
        val released = controller.release()
        assertTrue(released is Event.Restore && released.mode == PlayerDisplayMode.FIT)
    }

    @Test
    fun `cancel restores committed mode from any state`() {
        controller.begin(PlayerDisplayMode.STRETCH)
        controller.update(PinchDisplayModeController.INWARD_ARM_THRESHOLD)
        val cancelled = controller.cancel()
        assertTrue(cancelled is Event.Cancelled && cancelled.mode == PlayerDisplayMode.STRETCH)
    }

    @Test
    fun `armed fires once per armed target`() {
        controller.begin(PlayerDisplayMode.FIT)
        controller.update(PinchDisplayModeController.OUTWARD_ARM_THRESHOLD)
        val events = controller.update(PinchDisplayModeController.OUTWARD_ARM_THRESHOLD + 0.05f)
        assertTrue(armedTarget(events) == null)
        val preview = preview(events)
        assertEquals(1f, preview?.progress ?: -1f, 0.0001f)
    }

    @Test
    fun `commit updates the committed mode for the next pinch`() {
        controller.begin(PlayerDisplayMode.FIT)
        controller.update(PinchDisplayModeController.OUTWARD_ARM_THRESHOLD)
        controller.release()
        assertEquals(PlayerDisplayMode.FILL, controller.committedMode)

        controller.begin(controller.committedMode)
        val preview = preview(controller.update(0.95f))
        assertEquals(PlayerDisplayMode.FIT, preview?.toward)
    }

    @Test
    fun `begin resets state from a previous sequence`() {
        controller.begin(PlayerDisplayMode.FIT)
        controller.update(PinchDisplayModeController.OUTWARD_ARM_THRESHOLD)
        controller.begin(PlayerDisplayMode.FIT)
        val released = controller.release()
        assertTrue(released is Event.Restore)
    }
}
