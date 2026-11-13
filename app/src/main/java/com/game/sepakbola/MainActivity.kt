package com.game.sepakbola

import android.app.Activity
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class ScreenState {
    TEAM_SELECT,
    MATCH_PLAY,
    FULL_TIME
}

data class TeamConfig(
    val id: Int,
    val name: String,
    val code: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val speedStat: Int,     // 1 - 10
    val powerStat: Int,     // 1 - 10
    val defenseStat: Int    // 1 - 10
)

enum class Role {
    GK, DEFENDER, STRIKER
}

class PlayerEntity(
    val id: Int,
    val isHome: Boolean,
    val role: Role,
    val number: Int,
    var x: Float,
    var y: Float,
    val homeX: Float,
    val homeY: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var facingAngle: Float = 0f,
    var stamina: Float = 100f
)

class BallEntity(
    var x: Float = 0.5f,
    var y: Float = 0.5f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var owner: PlayerEntity? = null,
    var stealCooldown: Float = 0f
)

class SoundFX {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var toneGen: ToneGenerator? = null

    init {
        try {
            toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 85)
        } catch (_: Exception) {
            toneGen = null
        }
    }

    fun playKick() {
        scope.launch {
            try {
                toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 45)
            } catch (_: Exception) {}
        }
    }

    fun playWhistle() {
        scope.launch {
            try {
                toneGen?.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 280)
                delay(300)
                toneGen?.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 450)
            } catch (_: Exception) {}
        }
    }

    fun playGoal() {
        scope.launch {
            try {
                toneGen?.startTone(ToneGenerator.TONE_DTMF_9, 160)
                delay(180)
                toneGen?.startTone(ToneGenerator.TONE_DTMF_D, 160)
                delay(180)
                toneGen?.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 500)
            } catch (_: Exception) {}
        }
    }

    fun release() {
        try {
            toneGen?.release()
        } catch (_: Exception) {}
    }
}

val AVAILABLE_TEAMS = listOf(
    TeamConfig(0, "INDONESIA", "IDN", Color(0xFFD50000), Color(0xFFFFFFFF), 9, 8, 8),
    TeamConfig(1, "BRAZIL", "BRA", Color(0xFFFFD600), Color(0xFF004D40), 9, 9, 8),
    TeamConfig(2, "ARGENTINA", "ARG", Color(0xFF29B6F6), Color(0xFFFFFFFF), 9, 9, 9),
    TeamConfig(3, "FRANCE", "FRA", Color(0xFF1565C0), Color(0xFFE53935), 10, 9, 8),
    TeamConfig(4, "GERMANY", "GER", Color(0xFFEEEEEE), Color(0xFF212121), 8, 10, 9),
    TeamConfig(5, "JAPAN", "JPN", Color(0xFF0D47A1), Color(0xFFFFFFFF), 10, 7, 8),
    TeamConfig(6, "ENGLAND", "ENG", Color(0xFFFFFFFF), Color(0xFFC62828), 8, 9, 8),
    TeamConfig(7, "PORTUGAL", "POR", Color(0xFFB71C1C), Color(0xFF1B5E20), 9, 10, 8)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0B132B)
                ) {
                    FootballGameApp()
                }
            }
        }
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

