package hello

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.body
import org.springframework.web.reactive.function.server.router
import reactor.core.publisher.Mono
import kotlin.math.abs

@SpringBootApplication
class KotlinApplication {

    @Bean
    fun routes() = router {
        GET {
            ServerResponse.ok().body(Mono.just("Let the battle begin!"))
        }

        POST("/**", accept(APPLICATION_JSON)) { request ->
            request.bodyToMono(ArenaUpdate::class.java).flatMap { arenaUpdate ->
                val self = arenaUpdate.arena.state[arenaUpdate._links.self.href] ?: return@flatMap randomAction

                val (w, h) = arenaUpdate.arena.dims
                val map = MutableList(h) { MutableList<PlayerState?>(w) { null } }
                arenaUpdate.arena.state.values.forEach { player ->
                    map[player.y][player.x] = player
                }

                if (self.wasHit) {
                    return@flatMap ServerResponse.ok().body(
                        Mono.just(
                            self.dodge(map, w, h).name
                        )
                    )
                }

                val others = arenaUpdate.arena.state - arenaUpdate._links.self.href
                val target = others.values.maxByOrNull(PlayerState::score) ?: return@flatMap randomAction
                ServerResponse.ok().body(
                    Mono.just(
                        self.getAction(target, map).name
                    )
                )
            }
        }
    }
}

fun PlayerState.dodge(map: List<List<PlayerState?>>, w: Int, h: Int): Action {
    for (player in map[y]) {
        if (player == null) continue
        if (
            player.y > y && player.direction == Direction.N ||
            player.y < y && player.direction == Direction.S
        ) return turnToOrElse(if (x < w / 2) Direction.E else Direction.W, forward(map))
        if (
            player.x > x && player.direction == Direction.W ||
            player.x < x && player.direction == Direction.E
        ) return turnToOrElse(if (y < h / 2) Direction.S else Direction.W, forward(map))
    }
    return Action.T
}

fun PlayerState.forward(map: List<List<PlayerState?>>): Action {
    val targetGrid = when (direction) {
        Direction.N -> map[y - 1][x]
        Direction.W -> map[y][x - 1]
        Direction.S -> map[y + 1][x]
        Direction.E -> map[y][x + 1]
    }
    return if (targetGrid != null) Action.T else Action.F
}

val randomAction get() = ServerResponse.ok().body(Mono.just(listOf("F", "R", "L", "T").random()))

fun PlayerState.absDelta(target: PlayerState) = abs(dx(target)) + abs(dy(target))
fun PlayerState.dx(target: PlayerState) = target.x - x
fun PlayerState.dy(target: PlayerState) = target.y - y

fun PlayerState.getAction(target: PlayerState, map: MutableList<MutableList<PlayerState?>>): Action {
    val dx = dx(target)
    val dy = dy(target)
    val absDelta = absDelta(target)
    val dir = when {
        dx > 0 -> Direction.E
        dx < 0 -> Direction.W
        dy > 0 -> Direction.S
        dy < 0 -> Direction.N
        else -> Direction.E // never
    }
    if (absDelta == 1) Action.T else forward(map)
    return turnToOrElse(
        dir,
        if (absDelta == 1) Action.T else forward(map)
    ).also {
        println(
            """
            self = $x, $y
            target = ${target.x}, ${target.y}
            dx = $dx
            dy = $dy
            absDelta = $absDelta
            dir = $dir
            action = $it
        """.trimIndent()
        )
    }
}

fun PlayerState.turnToOrElse(dir: Direction, action: Action) = when (direction) {
    Direction.N -> when (dir) {
        Direction.S -> Action.R
        Direction.W -> Action.L
        Direction.E -> Action.R
        else -> action
    }
    Direction.W -> when (dir) {
        Direction.E -> Action.R
        Direction.N -> Action.R
        Direction.S -> Action.L
        else -> action
    }
    Direction.S -> when (dir) {
        Direction.N -> Action.R
        Direction.W -> Action.R
        Direction.E -> Action.L
        else -> action
    }
    Direction.E -> when (dir) {
        Direction.W -> Action.R
        Direction.N -> Action.L
        Direction.S -> Action.R
        else -> action
    }
}

fun main(args: Array<String>) {
    runApplication<KotlinApplication>(*args)
}

data class ArenaUpdate(val _links: Links, val arena: Arena)
data class PlayerState(val x: Int, val y: Int, val direction: Direction, val score: Int, val wasHit: Boolean)
data class Links(val self: Self)
data class Self(val href: String)
data class Arena(val dims: List<Int>, val state: Map<String, PlayerState>)

enum class Direction {
    N, W, S, E
}

enum class Action {
    F, R, L, T
}