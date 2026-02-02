// Main.kt
import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.application.Application
import javafx.event.ActionEvent
import javafx.event.EventHandler
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

class Main : Application() {

    enum class State { IDLE, HOLDING, WAITING_FOR_SWITCH, SUCCESS, FAIL }

    private var currentState = State.IDLE

    private lateinit var idlePlayer: MediaPlayer
    private lateinit var movingPlayer: MediaPlayer
    private lateinit var mediaView: MediaView
    private lateinit var feedbackLabel: Label

    private var activeKey: KeyCode? = null
    private var firstKey: KeyCode? = KeyCode.A
    private var secondKey: KeyCode? = KeyCode.D

    private var releaseTime = 0L
    private var passThresholdMs = 100
    private var autoFailTimeline: Timeline? = null

    override fun start(stage: Stage) {

        try {
            idlePlayer = MediaPlayer(
                Media(javaClass.getResource("/idle.mp4")!!.toExternalForm())
            ).apply {
                cycleCount = MediaPlayer.INDEFINITE
                play()
            }

            movingPlayer = MediaPlayer(
                Media(javaClass.getResource("/moving.mp4")!!.toExternalForm())
            )

            mediaView = MediaView(idlePlayer).apply {
                isPreserveRatio = true
                isSmooth = true
            }
        } catch (e: Exception) {
            println("Error loading media: ${e.message}")
            mediaView = MediaView()
            feedbackLabel = Label("Video missing - Logic Only Mode")
        }

        if (!this::feedbackLabel.isInitialized) feedbackLabel = Label("")
        feedbackLabel.style = "-fx-font-size: 24px; -fx-font-weight: bold;"

        val allKeys = listOf(
            KeyCode.A, KeyCode.D, KeyCode.W, KeyCode.S,
            KeyCode.C, KeyCode.Q, KeyCode.E,
            KeyCode.SHIFT, KeyCode.CONTROL
        )

        fun keyText(code: KeyCode?) =
            if (code == KeyCode.CONTROL) "CTRL" else code?.name ?: ""

        val key1Box = ComboBox<KeyCode>()
        val key2Box = ComboBox<KeyCode>()

        val cellFactory = javafx.util.Callback<ListView<KeyCode>, ListCell<KeyCode>> {
            object : ListCell<KeyCode>() {
                override fun updateItem(item: KeyCode?, empty: Boolean) {
                    super.updateItem(item, empty)
                    text = if (empty || item == null) "" else keyText(item)
                }
            }
        }

        key1Box.cellFactory = cellFactory
        key1Box.buttonCell = cellFactory.call(null)
        key2Box.cellFactory = cellFactory
        key2Box.buttonCell = cellFactory.call(null)

        fun rebuildKeyBoxes() {
            key1Box.onAction = null
            key2Box.onAction = null

            val k1 = firstKey
            val k2 = secondKey

            val k1Items = allKeys.filter { it != k2 }
            val k2Items = allKeys.filter { it != k1 }

            key1Box.items.setAll(k1Items)
            key2Box.items.setAll(k2Items)

            if (k1 != null && k1 in k1Items) key1Box.value = k1
            if (k2 != null && k2 in k2Items) key2Box.value = k2

            key1Box.setOnAction {
                firstKey = key1Box.value
                rebuildKeyBoxes()
            }
            key2Box.setOnAction {
                secondKey = key2Box.value
                rebuildKeyBoxes()
            }

            key1Box.scene?.root?.requestFocus()
        }

        rebuildKeyBoxes()

        val msValueLabel = Label(passThresholdMs.toString()).apply {
            style = "-fx-underline: true; -fx-font-size: 16px; -fx-cursor: hand;"
            tooltip = Tooltip("Click to edit threshold (10-200ms)")
        }
        val msSuffixLabel = Label(" ms")
        val msTextField = TextField()

        msTextField.textFormatter = TextFormatter<String> { change ->
            if (change.controlNewText.matches(Regex("\\d*"))) change else null
        }

        val msBox = HBox(4.0, msValueLabel, msSuffixLabel).apply {
            alignment = Pos.CENTER
        }

        fun commitMsEdit() {
            val text = msTextField.text
            val parsed = if (text.isEmpty()) 100 else (text.toIntOrNull() ?: 100)
            passThresholdMs = parsed.coerceIn(10, 200)

            msValueLabel.text = passThresholdMs.toString()
            msBox.children.setAll(msValueLabel, msSuffixLabel)

            msBox.scene?.root?.requestFocus()
        }

        msValueLabel.setOnMouseClicked { event ->
            msTextField.text = passThresholdMs.toString()
            msBox.children.setAll(msTextField, msSuffixLabel)
            msTextField.requestFocus()
            msTextField.selectAll()
            event.consume()
        }

        msTextField.setOnAction { commitMsEdit() }

        msTextField.focusedProperty().addListener { _, _, focused ->
            if (!focused) commitMsEdit()
        }

        val bottomRow = VBox(
            10.0,
            feedbackLabel,
            HBox(20.0, Label("Keys:"), key1Box, key2Box, Label("Threshold:"), msBox).apply {
                alignment = Pos.CENTER
            }
        ).apply {
            alignment = Pos.CENTER
            prefHeight = 120.0
        }

        val root = VBox(mediaView, bottomRow).apply {
            alignment = Pos.TOP_CENTER
        }
        VBox.setVgrow(mediaView, Priority.ALWAYS)

        val scene = Scene(root, 900.0, 600.0)

        mediaView.fitWidthProperty().bind(scene.widthProperty())
        mediaView.fitHeightProperty().bind(scene.heightProperty().subtract(bottomRow.prefHeight))

        scene.setOnKeyPressed { e ->
            if (currentState == State.IDLE && (e.code == firstKey || e.code == secondKey)) {
                activeKey = e.code
                currentState = State.HOLDING
                feedbackLabel.text = "HOLDING ${keyText(activeKey)}"
                feedbackLabel.style = "-fx-text-fill: black;"

                if(this::movingPlayer.isInitialized) {
                    movingPlayer.stop()
                    movingPlayer.seek(Duration.ZERO)
                    mediaView.mediaPlayer = movingPlayer
                    movingPlayer.play()
                }
            }
            else if (currentState == State.WAITING_FOR_SWITCH) {
                if (e.code != activeKey && (e.code == firstKey || e.code == secondKey)) {
                    val delta = System.currentTimeMillis() - releaseTime
                    evaluateCounterstrafe(delta)
                }
            }
        }

        scene.setOnKeyReleased { e ->
            if (currentState == State.HOLDING && e.code == activeKey) {
                releaseTime = System.currentTimeMillis()
                currentState = State.WAITING_FOR_SWITCH
                feedbackLabel.text = "WAITING..."

                autoFailTimeline?.stop()
                autoFailTimeline = Timeline(
                    KeyFrame(
                        Duration.seconds(1.0),
                        EventHandler<ActionEvent> { evaluateCounterstrafe(1001L) }
                    )
                ).apply { play() }
            }
        }

        root.setOnMouseClicked { root.requestFocus() }

        stage.scene = scene
        stage.title = "Counterstrafe Trainer"
        stage.show()
        root.requestFocus()
    }

