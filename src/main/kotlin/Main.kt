// Main.kt
import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.application.Application
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.*
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.scene.media.MediaView
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.scene.text.FontWeight
import javafx.stage.Stage
import javafx.util.Duration
import kotlin.math.max

class Main : Application() {

    private var firstKey: KeyCode = KeyCode.A
    private var secondKey: KeyCode = KeyCode.D
    private var passThresholdMs: Int = 100

    private var isMuted = true

    private var currentRound = 0
    private var totalScore = 0.0
    private val maxRounds = 10

    private lateinit var idlePlayer: MediaPlayer
    private lateinit var movingPlayer: MediaPlayer
    private lateinit var mediaView: MediaView

    private lateinit var statusLabel: Label
    private lateinit var stage: Stage
    private lateinit var mainScene: Scene

    private var autoFailTimeline: Timeline? = null
    private var transitionTimeline: Timeline? = null

    enum class GameState { IDLE, HOLDING, WAITING_FOR_SWITCH, RESULT_DISPLAY }
    private var currentState = GameState.IDLE
    private var activeKey: KeyCode? = null

    private var pressTime = 0L
    private var releaseTime = 0L

    override fun start(primaryStage: Stage) {
        this.stage = primaryStage

        try {
            idlePlayer = MediaPlayer(
                Media(javaClass.getResource("/idle.mp4")!!.toExternalForm())
            ).apply { cycleCount = MediaPlayer.INDEFINITE }

            movingPlayer = MediaPlayer(
                Media(javaClass.getResource("/moving.mp4")!!.toExternalForm())
            )

            updateVolume()

            mediaView = MediaView(idlePlayer).apply {
                isPreserveRatio = true
                isSmooth = true
            }
        } catch (e: Exception) {
            println("Error loading media: ${e.message}")
            mediaView = MediaView()
        }

        stage.title = "Counterstrafe Trainer"

        mainScene = Scene(StackPane(), 900.0, 600.0)
        stage.scene = mainScene

        showMenu()
        stage.show()
    }

    private fun updateVolume() {
        val volume = if (isMuted) 0.0 else 1.0
        if (this::idlePlayer.isInitialized) idlePlayer.volume = volume
        if (this::movingPlayer.isInitialized) movingPlayer.volume = volume
    }

    private fun showMenu() {
        autoFailTimeline?.stop()
        transitionTimeline?.stop()

        if (this::idlePlayer.isInitialized) idlePlayer.stop()
        if (this::movingPlayer.isInitialized) movingPlayer.stop()

        mainScene.setOnKeyPressed(null)
        mainScene.setOnKeyReleased(null)

        val titleLabel = Label("Counterstrafe Trainer").apply {
            font = Font.font("Arial", FontWeight.BOLD, 32.0)
            textFill = Color.WHITE
        }

        val allKeys = listOf(
            KeyCode.A, KeyCode.D, KeyCode.W, KeyCode.S,
            KeyCode.C, KeyCode.Q, KeyCode.E,
            KeyCode.SHIFT, KeyCode.CONTROL
        )

        fun keyText(code: KeyCode?) = if (code == KeyCode.CONTROL) "CTRL" else code?.name ?: ""

        val cellFactory = javafx.util.Callback<ListView<KeyCode>, ListCell<KeyCode>> {
            object : ListCell<KeyCode>() {
                override fun updateItem(item: KeyCode?, empty: Boolean) {
                    super.updateItem(item, empty)
                    text = if (empty || item == null) "" else keyText(item)
                }
            }
        }

        val key1Box = ComboBox<KeyCode>()
        val key2Box = ComboBox<KeyCode>()

        key1Box.cellFactory = cellFactory
        key1Box.buttonCell = cellFactory.call(null)
        key2Box.cellFactory = cellFactory
        key2Box.buttonCell = cellFactory.call(null)

        fun refreshKeyBoxes() {
            key1Box.onAction = null
            key2Box.onAction = null

            val currentK1 = firstKey
            val currentK2 = secondKey

            val k1Options = allKeys.filter { it != currentK2 }
            val k2Options = allKeys.filter { it != currentK1 }

            key1Box.items.setAll(k1Options)
            key2Box.items.setAll(k2Options)

            key1Box.value = if (currentK1 in k1Options) currentK1 else k1Options.first()
            key2Box.value = if (currentK2 in k2Options) currentK2 else k2Options.first()

            firstKey = key1Box.value
            secondKey = key2Box.value

            key1Box.setOnAction {
                firstKey = key1Box.value
                refreshKeyBoxes()
            }
            key2Box.setOnAction {
                secondKey = key2Box.value
                refreshKeyBoxes()
            }
        }

        refreshKeyBoxes()

        val msField = TextField(passThresholdMs.toString()).apply {
            prefWidth = 60.0
            textFormatter = TextFormatter<String> { change ->
                if (change.controlNewText.matches(Regex("\\d*"))) change else null
            }
        }

        fun commitMs() {
            val input = msField.text.toIntOrNull() ?: 100
            val clamped = input.coerceIn(10, 200)
            passThresholdMs = clamped
            msField.text = clamped.toString()
        }

        msField.setOnAction { commitMs() }
        msField.focusedProperty().addListener { _, _, focused ->
            if (!focused) commitMs()
        }

        val soundBtn = Button(if (isMuted) "SOUND" else "MUTE").apply {
            font = Font.font("Arial", FontWeight.BOLD, 14.0)
            setMaxWidth(Double.MAX_VALUE)
            setOnAction {
                isMuted = !isMuted
                text = if (isMuted) "SOUND" else "MUTE"
                updateVolume()
            }
        }

        val settingsGrid = GridPane().apply {
            hgap = 10.0
            vgap = 10.0
            alignment = Pos.CENTER

            val l1 = Label("Key 1:").apply { textFill = Color.WHITE }
            val l2 = Label("Key 2:").apply { textFill = Color.WHITE }
            val l3 = Label("Threshold (ms):").apply { textFill = Color.WHITE }

            addRow(0, l1, key1Box)
            addRow(1, l2, key2Box)
            addRow(2, l3, msField)
            add(soundBtn, 0, 3, 2, 1)
        }

        val playButton = Button("PLAY").apply {
            font = Font.font("Arial", FontWeight.BOLD, 20.0)
            prefWidth = 200.0
            setOnAction {
                commitMs()
                startGame()
            }
        }

        val root = VBox(30.0, titleLabel, settingsGrid, playButton).apply {
            alignment = Pos.CENTER
            padding = javafx.geometry.Insets(20.0)
            style = "-fx-background-color: black;"
        }

        mainScene.root = root
    }

