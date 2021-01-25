package com.serwylo.beatgame.entities

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.g2d.Animation
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.serwylo.beatgame.Globals
import kotlin.math.abs

class Player(
        val velocity: Vector2 = Vector2(),
        atlas: TextureAtlas
) : Entity {

    val position = Vector2()

    /**
     * For animation purposes, record the last time we hit an obstacle. Use this to show some
     * visual feedback as to how frequently we get hit.
     */
    var hitAnimation = -1f

    /**
     * For one frame, we will record a hit has been performed. This allows the main game to interrotage
     * and respond (e.g. by starting particle effects, shaking camera, vibrating, etc.
     */
    var justHitDamage = 0

    private var jumpCount = 0

    private var walkAnimation: Animation<TextureAtlas.AtlasRegion>
    private var deathAnimation: Animation<TextureRegion>
    private var textureJump: TextureRegion
    private var textureHit: TextureRegion

    private var health = 1000

    private var score: Float = 0f

    var scoreMultiplier = 1f

    fun getHealth(): Int { return health }

    enum class State {
        RUNNING,
        JUMPING,
        DEAD
    }

    private var state: State = State.RUNNING

    private val hitObstacles = mutableSetOf<Obstacle>()

    private var deathTime = 0f

    init {
        textureJump = atlas.findRegion("character_a_jump")
        textureHit = atlas.findRegion("character_a_hit")

        val texturesWalk = atlas.findRegions("character_a_walk")
        walkAnimation = Animation(0.2f, texturesWalk)

        deathAnimation = Animation(
                0.5f,
                textureHit,
                atlas.findRegion("character_a_duck"),
                atlas.findRegion("ghost"),
                atlas.findRegion("ghost_x")
        )
    }

    fun performJump() {

        if (jumpCount < 2 && abs(velocity.y) <= DOUBLE_JUMP_THRESHOLD) {

            velocity.y = JUMP_VELOCITY
            state = State.JUMPING
            jumpCount ++

        }

    }

    private fun sprite(isPaused: Boolean): TextureRegion {

        if (state == State.DEAD) {
            return deathAnimation.getKeyFrame(Globals.animationTimer - deathTime, false)
        }

        if (hitAnimation > 0) {
            return textureHit
        }

        if (state == State.RUNNING) {
            if (isPaused) {
                return walkAnimation.getKeyFrame(0f)
            } else {
                return walkAnimation.getKeyFrame(Globals.animationTimer, true)
            }
        }

        return textureJump

    }

    override fun render(camera: Camera, isPaused: Boolean) {

        val batch = Globals.spriteBatch
        batch.projectionMatrix = camera.combined
        batch.begin()
        batch.draw(sprite(isPaused), position.x, position.y, WIDTH, HEIGHT)
        batch.end()

    }

    override fun update(delta: Float) {
        if (state == State.DEAD) {
            return
        }

        hitAnimation -= delta

        velocity.y += GRAVITY_CONSTANT * delta

        position.x += velocity.x * delta
        position.y += velocity.y * delta

        if (position.y < 0) {
            landOnSurface(0f)
        } else if (state == State.JUMPING){
            score += SCORE_PER_SECOND * delta * scoreMultiplier
        }
    }

    fun isColliding(rect: Rectangle): Boolean {
        if (rect.x + rect.width < position.x || rect.x > position.x + WIDTH || rect.y + rect.height < position.y ) {
            return false
        }

        // Are we falling (good, we landed on an object), or moving forward into an object?
        if (position.y > rect.y + rect.height - CLIMB_THRESHOLD) {

            if (velocity.y <= 0) {
                landOnSurface(rect.y + rect.height)
            }

            return false
        }

        return true
    }

    private fun landOnSurface(height: Float) {
        if (height <= 0f) {
            scoreMultiplier = 1f
        } else if (state == State.JUMPING) {
            scoreMultiplier += 0.5f
        }

        state = State.RUNNING
        velocity.y = 0f
        position.y = height
        jumpCount = 0
    }

    fun hit(obstacle: Obstacle) {

        hitAnimation = HIT_ANIMATION_DURATION

        if (!hitObstacles.contains(obstacle)) {

            hitObstacles.add(obstacle)

            scoreMultiplier = 1f

            // Bigger obstacles cause more damage.
            val damage = (obstacle.rect.area() * AREA_TO_DAMAGE).toInt().coerceAtLeast(MIN_DAMAGE)

            // If we are jumping upward and hit the obstacle above half way then we can visually
            // it doesn't look like such a big deal when you hit it, so reduce the damage accordingly.
            val scaledDamage = if (velocity.y > 0) {
                val scale = 1 -  (position.y - obstacle.rect.y) / obstacle.rect.height
                (damage * scale).toInt().coerceAtLeast(MIN_DAMAGE)
            } else {
                damage
            }

            health -= scaledDamage
            justHitDamage = scaledDamage

            if (health <= 0) {
                health = 0
                deathTime = Globals.animationTimer
                velocity.set(0f, 0f)
                state = State.DEAD
            }

        }

    }

    fun getScore(): Int {
        return score.toInt()
    }

    fun clearHit() {
        justHitDamage = 0
    }

    companion object {

        const val WIDTH = 0.8f
        const val HEIGHT = 0.8f

        const val HIT_ANIMATION_DURATION = 0.1f

        /**
         * Be a little bit generous. If there are many obstacles that are almost the same height,
         * just let the player climb onto the next one if it is more or less a similar height.
         */
        const val CLIMB_THRESHOLD = 0.4f

        /**
         * Only allow double jumps when you are close to the top of your first jump. Seems to
         * offer a nice experience.
         */
        const val DOUBLE_JUMP_THRESHOLD = 6f

        const val GRAVITY_CONSTANT = -9.8f * 4f

        const val JUMP_VELOCITY = 10f

        /**
         * When hitting an obstacle, multiply the area by this in order to figure out how much damage to do.
         */
        const val AREA_TO_DAMAGE = 15f

        const val MIN_DAMAGE = 1

        const val SCORE_PER_SECOND = 100

    }

}