    private fun evaluateCounterstrafe(deltaMs: Long) {
        autoFailTimeline?.stop()
        if (this::movingPlayer.isInitialized) movingPlayer.pause()

        if (deltaMs <= passThresholdMs) {
            currentState = State.SUCCESS
            feedbackLabel.text = "SUCCESS – ${deltaMs}ms"
            feedbackLabel.style = "-fx-text-fill: green; -fx-font-size: 24px; -fx-font-weight: bold;"
            Timeline(
                KeyFrame(Duration.seconds(1.5), EventHandler { playIdle() })
            ).play()
        } else {
            currentState = State.FAIL
            feedbackLabel.text = "FAIL – ${deltaMs}ms"
            feedbackLabel.style = "-fx-text-fill: red; -fx-font-size: 24px; -fx-font-weight: bold;"
            Timeline(
                KeyFrame(Duration.seconds(1.5), EventHandler { playIdle() })
            ).play()
        }
    }

    private fun playIdle() {
        if(this::movingPlayer.isInitialized) movingPlayer.stop()
        if(this::idlePlayer.isInitialized) {
            idlePlayer.seek(Duration.ZERO)
            mediaView.mediaPlayer = idlePlayer
            idlePlayer.play()
        }
        feedbackLabel.text = "Ready"
        feedbackLabel.style = "-fx-text-fill: black;"
        currentState = State.IDLE
    }
}

fun main() {
    Application.launch(Main::class.java)
}