    private fun startGame() {
        currentRound = 0
        totalScore = 0.0
        currentState = GameState.IDLE

        statusLabel = Label("Ready - Round 1/10").apply {
            style = """
                -fx-background-color: white; 
                -fx-text-fill: black; 
                -fx-padding: 10px 20px; 
                -fx-background-radius: 10px; 
                -fx-border-color: #333; 
                -fx-border-radius: 10px; 
                -fx-font-size: 18px; 
                -fx-font-weight: bold;
                -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.4), 10, 0, 0, 2);
            """.trimIndent()
        }

        val overlayBox = VBox(statusLabel).apply {
            alignment = Pos.TOP_CENTER
            padding = javafx.geometry.Insets(20.0, 0.0, 0.0, 0.0)
            isPickOnBounds = false
        }

        val gameRoot = StackPane().apply {
            alignment = Pos.CENTER
            children.addAll(mediaView, overlayBox)
            style = "-fx-background-color: black;"
        }

        mediaView.fitWidthProperty().bind(mainScene.widthProperty())
        mediaView.fitHeightProperty().bind(mainScene.heightProperty())

        mainScene.root = gameRoot

        mainScene.setOnKeyPressed { e -> handleKeyPress(e) }
        mainScene.setOnKeyReleased { e -> handleKeyRelease(e) }

        gameRoot.setOnMouseClicked { gameRoot.requestFocus() }
        gameRoot.requestFocus()

        playIdleVideo()
    }

    private fun handleKeyPress(e: KeyEvent) {
        if (e.code == KeyCode.ESCAPE && currentRound < 9) {
            showMenu()
            return
        }

        if (currentState == GameState.IDLE) {
            if (e.code == firstKey || e.code == secondKey) {
                activeKey = e.code
                pressTime = System.currentTimeMillis()
                currentState = GameState.HOLDING
                updateStatus("HOLDING ${e.code.name}", false)
                playMovingVideo()
            }
        } else if (currentState == GameState.HOLDING) {
            if (e.code != activeKey && (e.code == firstKey || e.code == secondKey)) {
                triggerRoundEnd(false, "FAIL - Overlap", 0.0)
            }
        } else if (currentState == GameState.WAITING_FOR_SWITCH) {
            if (e.code != activeKey && (e.code == firstKey || e.code == secondKey)) {
                val delta = System.currentTimeMillis() - releaseTime
                evaluateRound(delta, true)
            }
        }
    }