@Composable
fun FootballGameApp() {
    val context = LocalContext.current
    val soundFX = remember { SoundFX() }

    DisposableEffect(Unit) {
        onDispose {
            soundFX.release()
        }
    }

    var screenState by remember { mutableStateOf(ScreenState.TEAM_SELECT) }
    var homeTeam by remember { mutableStateOf(AVAILABLE_TEAMS[0]) }
    var awayTeam by remember { mutableStateOf(AVAILABLE_TEAMS[1]) }
    var matchDurationSeconds by remember { mutableIntStateOf(90) }

    var finalHomeScore by remember { mutableIntStateOf(0) }
    var finalAwayScore by remember { mutableIntStateOf(0) }
    var finalHomeShots by remember { mutableIntStateOf(0) }
    var finalAwayShots by remember { mutableIntStateOf(0) }

    LaunchedEffect(screenState) {
        (context as? Activity)?.requestedOrientation =
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    when (screenState) {
        ScreenState.TEAM_SELECT -> {
            TeamSelectScreen(
                homeTeam = homeTeam,
                awayTeam = awayTeam,
                matchDuration = matchDurationSeconds,
                onSelectHome = {
                    homeTeam = it
                    if (awayTeam.id == it.id) {
                        awayTeam = AVAILABLE_TEAMS[(it.id + 1) % AVAILABLE_TEAMS.size]
                    }
                    soundFX.playKick()
                },
                onSelectAway = {
                    awayTeam = it
                    if (homeTeam.id == it.id) {
                        homeTeam = AVAILABLE_TEAMS[(it.id + 1) % AVAILABLE_TEAMS.size]
                    }
                    soundFX.playKick()
                },
                onChangeDuration = {
                    matchDurationSeconds = it
                    soundFX.playKick()
                },
                onStartMatch = {
                    soundFX.playWhistle()
                    screenState = ScreenState.MATCH_PLAY
                }
            )
        }

        ScreenState.MATCH_PLAY -> {
            MatchPlayScreen(
                homeTeam = homeTeam,
                awayTeam = awayTeam,
                matchDurationSeconds = matchDurationSeconds,
                soundFX = soundFX,
                onFullTime = { hScore, aScore, hShots, aShots ->
                    finalHomeScore = hScore
                    finalAwayScore = aScore
                    finalHomeShots = hShots
                    finalAwayShots = aShots
                    soundFX.playWhistle()
                    screenState = ScreenState.FULL_TIME
                }
            )
        }

        ScreenState.FULL_TIME -> {
            FullTimeScreen(
                homeTeam = homeTeam,
                awayTeam = awayTeam,
                homeScore = finalHomeScore,
                awayScore = finalAwayScore,
                homeShots = finalHomeShots,
                awayShots = finalAwayShots,
                onRematch = {
                    soundFX.playWhistle()
                    screenState = ScreenState.MATCH_PLAY
                },
                onChangeTeams = {
                    soundFX.playKick()
                    screenState = ScreenState.TEAM_SELECT
                }
            )
        }
    }
}

@Composable
fun TeamSelectScreen(
    homeTeam: TeamConfig,
    awayTeam: TeamConfig,
    matchDuration: Int,
    onSelectHome: (TeamConfig) -> Unit,
    onSelectAway: (TeamConfig) -> Unit,
    onChangeDuration: (Int) -> Unit,
    onStartMatch: () -> Unit
) {
    var selectingForHome by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0A1128), Color(0xFF1C2541), Color(0xFF0B132B))
                )
            )
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "2D ULTIMATE FOOTBALL (3v3)",
            color = Color(0xFFFFD700),
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TeamShowcaseCard(
                label = "TIM ANDA (HOME)",
                team = homeTeam,
                isActive = selectingForHome,
                onClick = { selectingForHome = true },
                modifier = Modifier.weight(1f)
            )

            Column(
                modifier = Modifier.padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "VS",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(60, 90, 120).forEach { sec ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (matchDuration == sec) Color(0xFF00E676)
                                    else Color(0xFF3A506B)
                                )
                                .clickable { onChangeDuration(sec) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${sec}s",
                                color = if (matchDuration == sec) Color.Black else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            TeamShowcaseCard(
                label = "LAWAN AI (AWAY)",
                team = awayTeam,
                isActive = !selectingForHome,
                onClick = { selectingForHome = false },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = if (selectingForHome) "Ketuk bendera di bawah untuk memilih TIM ANDA:"
            else "Ketuk bendera di bawah untuk memilih TIM LAWAN (AI):",
            color = Color(0xFF80FFDB),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(6.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(AVAILABLE_TEAMS) { _, team ->
                val isSelectedHome = team.id == homeTeam.id
                val isSelectedAway = team.id == awayTeam.id
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1F2833))
                        .border(
                            width = 2.dp,
                            color = when {
                                isSelectedHome -> Color(0xFF00E676)
                                isSelectedAway -> Color(0xFFFF5252)
                                else -> Color.Transparent
                            },
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable {
                            if (selectingForHome) onSelectHome(team) else onSelectAway(team)
                        }
                        .padding(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(team.primaryColor)
                                .border(2.dp, team.secondaryColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = team.code,
                                color = if (team.primaryColor == Color.White || team.primaryColor == Color(0xFFEEEEEE) || team.primaryColor == Color(0xFFFFD600)) Color.Black else Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = team.name,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "SPD:${team.speedStat} POW:${team.powerStat} DEF:${team.defenseStat}",
                                color = Color.LightGray,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onStartMatch,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(44.dp)
        ) {
            Text(
                text = "KICK OFF PERTANDINGAN",
                color = Color.Black,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
fun TeamShowcaseCard(
    label: String,
    team: TeamConfig,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF16213E))
            .border(
                width = if (isActive) 2.5.dp else 1.dp,
                color = if (isActive) Color(0xFFFFD700) else Color(0xFF3A506B),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(team.primaryColor)
                .border(3.dp, team.secondaryColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = team.code,
                color = if (team.primaryColor == Color.White || team.primaryColor == Color(0xFFEEEEEE) || team.primaryColor == Color(0xFFFFD600)) Color.Black else Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = label,
                color = Color(0xFFFFD700),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = team.name,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Speed ${team.speedStat} | Power ${team.powerStat} | Def ${team.defenseStat}",
                color = Color(0xFFB0BEC5),
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun MatchPlayScreen(
    homeTeam: TeamConfig,
    awayTeam: TeamConfig,
    matchDurationSeconds: Int,
    soundFX: SoundFX,
    onFullTime: (Int, Int, Int, Int) -> Unit
) {
    // Pitch normalized coordinates: X in [0.05..0.95], Y in [0.08..0.92]
    val pitchLeft = 0.05f
    val pitchRight = 0.95f
    val pitchTop = 0.08f
    val pitchBottom = 0.92f
    val goalTop = 0.36f
    val goalBottom = 0.64f

    var homeScore by remember { mutableIntStateOf(0) }
    var awayScore by remember { mutableIntStateOf(0) }
    var homeShots by remember { mutableIntStateOf(0) }
    var awayShots by remember { mutableIntStateOf(0) }
    var timeRemaining by remember { mutableFloatStateOf(matchDurationSeconds.toFloat()) }
    var goalBannerText by remember { mutableStateOf<String?>(null) }
    var goalPauseTimer by remember { mutableFloatStateOf(0f) }

    // Multi-touch controller states
    var joyX by remember { mutableFloatStateOf(0f) }
    var joyY by remember { mutableFloatStateOf(0f) }
    var isSprinting by remember { mutableStateOf(false) }

    // Players (3v3: 0=GK, 1=Defender, 2=Striker for Home; 3=GK, 4=Defender, 5=Striker for Away)
    val players = remember {
        listOf(
            PlayerEntity(0, true, Role.GK, 1, 0.09f, 0.50f, 0.09f, 0.50f, facingAngle = 0f),
            PlayerEntity(1, true, Role.DEFENDER, 4, 0.26f, 0.34f, 0.26f, 0.34f, facingAngle = 0f),
            PlayerEntity(2, true, Role.STRIKER, 9, 0.42f, 0.55f, 0.38f, 0.55f, facingAngle = 0f),

            PlayerEntity(3, false, Role.GK, 1, 0.91f, 0.50f, 0.91f, 0.50f, facingAngle = Math.PI.toFloat()),
            PlayerEntity(4, false, Role.DEFENDER, 5, 0.74f, 0.66f, 0.74f, 0.66f, facingAngle = Math.PI.toFloat()),
            PlayerEntity(5, false, Role.STRIKER, 10, 0.58f, 0.45f, 0.62f, 0.45f, facingAngle = Math.PI.toFloat())
        )
    }

    var controlledPlayerIndex by remember { mutableIntStateOf(2) }
    val ball = remember { BallEntity(x = 0.5f, y = 0.5f) }
    var frameTick by remember { mutableLongStateOf(0L) }

    fun resetPositionsAfterGoal() {
        players[0].apply { x = 0.09f; y = 0.50f; vx = 0f; vy = 0f; facingAngle = 0f }
        players[1].apply { x = 0.26f; y = 0.34f; vx = 0f; vy = 0f; facingAngle = 0f }
        players[2].apply { x = 0.43f; y = 0.52f; vx = 0f; vy = 0f; facingAngle = 0f }
        players[3].apply { x = 0.91f; y = 0.50f; vx = 0f; vy = 0f; facingAngle = Math.PI.toFloat() }
        players[4].apply { x = 0.74f; y = 0.66f; vx = 0f; vy = 0f; facingAngle = Math.PI.toFloat() }
        players[5].apply { x = 0.57f; y = 0.48f; vx = 0f; vy = 0f; facingAngle = Math.PI.toFloat() }
        ball.apply {
            x = 0.5f
            y = 0.5f
            vx = 0f
            vy = 0f
            owner = null
            stealCooldown = 0.3f
        }
        controlledPlayerIndex = 2
    }

    // Action triggers called from right-hand buttons
    fun performPassOrSwitch() {
        val active = players[controlledPlayerIndex]
        if (ball.owner == active) {
            // Find best home teammate
            val teammates = players.filter { it.isHome && it.id != active.id }
            val target = teammates.maxByOrNull { it.x } ?: teammates.first()
            val angle = atan2(target.y - active.y, target.x - active.x)
            val passSpeed = 0.62f + (homeTeam.powerStat * 0.018f)
            ball.owner = null
            ball.stealCooldown = 0.22f
            ball.vx = cos(angle) * passSpeed
            ball.vy = sin(angle) * passSpeed
            active.facingAngle = angle
            if (target.role != Role.GK) {
                controlledPlayerIndex = target.id
            }
            soundFX.playKick()
        } else {
            // Switch to closest home player to the ball (prefer outfield, allow GK if ball is very close)
            val candidates = players.filter { it.isHome && it.id != active.id }
            val closest = candidates.minByOrNull { hypot(it.x - ball.x, it.y - ball.y) }
            if (closest != null) {
                controlledPlayerIndex = closest.id
                soundFX.playKick()
            }
        }
    }

    fun performShootOrTackle() {
        val active = players[controlledPlayerIndex]
        if (ball.owner == active) {
            // Shoot towards opponent goal (right side) with joystick aim influence
            val targetX = pitchRight + 0.02f
            val aimOffsetY = (joyY * 0.12f).coerceIn(-0.11f, 0.11f)
            val targetY = 0.5f + aimOffsetY
            val angle = atan2(targetY - active.y, targetX - active.x)
            val shotSpeed = 0.85f + (homeTeam.powerStat * 0.025f)
            ball.owner = null
            ball.stealCooldown = 0.28f
            ball.vx = cos(angle) * shotSpeed
            ball.vy = sin(angle) * shotSpeed
            active.facingAngle = angle
            homeShots++
            soundFX.playKick()
        } else {
            // Lunging tackle towards ball
            val dist = hypot(ball.x - active.x, ball.y - active.y)
            val angle = atan2(ball.y - active.y, ball.x - active.x)
            active.facingAngle = angle
            active.vx = cos(angle) * 0.45f
            active.vy = sin(angle) * 0.45f
            if (dist < 0.065f && ball.stealCooldown <= 0f) {
                ball.owner = active
                ball.stealCooldown = 0.35f
                soundFX.playKick()
            }
        }
    }

    // Main 60 FPS Game Loop
    LaunchedEffect(Unit) {
        var lastNanos = 0L
        while (timeRemaining > 0f) {
            withFrameNanos { nanos ->
                if (lastNanos == 0L) lastNanos = nanos
                val dt = ((nanos - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                lastNanos = nanos
                frameTick++

                if (goalPauseTimer > 0f) {
                    goalPauseTimer -= dt
                    if (goalPauseTimer <= 0f) {
                        goalBannerText = null
                        resetPositionsAfterGoal()
                    }
                    return@withFrameNanos
                }

                timeRemaining = max(0f, timeRemaining - dt)
                if (ball.stealCooldown > 0f) {
                    ball.stealCooldown = max(0f, ball.stealCooldown - dt)
                }

                // Auto-switch controlled player if another Home player gets possession
                val currentOwner = ball.owner
                if (currentOwner != null && currentOwner.isHome && currentOwner.id != controlledPlayerIndex) {
                    controlledPlayerIndex = currentOwner.id
                }

                // Update all 6 players
                players.forEach { p ->
                    val teamCfg = if (p.isHome) homeTeam else awayTeam
                    val baseMaxSpeed = 0.22f + (teamCfg.speedStat * 0.009f)

                    if (p.isHome && p.id == controlledPlayerIndex) {
                        // Human controlled player
                        val mag = hypot(joyX, joyY).coerceAtMost(1f)
                        val sprintMultiplier = if (isSprinting && p.stamina > 10f) {
                            p.stamina = max(0f, p.stamina - 28f * dt)
                            1.36f
                        } else {
                            p.stamina = min(100f, p.stamina + 16f * dt)
                            1.0f
                        }
                        if (mag > 0.08f) {
                            val angle = atan2(joyY, joyX)
                            p.facingAngle = angle
                            val speed = baseMaxSpeed * sprintMultiplier * mag
                            p.vx = cos(angle) * speed
                            p.vy = sin(angle) * speed
                        } else {
                            p.vx *= 0.75f
                            p.vy *= 0.75f
                        }
                    } else {
                        // AI Logic for Home Teammates & Away Opponents
                        p.stamina = min(100f, p.stamina + 15f * dt)
                        updateAIPlayer(
                            p = p,
                            ball = ball,
                            allPlayers = players,
                            teamCfg = teamCfg,
                            baseSpeed = baseMaxSpeed,
                            pitchLeft = pitchLeft,
                            pitchRight = pitchRight,
                            soundFX = soundFX,
                            onAwayShot = { awayShots++ }
                        )
                    }

                    // Apply velocity & clamp inside pitch
                    p.x = (p.x + p.vx * dt).coerceIn(pitchLeft + 0.015f, pitchRight - 0.015f)
                    p.y = (p.y + p.vy * dt).coerceIn(pitchTop + 0.02f, pitchBottom - 0.02f)
                }

                // Resolve player-to-player collisions lightly so they don't stack
                for (i in players.indices) {
                    for (j in i + 1 until players.size) {
                        val a = players[i]
                        val b = players[j]
                        val dx = b.x - a.x
                        val dy = b.y - a.y
                        val dist = hypot(dx, dy)
                        val minDist = 0.036f
                        if (dist in 0.0001f..minDist) {
                            val overlap = (minDist - dist) * 0.5f
                            val nx = dx / dist
                            val ny = dy / dist
                            a.x -= nx * overlap
                            a.y -= ny * overlap
                            b.x += nx * overlap
                            b.y += ny * overlap
                        }
                    }
                }

                // Update Ball Physics & Possession
                val owner = ball.owner
                if (owner != null) {
                    val dribbleOffset = 0.022f
                    ball.x = owner.x + cos(owner.facingAngle) * dribbleOffset
                    ball.y = owner.y + sin(owner.facingAngle) * dribbleOffset
                    ball.vx = owner.vx
                    ball.vy = owner.vy

                    // Check if an opponent tackles the ball carrier
                    if (ball.stealCooldown <= 0f) {
                        val tackler = players.firstOrNull {
                            it.isHome != owner.isHome &&
                                hypot(it.x - ball.x, it.y - ball.y) < 0.031f
                        }
                        if (tackler != null) {
                            ball.owner = tackler
                            ball.stealCooldown = 0.38f
                            if (tackler.isHome) {
                                controlledPlayerIndex = tackler.id
                            }
                            soundFX.playKick()
                        }
                    }
                } else {
                    // Free ball movement
                    ball.x += ball.vx * dt
                    ball.y += ball.vy * dt
                    // Grass friction
                    val friction = 1f - (1.85f * dt).coerceAtMost(0.15f)
                    ball.vx *= friction
                    ball.vy *= friction

                    // Check Goal Scored BEFORE wall bounce on X
                    val inGoalMouthY = ball.y in goalTop..goalBottom
                    if (ball.x <= pitchLeft && inGoalMouthY) {
                        // Away team scored!
                        awayScore++
                        goalBannerText = "GOL! ${awayTeam.name} MENCETAK SKOR!"
                        goalPauseTimer = 2.0f
                        soundFX.playGoal()
                        return@withFrameNanos
                    } else if (ball.x >= pitchRight && inGoalMouthY) {
                        // Home team scored!
                        homeScore++
                        goalBannerText = "GOOOLLL!! ${homeTeam.name}!"
                        goalPauseTimer = 2.0f
                        soundFX.playGoal()
                        return@withFrameNanos
                    }

                    // Bounce off top/bottom sidelines
                    if (ball.y < pitchTop + 0.012f) {
                        ball.y = pitchTop + 0.012f
                        ball.vy = -ball.vy * 0.75f
                    } else if (ball.y > pitchBottom - 0.012f) {
                        ball.y = pitchBottom - 0.012f
                        ball.vy = -ball.vy * 0.75f
                    }

                    // Bounce off left/right endlines outside goal mouth
                    if (ball.x < pitchLeft + 0.01f && !inGoalMouthY) {
                        ball.x = pitchLeft + 0.01f
                        ball.vx = -ball.vx * 0.75f
                    } else if (ball.x > pitchRight - 0.01f && !inGoalMouthY) {
                        ball.x = pitchRight - 0.01f
                        ball.vx = -ball.vx * 0.75f
                    }

                    // Check pickup by any player
                    if (ball.stealCooldown <= 0f) {
                        val catcher = players.minByOrNull { hypot(it.x - ball.x, it.y - ball.y) }
                        if (catcher != null && hypot(catcher.x - ball.x, catcher.y - ball.y) < 0.032f) {
                            ball.owner = catcher
                            ball.stealCooldown = 0.22f
                            if (catcher.isHome) {
                                controlledPlayerIndex = catcher.id
                            }
                            soundFX.playKick()
                        }
                    }
                }
            }
        }

        onFullTime(homeScore, awayScore, homeShots, awayShots)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 2D Pitch & Entities Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Read frameTick so Canvas redraws on every physics step
            val tickReader = frameTick

            // 1. Draw Outer Stadium Floor
            drawRect(color = Color(0xFF1B4332), size = size)

            val pLeft = pitchLeft * w
            val pRight = pitchRight * w
            val pTop = pitchTop * h
            val pBottom = pitchBottom * h
            val pWidth = pRight - pLeft
            val pHeight = pBottom - pTop

            // 2. Alternating Grass Stripes
            val stripeCount = 10
            val stripeWidth = pWidth / stripeCount
            for (i in 0 until stripeCount) {
                drawRect(
                    color = if (i % 2 == 0) Color(0xFF2D6A4F) else Color(0xFF40916C),
                    topLeft = Offset(pLeft + i * stripeWidth, pTop),
                    size = Size(stripeWidth, pHeight)
                )
            }

            // 3. Pitch Markings (Lines, Penalty Boxes, Center Circle)
            val lineStroke = Stroke(width = 3.dp.toPx())
            val lineColor = Color.White.copy(alpha = 0.85f)

            // Outer boundary
            drawRect(
                color = lineColor,
                topLeft = Offset(pLeft, pTop),
                size = Size(pWidth, pHeight),
                style = lineStroke
            )

            // Halfway line & Center Circle
            val midX = (pLeft + pRight) * 0.5f
            val midY = (pTop + pBottom) * 0.5f
            drawLine(
                color = lineColor,
                start = Offset(midX, pTop),
                end = Offset(midX, pBottom),
                strokeWidth = 3.dp.toPx()
            )
            drawCircle(
                color = lineColor,
                radius = pHeight * 0.18f,
                center = Offset(midX, midY),
                style = lineStroke
            )
            drawCircle(
                color = lineColor,
                radius = 4.dp.toPx(),
                center = Offset(midX, midY)
            )

            // Penalty Boxes
            val boxW = pWidth * 0.14f
            val boxH = pHeight * 0.52f
            val boxTop = midY - boxH * 0.5f
            drawRect(
                color = lineColor,
                topLeft = Offset(pLeft, boxTop),
                size = Size(boxW, boxH),
                style = lineStroke
            )
            drawRect(
                color = lineColor,
                topLeft = Offset(pRight - boxW, boxTop),
                size = Size(boxW, boxH),
                style = lineStroke
            )

            // 4. Goals (Left = Home Goal, Right = Away Goal)
            val gTop = goalTop * h
            val gBottom = goalBottom * h
            val gHeight = gBottom - gTop
            val gDepth = pLeft * 0.72f

            // Left Goal Net
            drawRect(
                color = Color.White.copy(alpha = 0.25f),
                topLeft = Offset(pLeft - gDepth, gTop),
                size = Size(gDepth, gHeight)
            )
            drawRect(
                color = homeTeam.primaryColor,
                topLeft = Offset(pLeft - gDepth, gTop),
                size = Size(gDepth, gHeight),
                style = Stroke(width = 4.dp.toPx())
            )

            // Right Goal Net
            drawRect(
                color = Color.White.copy(alpha = 0.25f),
                topLeft = Offset(pRight, gTop),
                size = Size(gDepth, gHeight)
            )
            drawRect(
                color = awayTeam.primaryColor,
                topLeft = Offset(pRight, gTop),
                size = Size(gDepth, gHeight),
                style = Stroke(width = 4.dp.toPx())
            )

            // 5. Draw Players
            val playerRadius = min(w, h) * 0.032f
            players.forEach { p ->
                val px = p.x * w
                val py = p.y * h
                val team = if (p.isHome) homeTeam else awayTeam

                // Controlled Player Highlight Ring
                if (p.isHome && p.id == controlledPlayerIndex) {
                    drawCircle(
                        color = Color(0xFF00E676),
                        radius = playerRadius * 1.48f,
                        center = Offset(px, py),
                        style = Stroke(width = 3.dp.toPx())
                    )
                }

                // Shadow
                drawCircle(
                    color = Color.Black.copy(alpha = 0.35f),
                    radius = playerRadius,
                    center = Offset(px + 2f, py + 4f)
                )

                // Jersey Fill
                val fillColor = if (p.role == Role.GK) {
                    if (p.isHome) Color(0xFFFF9100) else Color(0xFF00B0FF)
                } else {
                    team.primaryColor
                }
                drawCircle(
                    color = fillColor,
                    radius = playerRadius,
                    center = Offset(px, py)
                )

                // Jersey Trim
                drawCircle(
                    color = team.secondaryColor,
                    radius = playerRadius,
                    center = Offset(px, py),
                    style = Stroke(width = 2.5.dp.toPx())
                )

                // Direction Nose Indicator
                val dirX = px + cos(p.facingAngle) * (playerRadius * 0.85f)
                val dirY = py + sin(p.facingAngle) * (playerRadius * 0.85f)
                drawCircle(
                    color = team.secondaryColor,
                    radius = playerRadius * 0.24f,
                    center = Offset(dirX, dirY)
                )
            }

            // 6. Draw Football
            val bx = ball.x * w
            val by = ball.y * h
            val ballRadius = min(w, h) * 0.019f
            drawCircle(
                color = Color.Black.copy(alpha = 0.35f),
                radius = ballRadius,
                center = Offset(bx + 2f, by + 3f)
            )
            drawCircle(
                color = Color.White,
                radius = ballRadius,
                center = Offset(bx, by)
            )
            drawCircle(
                color = Color(0xFF212121),
                radius = ballRadius,
                center = Offset(bx, by),
                style = Stroke(width = 2.dp.toPx())
            )
            // Pentagon center dot on ball
            drawCircle(
                color = Color(0xFF212121),
                radius = ballRadius * 0.38f,
                center = Offset(bx, by)
            )
            if (tickReader < 0L) drawCircle(Color.Transparent, 1f)
        }

        // Top Broadcast Scoreboard & Timer HUD
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xDD0B132B))
                .border(1.dp, Color(0xFF3A506B), RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(homeTeam.primaryColor)
                        .border(1.dp, homeTeam.secondaryColor, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = homeTeam.code,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "$homeScore - $awayScore",
                color = Color(0xFFFFD700),
                fontSize = 18.sp,
                fontWeight = FontWeight.Black
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = awayTeam.code,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(awayTeam.primaryColor)
                        .border(1.dp, awayTeam.secondaryColor, CircleShape)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            val totalSec = timeRemaining.toInt()
            val mins = totalSec / 60
            val secs = totalSec % 60
            Text(
                text = "%02d:%02d".format(mins, secs),
                color = if (totalSec <= 10) Color(0xFFFF5252) else Color(0xFF00E676),
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        // Stamina Bar for Active Player (Top Left)
        val activeStamina = players[controlledPlayerIndex].stamina
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 10.dp)
        ) {
            Text(
                text = "STAMINA #${players[controlledPlayerIndex].number}",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Box(
                modifier = Modifier
                    .width(90.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0x88000000))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth((activeStamina / 100f).coerceIn(0f, 1f))
                        .background(
                            if (activeStamina > 30f) Color(0xFF00E676) else Color(0xFFFF5252)
                        )
                )
            }
        }

        // Goal Celebration Banner Overlay
        if (goalBannerText != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xEE0B132B))
                    .border(3.dp, Color(0xFFFFD700), RoundedCornerShape(16.dp))
                    .padding(horizontal = 32.dp, vertical = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = goalBannerText ?: "",
                    color = Color(0xFFFFD700),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
            }
        }

        // LEFT MULTI-TOUCH ZONE: Virtual Analog Joystick
        VirtualJoystick(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, bottom = 20.dp)
                .size(135.dp),
            onJoystickMoved = { x, y ->
                joyX = x
                joyY = y
            }
        )

        // RIGHT MULTI-TOUCH ZONE: Independent Pass/Switch, Shoot/Tackle, and Sprint Hold Buttons
        val hasBall = ball.owner?.isHome == true
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 22.dp, bottom = 18.dp)
                .size(width = 210.dp, height = 145.dp)
        ) {
            // PASS / SWITCH Button (Bottom-Left of Right Cluster)
            ArcadeActionButton(
                label = if (hasBall) "PASS" else "SWITCH",
                color = Color(0xFF2979FF),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .size(72.dp),
                onPressDown = { performPassOrSwitch() }
            )

            // SHOOT / TACKLE Button (Bottom-Right of Right Cluster)
            ArcadeActionButton(
                label = if (hasBall) "SHOOT" else "TACKLE",
                color = Color(0xFFFF1744),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(78.dp),
                onPressDown = { performShootOrTackle() }
            )

            // SPRINT Hold Button (Top-Center of Right Cluster)
            ArcadeHoldButton(
                label = "SPRINT",
                isActive = isSprinting,
                activeColor = Color(0xFFFFD600),
                idleColor = Color(0xFF00C853),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .size(66.dp),
                onHoldChanged = { holding -> isSprinting = holding }
            )
        }
    }
}

private fun updateAIPlayer(
    p: PlayerEntity,
    ball: BallEntity,
    allPlayers: List<PlayerEntity>,
    teamCfg: TeamConfig,
    baseSpeed: Float,
    pitchLeft: Float,
    pitchRight: Float,
    soundFX: SoundFX,
    onAwayShot: () -> Unit
) {
    // 1. Goalkeeper Logic
    if (p.role == Role.GK) {
        if (ball.owner == p) {
            // GK clears or passes ball forward immediately
            val teammates = allPlayers.filter { it.isHome == p.isHome && it.role != Role.GK }
            val target = teammates.randomOrNull()
            val targetX = target?.x ?: 0.5f
            val targetY = target?.y ?: 0.5f
            val angle = atan2(targetY - p.y, targetX - p.x)
            ball.owner = null
            ball.stealCooldown = 0.28f
            ball.vx = cos(angle) * 0.68f
            ball.vy = sin(angle) * 0.68f
            p.facingAngle = angle
            soundFX.playKick()
            return
        }

        val targetY = ball.y.coerceIn(0.37f, 0.63f)
        val targetX = if (p.isHome) pitchLeft + 0.035f else pitchRight - 0.035f
        val dx = targetX - p.x
        val dy = targetY - p.y
        val dist = hypot(dx, dy)
        if (dist > 0.008f) {
            val gkSpeed = baseSpeed * 0.85f
            p.vx = (dx / dist) * gkSpeed
            p.vy = (dy / dist) * gkSpeed
            p.facingAngle = if (p.isHome) 0f else Math.PI.toFloat()
        } else {
            p.vx = 0f
            p.vy = 0f
        }
        return
    }

    // 2. If AI Outfield Player has the ball
    if (ball.owner == p) {
        if (!p.isHome) {
            // Away AI attacker moves toward Left Goal (Home Goal)
            val goalX = pitchLeft
            val goalY = 0.5f
            val distToGoal = hypot(goalX - p.x, goalY - p.y)

            // Shoot if within shooting range
            if (p.x < 0.34f || distToGoal < 0.28f) {
                val aimY = (0.5f + Random.nextFloat() * 0.16f - 0.08f).coerceIn(0.38f, 0.62f)
                val angle = atan2(aimY - p.y, goalX - p.x)
                val shotPower = 0.82f + (teamCfg.powerStat * 0.022f)
                ball.owner = null
                ball.stealCooldown = 0.28f
                ball.vx = cos(angle) * shotPower
                ball.vy = sin(angle) * shotPower
                p.facingAngle = angle
                onAwayShot()
                soundFX.playKick()
                return
            }

            // Check if closely pressed by Home defender -> Pass to open AI teammate
            val nearbyOpponent = allPlayers.any {
                it.isHome && hypot(it.x - p.x, it.y - p.y) < 0.075f
            }
            if (nearbyOpponent && Random.nextFloat() < 0.08f) {
                val mate = allPlayers.firstOrNull { !it.isHome && it.id != p.id && it.role != Role.GK }
                if (mate != null && mate.x < p.x + 0.1f) {
                    val angle = atan2(mate.y - p.y, mate.x - p.x)
                    ball.owner = null
                    ball.stealCooldown = 0.25f
                    ball.vx = cos(angle) * 0.60f
                    ball.vy = sin(angle) * 0.60f
                    p.facingAngle = angle
                    soundFX.playKick()
                    return
                }
            }

            // Dribble toward Home Goal
            val angle = atan2(goalY - p.y, goalX - p.x)
            p.facingAngle = angle
            p.vx = cos(angle) * (baseSpeed * 0.96f)
            p.vy = sin(angle) * (baseSpeed * 0.96f)
        }
        return
    }

    // 3. Outfield AI without the ball
    val sameTeamOutfield = allPlayers.filter { it.isHome == p.isHome && it.role != Role.GK }
    val closestToBall = sameTeamOutfield.minByOrNull { hypot(it.x - ball.x, it.y - ball.y) }

    val targetX: Float
    val targetY: Float

    if (!p.isHome && closestToBall?.id == p.id && ball.owner?.isHome != false) {
        // Primary AI presser chases the ball
        targetX = ball.x
        targetY = ball.y
    } else if (p.isHome && ball.owner == null && closestToBall?.id == p.id) {
        // Non-controlled Home AI helps pick up loose ball if closest
        targetX = ball.x
        targetY = ball.y
    } else {
        // Positional support movement relative to ball X
        val ballShiftX = (ball.x - 0.5f) * 0.35f
        targetX = (p.homeX + ballShiftX).coerceIn(0.15f, 0.85f)
        targetY = (p.homeY + (ball.y - 0.5f) * 0.25f).coerceIn(0.15f, 0.85f)
    }

    val dx = targetX - p.x
    val dy = targetY - p.y
    val dist = hypot(dx, dy)
    if (dist > 0.015f) {
        val angle = atan2(dy, dx)
        p.facingAngle = angle
        val moveSpeed = baseSpeed * (0.85f + teamCfg.defenseStat * 0.012f)
        p.vx = cos(angle) * moveSpeed
        p.vy = sin(angle) * moveSpeed
    } else {
        p.vx *= 0.6f
        p.vy *= 0.6f
    }
}

@Composable
fun VirtualJoystick(
    modifier: Modifier = Modifier,
    onJoystickMoved: (Float, Float) -> Unit
) {
    var knobOffset by remember { mutableStateOf(Offset.Zero) }

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            val maxRadius = size.width * 0.36f
            val center = Offset(size.width / 2f, size.height / 2f)

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                fun updateFromPosition(pos: Offset) {
                    val dx = pos.x - center.x
                    val dy = pos.y - center.y
                    val dist = sqrt(dx * dx + dy * dy)
                    if (dist <= maxRadius) {
                        knobOffset = Offset(dx, dy)
                    } else {
                        val ratio = maxRadius / dist
                        knobOffset = Offset(dx * ratio, dy * ratio)
                    }
                    onJoystickMoved(
                        (knobOffset.x / maxRadius).coerceIn(-1f, 1f),
                        (knobOffset.y / maxRadius).coerceIn(-1f, 1f)
                    )
                }

                updateFromPosition(down.position)

                do {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                    if (change != null && change.pressed) {
                        change.consume()
                        updateFromPosition(change.position)
                    }
                } while (event.changes.any { it.pressed })

                knobOffset = Offset.Zero
                onJoystickMoved(0f, 0f)
            }
        }
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val baseRadius = size.minDimension * 0.44f
        val knobRadius = size.minDimension * 0.20f

        drawCircle(
            color = Color.Black.copy(alpha = 0.38f),
            radius = baseRadius,
            center = center
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.45f),
            radius = baseRadius,
            center = center,
            style = Stroke(width = 3.dp.toPx())
        )
        drawCircle(
            color = Color(0xCC00E676),
            radius = knobRadius,
            center = center + knobOffset
        )
        drawCircle(
            color = Color.White,
            radius = knobRadius,
            center = center + knobOffset,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

@Composable
fun ArcadeActionButton(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onPressDown: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(if (isPressed) color.copy(alpha = 0.95f) else color.copy(alpha = 0.72f))
            .border(3.dp, Color.White.copy(alpha = 0.85f), CircleShape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    onPressDown()
                    do {
                        val event = awaitPointerEvent()
                    } while (event.changes.any { it.pressed })
                    isPressed = false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ArcadeHoldButton(
    label: String,
    isActive: Boolean,
    activeColor: Color,
    idleColor: Color,
    modifier: Modifier = Modifier,
    onHoldChanged: (Boolean) -> Unit
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(if (isActive) activeColor.copy(alpha = 0.92f) else idleColor.copy(alpha = 0.72f))
            .border(3.dp, Color.White.copy(alpha = 0.85f), CircleShape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onHoldChanged(true)
                    do {
                        val event = awaitPointerEvent()
                    } while (event.changes.any { it.pressed })
                    onHoldChanged(false)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isActive) Color.Black else Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
fun FullTimeScreen(
    homeTeam: TeamConfig,
    awayTeam: TeamConfig,
    homeScore: Int,
    awayScore: Int,
    homeShots: Int,
    awayShots: Int,
    onRematch: () -> Unit,
    onChangeTeams: () -> Unit
) {
    val resultHeadline = when {
        homeScore > awayScore -> "KEMENANGAN GEMILANG! (${homeTeam.name} MENANG)"
        homeScore < awayScore -> "PERTANDINGAN BERAKHIR (${awayTeam.name} MENANG)"
        else -> "HASIL IMBANG! (DRAW)"
    }

    val headlineColor = when {
        homeScore > awayScore -> Color(0xFF00E676)
        homeScore < awayScore -> Color(0xFFFF5252)
        else -> Color(0xFFFFD700)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0B132B), Color(0xFF1C2541), Color(0xFF0B132B))
                )
            )
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "FULL TIME",
            color = Color(0xFFFFD700),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = resultHeadline,
            color = headlineColor,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF16213E))
                .border(2.dp, Color(0xFF3A506B), RoundedCornerShape(16.dp))
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(homeTeam.primaryColor)
                        .border(3.dp, homeTeam.secondaryColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = homeTeam.code,
                        color = if (homeTeam.primaryColor == Color.White || homeTeam.primaryColor == Color(0xFFEEEEEE) || homeTeam.primaryColor == Color(0xFFFFD600)) Color.Black else Color.White,
                        fontWeight = FontWeight.Black
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(homeTeam.name, color = Color.White, fontWeight = FontWeight.Bold)
                Text("Shots: $homeShots", color = Color.LightGray, fontSize = 12.sp)
            }

            Text(
                text = "$homeScore  -  $awayScore",
                color = Color(0xFFFFD700),
                fontSize = 36.sp,
                fontWeight = FontWeight.Black
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(awayTeam.primaryColor)
                        .border(3.dp, awayTeam.secondaryColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = awayTeam.code,
                        color = if (awayTeam.primaryColor == Color.White || awayTeam.primaryColor == Color(0xFFEEEEEE) || awayTeam.primaryColor == Color(0xFFFFD600)) Color.Black else Color.White,
                        fontWeight = FontWeight.Black
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(awayTeam.name, color = Color.White, fontWeight = FontWeight.Bold)
                Text("Shots: $awayShots", color = Color.LightGray, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = onRematch,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .width(180.dp)
                    .height(46.dp)
            ) {
                Text(
                    text = "REMATCH",
                    color = Color.Black,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Button(
                onClick = onChangeTeams,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2979FF)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .width(180.dp)
                    .height(46.dp)
            ) {
                Text(
                    text = "PILIH TIM BARU",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}
