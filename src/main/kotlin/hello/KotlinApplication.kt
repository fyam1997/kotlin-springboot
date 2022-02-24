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
                val others = arenaUpdate.arena.state - arenaUpdate._links.self.href
                val target = others.values.sortedBy { player ->
                    self.absDelta(player)
                }.firstOrNull() ?: return@flatMap randomAction

                ServerResponse.ok().body(Mono.just(self.getAction(target).name))
            }
        }
    }
}

val randomAction get() = ServerResponse.ok().body(Mono.just(listOf("F", "R", "L", "T").random()))

fun PlayerState.absDelta(target: PlayerState) = abs(dx(target)) + abs(dy(target))
fun PlayerState.dx(target: PlayerState) = target.x - x
fun PlayerState.dy(target: PlayerState) = target.y - y

fun PlayerState.getAction(target: PlayerState): Action {
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
    if (absDelta == 1) Action.T else Action.F
    return turnToOrElse(
        dir,
        if (absDelta == 1) Action.T else Action.F
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
        Direction.W -> Action.R
        Direction.S -> Action.R
        Direction.E -> Action.L
        else -> action
    }
    Direction.W -> when (dir) {
        Direction.N -> Action.L
        Direction.S -> Action.R
        Direction.E -> Action.R
        else -> action
    }
    Direction.S -> when (dir) {
        Direction.N -> Action.R
        Direction.W -> Action.L
        Direction.E -> Action.R
        else -> action
    }
    Direction.E -> when (dir) {
        Direction.N -> Action.R
        Direction.W -> Action.R
        Direction.S -> Action.L
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