    private fun handleKeyRelease(e: KeyEvent) {
        if (currentState == GameState.HOLDING && e.code == activeKey) {
            val holdDuration = System.currentTimeMillis() - pressTime
            if (holdDuration < 80) {
                if (this::movingPlayer.isInitialized) movingPlayer.pause()
                triggerRoundEnd(false, "FAIL - ${passThresholdMs}ms", 0.0)
                return
            }

            releaseTime = System.currentTimeMillis()
            currentState = GameState.WAITING_FOR_SWITCH
            updateStatus("RELEASED...", false)

            autoFailTimeline?.stop()
            autoFailTimeline = Timeline(
                KeyFrame(Duration.seconds(1.0), EventHandler { evaluateRound(1001L, false) })
            ).apply { play() }
        }
    }

    private fun evaluateRound(deltaMs: Long, keyHit: Boolean) {
        val passed = keyHit && deltaMs <= passThresholdMs

        if (passed) {
            val safeMs = max(1L, deltaMs)
            val roundScore = 1000.0 / safeMs.toDouble()
            triggerRoundEnd(true, "SUCCESS! ${deltaMs}ms (+${roundScore.toInt()} pts)", roundScore)
        } else {
            triggerRoundEnd(false, "FAIL (${deltaMs}ms)", 0.0)
        }
    }

    private fun triggerRoundEnd(success: Boolean, message: String, scoreToAdd: Double) {
        autoFailTimeline?.stop()
        if (this::movingPlayer.isInitialized) movingPlayer.pause()

        currentState = GameState.RESULT_DISPLAY
        totalScore += scoreToAdd

        statusLabel.text = message
        val colorStyle = if (success) "-fx-text-fill: green;" else "-fx-text-fill: red;"
        statusLabel.style = statusLabel.style.replace("-fx-text-fill: black;", colorStyle)

        currentRound++

        transitionTimeline = Timeline(KeyFrame(Duration.seconds(2.0), EventHandler {
            if (currentRound >= maxRounds) {
                showScoreScreen()
            } else {
                nextRound()
            }
        }))
        transitionTimeline?.play()
    }

    private fun nextRound() {
        currentState = GameState.IDLE
        activeKey = null
        playIdleVideo()
        updateStatus("Ready - Round ${currentRound + 1}/$maxRounds", true)
    }

    private fun updateStatus(text: String, resetColor: Boolean) {
        statusLabel.text = text
        if (resetColor) {
            statusLabel.style = statusLabel.style
                .replace("-fx-text-fill: green;", "-fx-text-fill: black;")
                .replace("-fx-text-fill: red;", "-fx-text-fill: black;")
        }
    }

    private fun playIdleVideo() {
        if (this::movingPlayer.isInitialized) movingPlayer.stop()
        if (this::idlePlayer.isInitialized) {
            idlePlayer.seek(Duration.ZERO)
            mediaView.mediaPlayer = idlePlayer
            idlePlayer.play()
        }
    }

    private fun playMovingVideo() {
        if (this::movingPlayer.isInitialized) {
            movingPlayer.stop()
            movingPlayer.seek(Duration.ZERO)
            mediaView.mediaPlayer = movingPlayer
            movingPlayer.play()
        }
    }

    private fun showScoreScreen() {
        if (this::idlePlayer.isInitialized) idlePlayer.stop()
        if (this::movingPlayer.isInitialized) movingPlayer.stop()

        mainScene.setOnKeyReleased(null)

        val gameOverLabel = Label("Training Complete").apply {
            font = Font.font("Arial", FontWeight.BOLD, 40.0)
            textFill = Color.WHITE
        }

        val finalScoreInt = totalScore.toInt()
        val scoreLabel = Label("Final Score: $finalScoreInt / 1000").apply {
            font = Font.font("Arial", FontWeight.NORMAL, 28.0)
            style = if(finalScoreInt > 500) "-fx-text-fill: lightgreen;" else "-fx-text-fill: #ff6666;"
        }

        val subText = Label("Rounds: $maxRounds | Threshold: ${passThresholdMs}ms").apply {
            font = Font.font("Arial", 16.0)
            textFill = Color.WHITE
        }

        val instructLabel = Label("Press ENTER to return to Menu").apply {
            font = Font.font("Arial", FontWeight.BOLD, 14.0)
            padding = javafx.geometry.Insets(40.0, 0.0, 0.0, 0.0)
            textFill = Color.WHITE
        }

        val root = VBox(20.0, gameOverLabel, scoreLabel, subText, instructLabel).apply {
            alignment = Pos.CENTER
            style = "-fx-background-color: black;"
        }

        mainScene.root = root

        mainScene.setOnKeyPressed { e ->
            if (e.code == KeyCode.ENTER) {
                showMenu()
            }
        }
    }
}

fun main() {
    Application.launch(Main::class.java)
}