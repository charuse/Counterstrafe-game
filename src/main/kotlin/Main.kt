import javafx.application.Application
import javafx.application.Platform
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.input.KeyCode
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.stage.Stage
import java.util.EnumSet
import kotlin.concurrent.fixedRateTimer

class CounterStrafeTest : Application() {

    private val MIN_HOLD_MS = 250.0
    private val COUNTERSTRAFE_MS = 100.0
    private val TIMEOUT_MS = 1000.0

    private val allowedKeys = EnumSet.of(
        KeyCode.W, KeyCode.A, KeyCode.S, KeyCode.D,
        KeyCode.Q, KeyCode.E,
        KeyCode.SHIFT, KeyCode.CONTROL
    )

    private var key1: KeyCode = KeyCode.A
    private var key2: KeyCode = KeyCode.D

    private var activeStartKey: KeyCode? = null
    private var activeCounterKey: KeyCode? = null

    private var keyPressedTime = 0L
    private var keyReleasedTime = 0L
    private var counterPressedTime = 0L

    private var startKeyDown = false
    private var counterKeyDown = false

    private val resultLabel = Label("Waiting...")
    private val timingLabel = Label("")

    private var holdTimerStarted = false

    private lateinit var key1Box: ComboBox<KeyCode>
    private lateinit var key2Box: ComboBox<KeyCode>

    override fun start(stage: Stage) {

        key1Box = ComboBox<KeyCode>().apply {
            items.addAll(allowedKeys)
            value = key1
            setCellFactory {
                object : javafx.scene.control.ListCell<KeyCode>() {
                    override fun updateItem(item: KeyCode?, empty: Boolean) {
                        super.updateItem(item, empty)
                        text = item?.name ?: ""
                        isDisable = item == key2
                    }
                }
            }
            setButtonCell(object : javafx.scene.control.ListCell<KeyCode>() {
                override fun updateItem(item: KeyCode?, empty: Boolean) {
                    super.updateItem(item, empty)
                    text = item?.name ?: ""
                }
            })
            setOnAction {
                key1 = value
                key2Box.items = key2Box.items
            }
        }

        key2Box = ComboBox<KeyCode>().apply {
            items.addAll(allowedKeys)
            value = key2
            setCellFactory {
                object : javafx.scene.control.ListCell<KeyCode>() {
                    override fun updateItem(item: KeyCode?, empty: Boolean) {
                        super.updateItem(item, empty)
                        text = item?.name ?: ""
                        isDisable = item == key1
                    }
                }
            }
            setButtonCell(object : javafx.scene.control.ListCell<KeyCode>() {
                override fun updateItem(item: KeyCode?, empty: Boolean) {
                    super.updateItem(item, empty)
                    text = item?.name ?: ""
                }
            })
            setOnAction {
                key2 = value
                key1Box.items = key1Box.items
            }
        }

        val layout = VBox(
            10.0,
            Label("Key 1"), key1Box,
            Label("Key 2"), key2Box,
            resultLabel,
            timingLabel
        ).apply {
            alignment = Pos.CENTER
        }

        val root = StackPane(layout)
        val scene = Scene(root, 400.0, 300.0)

        scene.setOnKeyPressed { e ->
            val now = System.nanoTime()

            if (activeStartKey == null && (e.code == key1 || e.code == key2)) {
                activeStartKey = e.code
                activeCounterKey = if (e.code == key1) key2 else key1
                startKeyDown = true
                keyPressedTime = now
                holdTimerStarted = true
                resultLabel.text = "HOLDING $activeStartKey..."
                startHoldTimer()
                return@setOnKeyPressed
            }

            if (e.code == activeCounterKey && !counterKeyDown) {
                counterKeyDown = true
                counterPressedTime = now

                if (startKeyDown) {
                    val holdMs = (now - keyPressedTime) / 1_000_000.0
                    sendResult("FAIL", "Overlap! Held $activeStartKey for ${"%.1f".format(holdMs)} ms")
                } else if (keyReleasedTime != 0L) {
                    val deltaMs = (counterPressedTime - keyReleasedTime) / 1_000_000.0
                    val holdMs = (keyReleasedTime - keyPressedTime) / 1_000_000.0

                    when {
                        deltaMs > TIMEOUT_MS ->
                            sendResult("FAIL", "Timeout! Took ${"%.1f".format(TIMEOUT_MS)} ms to switch")
                        holdMs < MIN_HOLD_MS ->
                            sendResult("FAIL", "Held $activeStartKey too short: ${"%.1f".format(holdMs)} ms")
                        deltaMs <= COUNTERSTRAFE_MS ->
                            sendResult("SUCCESS", "Held $activeStartKey ${"%.1f".format(holdMs)} ms, switched in ${"%.1f".format(deltaMs)} ms")
                        else ->
                            sendResult("FAIL", "Too slow! Held $activeStartKey ${"%.1f".format(holdMs)} ms, delay ${"%.1f".format(deltaMs)} ms")
                    }
                }
            }
        }

        scene.setOnKeyReleased { e ->
            val now = System.nanoTime()

            if (e.code == activeStartKey && startKeyDown) {
                startKeyDown = false
                keyReleasedTime = now
                resultLabel.text = "SWITCH to $activeCounterKey!"
            }

            if (e.code == activeCounterKey && counterKeyDown) {
                counterKeyDown = false
                reset()
            }
        }

        stage.title = "Counterstrafe Trainer"
        stage.scene = scene
        stage.isResizable = true
        stage.setOnCloseRequest {
            Platform.exit()
            System.exit(0)
        }

        stage.show()
    }

    private fun startHoldTimer() {
        fixedRateTimer("holdTimer", false, 0L, 16L) {
            val now = System.nanoTime()

            if (startKeyDown) {
                val deltaMs = (now - keyPressedTime) / 1_000_000.0
                Platform.runLater {
                    timingLabel.text = "Holding $activeStartKey: ${"%.1f".format(deltaMs)} ms"
                }
            } else if (keyReleasedTime != 0L && !counterKeyDown) {
                var delta = (now - keyReleasedTime) / 1_000_000.0
                if (delta >= TIMEOUT_MS) {
                    delta = TIMEOUT_MS
                    Platform.runLater {
                        timingLabel.text = "Time since release: ${"%.1f".format(delta)} ms"
                        sendResult("FAIL", "Timeout! Took ${"%.1f".format(delta)} ms to switch")
                    }
                    cancel()
                } else {
                    Platform.runLater {
                        timingLabel.text = "Time since release: ${"%.1f".format(delta)} ms"
                    }
                }
            }

            if (!holdTimerStarted) cancel()
        }
    }

    private fun sendResult(result: String, reason: String) {
        resultLabel.text = result.also { timingLabel.text = reason }
        reset()
    }

    private fun reset() {
        keyPressedTime = 0L.also { keyReleasedTime = it; counterPressedTime = it }
        startKeyDown = false.also { counterKeyDown = it; holdTimerStarted = it }
        activeStartKey = null
        activeCounterKey = null
    }
}

fun main() {
    Application.launch(CounterStrafeTest::class.java)
}
