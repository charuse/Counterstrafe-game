// main.kt
import javafx.application.Application
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.input.KeyCode
import javafx.scene.layout.*
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.scene.media.MediaView
import javafx.stage.Stage
import javafx.util.Duration
import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.event.EventHandler

const val IDLE_VIDEO = "idle.mp4"
const val MOVING_VIDEO = "moving.mp4"

enum class State {
    IDLE, HOLDING, WAITING_FOR_SWITCH, SUCCESS, FAIL
}

class MainApp : Application() {

    private lateinit var idlePlayer: MediaPlayer
    private lateinit var movingPlayer: MediaPlayer
    private lateinit var mediaView: MediaView
    private lateinit var feedbackLabel: Label

    private var currentState = State.IDLE
    private var key1 = KeyCode.A
    private var key2 = KeyCode.D

    private var activeKey: KeyCode? = null
    private var expectedSwitchKey: KeyCode? = null

    private var releaseTime = 0L
    private var autoFailTimeline: Timeline? = null

    private var passThresholdMs = 100

    override fun start(stage: Stage) {
        val root = StackPane(Label("Loading..."))
        val scene = Scene(root, 1000.0, 700.0)

        stage.scene = scene
        stage.show()

        Platform.runLater {
            preloadMedia {
                root.children.setAll(buildUI(scene))
            }
        }
    }

    private fun preloadMedia(onDone: () -> Unit) {
        idlePlayer = MediaPlayer(Media(javaClass.getResource(IDLE_VIDEO)!!.toExternalForm())).apply {
            isMute = true
            cycleCount = MediaPlayer.INDEFINITE
        }
        movingPlayer = MediaPlayer(Media(javaClass.getResource(MOVING_VIDEO)!!.toExternalForm())).apply {
            isMute = true
        }
        onDone()
    }

    private fun setMuted(muted: Boolean) {
        idlePlayer.isMute = muted
        movingPlayer.isMute = muted
    }

    private fun buildUI(scene: Scene): BorderPane {

        mediaView = MediaView(idlePlayer).apply {
            isPreserveRatio = true
            fitWidthProperty().bind(scene.widthProperty())
            fitHeightProperty().bind(scene.heightProperty().multiply(0.75))
        }

        feedbackLabel = Label("").apply {
            style = "-fx-font-size: 24px;"
        }

        val key1Box = ComboBox<KeyCode>().apply {
            items.addAll(KeyCode.A, KeyCode.D, KeyCode.W, KeyCode.S)
            value = key1
            setOnAction { key1 = value }
        }

        val key2Box = ComboBox<KeyCode>().apply {
            items.addAll(KeyCode.A, KeyCode.D, KeyCode.W, KeyCode.S)
            value = key2
            setOnAction { key2 = value }
        }

        val keysBox = HBox(20.0, key1Box, key2Box).apply {
            alignment = Pos.CENTER
        }

        val muteToggle = ToggleButton("SOUND").apply {
            isSelected = true
            setMuted(true)
            setOnAction {
                val muted = isSelected
                text = if (muted) "SOUND" else "MUTE"
                setMuted(muted)
            }
        }

        val msValueLabel = Label(passThresholdMs.toString()).apply {
            style = "-fx-underline: true;"
        }

        val msSuffixLabel = Label("ms")

        val msTextField = TextField().apply {
            prefWidth = 60.0
            textFormatter = TextFormatter<String> { change ->
                if (change.controlNewText.matches(Regex("\\d*"))) change else null
            }
        }

        lateinit var msBox: HBox

        fun commitMsEdit() {
            val parsed = msTextField.text.toIntOrNull() ?: passThresholdMs
            passThresholdMs = parsed.coerceIn(10, 200)
            msValueLabel.text = passThresholdMs.toString()
            msBox.children.setAll(msValueLabel, msSuffixLabel)
        }

        msBox = HBox(6.0, msValueLabel, msSuffixLabel).apply {
            alignment = Pos.CENTER
        }

        msValueLabel.setOnMouseClicked {
            msTextField.text = passThresholdMs.toString()
            msBox.children.setAll(msTextField, msSuffixLabel)
            msTextField.requestFocus()
            msTextField.selectAll()
        }

        msTextField.setOnAction {
            commitMsEdit()
        }

        msTextField.focusedProperty().addListener { _, _, focused ->
            if (!focused) commitMsEdit()
        }

        val bottomRow = HBox(30.0, keysBox, msBox, muteToggle).apply {
            alignment = Pos.CENTER
            padding = Insets(12.0)
        }

        val layout = BorderPane().apply {
            top = StackPane(mediaView)
            center = feedbackLabel
            bottom = bottomRow
        }

        playIdle()

        scene.setOnKeyPressed { e ->
            if (currentState == State.IDLE && (e.code == key1 || e.code == key2)) {
                activeKey = e.code
                expectedSwitchKey = if (e.code == key1) key2 else key1
                currentState = State.HOLDING
                playMoving()
            } else if (currentState == State.WAITING_FOR_SWITCH && e.code == expectedSwitchKey) {
                autoFailTimeline?.stop()
                val deltaMs = System.currentTimeMillis() - releaseTime
                evaluateCounterstrafe(deltaMs)
            }
        }

        scene.setOnKeyReleased { e ->
            if (currentState == State.HOLDING && e.code == activeKey) {
                releaseTime = System.currentTimeMillis()
                currentState = State.WAITING_FOR_SWITCH
                autoFailTimeline = Timeline(
                    KeyFrame(
                        Duration.seconds(1.0),
                        EventHandler {
                            if (currentState == State.WAITING_FOR_SWITCH) {
                                evaluateCounterstrafe(1001)
                            }
                        }
                    )
                ).apply { play() }
            }
        }

        return layout
    }

    private fun evaluateCounterstrafe(deltaMs: Long) {
        autoFailTimeline?.stop()

        val success = deltaMs <= passThresholdMs
        feedbackLabel.text =
            if (success) "SUCCESS – ${deltaMs}ms" else "FAIL – ${deltaMs}ms"

        movingPlayer.pause()

        Timeline(
            KeyFrame(
                Duration.seconds(if (success) 2.0 else 1.0),
                EventHandler { playIdle() }
            )
        ).play()
    }

    private fun playIdle() {
        currentState = State.IDLE
        movingPlayer.pause()
        idlePlayer.seek(Duration.ZERO)
        mediaView.mediaPlayer = idlePlayer
        idlePlayer.play()
        feedbackLabel.text = ""
    }

    private fun playMoving() {
        idlePlayer.pause()
        movingPlayer.seek(Duration.ZERO)
        mediaView.mediaPlayer = movingPlayer
        movingPlayer.play()
    }
}

fun main() {
    Application.launch(MainApp::class.java)
}