package neo.ui

import neo.Renderer.Material
import neo.Renderer.Material.idMaterial
import neo.framework.CVarSystem
import neo.framework.CVarSystem.idCVar
import neo.framework.DeclManager
import neo.framework.File_h.idFile
import neo.framework.KeyInput
import neo.framework.Session
import neo.idlib.Lib
import neo.idlib.Text.Parser.idParser
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.List.idList
import neo.idlib.math.Math_h
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Random.idRandom
import neo.idlib.math.Vector.idVec2
import neo.idlib.math.Vector.idVec4
import neo.sys.sys_public.sysEventType_t
import neo.sys.sys_public.sysEvent_s
import neo.ui.DeviceContext.idDeviceContext
import neo.ui.SimpleWindow.drawWin_t
import neo.ui.UserInterfaceLocal.idUserInterfaceLocal
import neo.ui.Window.idWindow
import neo.ui.Winvar.idWinBool
import neo.ui.Winvar.idWinVar

/**
 *
 */
object GameBearShootWindow {
    const val BEAR_GRAVITY = 240
    const val BEAR_SHRINK_TIME = 2000f
    const val BEAR_SIZE = 24f

    //
    const val MAX_WINDFORCE = 100f

    //
    val bearTurretAngle: idCVar? = idCVar("bearTurretAngle", "0", CVarSystem.CVAR_FLOAT, "")
    val bearTurretForce: idCVar? = idCVar("bearTurretForce", "200", CVarSystem.CVAR_FLOAT, "")

    //
    /*
     *****************************************************************************
     * BSEntity
     ****************************************************************************
     */
    class BSEntity(  //
        var game: idGameBearShootWindow?
    ) {
        //
        var entColor: idVec4?

        //
        var fadeIn: Boolean
        var fadeOut: Boolean
        var material: idMaterial?
        var materialName: idStr?
        var position: idVec2? = null
        var rotation: Float
        var rotationSpeed: Float
        var velocity: idVec2? = null
        var visible = true
        var width: Float
        var height: Float

        //	// virtual				~BSEntity();
        //
        fun WriteToSaveGame(savefile: idFile?) {
            game.WriteSaveGameString(materialName.toString(), savefile)
            savefile.WriteFloat(width)
            savefile.WriteFloat(height)
            savefile.WriteBool(visible)
            savefile.Write(entColor)
            savefile.Write(position)
            savefile.WriteFloat(rotation)
            savefile.WriteFloat(rotationSpeed)
            savefile.Write(velocity)
            savefile.WriteBool(fadeIn)
            savefile.WriteBool(fadeOut)
        }

        fun ReadFromSaveGame(savefile: idFile?, _game: idGameBearShootWindow?) {
            game = _game
            game.ReadSaveGameString(materialName, savefile)
            SetMaterial(materialName.toString())
            width = savefile.ReadFloat()
            height = savefile.ReadFloat()
            visible = savefile.ReadBool()
            savefile.Read(entColor)
            savefile.Read(position)
            rotation = savefile.ReadFloat()
            rotationSpeed = savefile.ReadFloat()
            savefile.Read(velocity)
            fadeIn = savefile.ReadBool()
            fadeOut = savefile.ReadBool()
        }

        fun SetMaterial(name: String?) {
            materialName.oSet(name)
            material = DeclManager.declManager.FindMaterial(name)
            material.SetSort(Material.SS_GUI.toFloat())
        }

        fun SetSize(_width: Float, _height: Float) {
            width = _width
            height = _height
        }

        fun SetVisible(isVisible: Boolean) {
            visible = isVisible
        }

        fun Update(timeslice: Float) {
            if (!visible) {
                return
            }

            // Fades
            if (fadeIn && entColor.w < 1f) {
                entColor.w += 1 * timeslice
                if (entColor.w >= 1f) {
                    entColor.w = 1f
                    fadeIn = false
                }
            }
            if (fadeOut && entColor.w > 0f) {
                entColor.w -= 1 * timeslice
                if (entColor.w <= 0f) {
                    entColor.w = 0f
                    fadeOut = false
                }
            }

            // Move the entity
            position.plusAssign(velocity.oMultiply(timeslice))

            // Rotate Entity
            rotation += rotationSpeed * timeslice
        }

        fun Draw(dc: idDeviceContext?) {
            if (visible) {
                dc.DrawMaterialRotated(
                    position.x,
                    position.y,
                    width,
                    height,
                    material,
                    entColor,
                    1.0f,
                    1.0f,
                    Math_h.DEG2RAD(rotation)
                )
            }
        }

        //
        init {
            entColor = Lib.Companion.colorWhite
            materialName = idStr("")
            material = null
            height = 8f
            width = height
            rotation = 0f
            rotationSpeed = 0f
            fadeIn = false
            fadeOut = false
            position.Zero()
            velocity.Zero()
        }
    }

    /*
     *****************************************************************************
     * idGameBearShootWindow
     ****************************************************************************
     */
    class idGameBearShootWindow : idWindow {
        private var bear: BSEntity? = null
        private var bearHitTarget = false

        // ~idGameBearShootWindow();
        private var bearIsShrinking = false

        //
        private var bearScale = 0f
        private var bearShrinkStartTime = 0

        //
        private var currentLevel = 0

        //
        private val entities: idList<BSEntity?>? = idList()
        private var gameOver = false

        //
        //
        private val gamerunning: idWinBool? = null
        private var goal: BSEntity? = null
        private var goalsHit = 0
        private var gunblast: BSEntity? = null
        private var helicopter: BSEntity? = null
        private val onContinue: idWinBool? = null
        private val onFire: idWinBool? = null
        private val onNewGame: idWinBool? = null
        private var timeRemaining = 0f

        //
        private var timeSlice = 0f

        //
        private var turret: BSEntity? = null

        //
        private var turretAngle = 0f
        private var turretForce = 0f
        private var updateScore = false
        private var wind: BSEntity? = null

        //
        private var windForce = 0f
        private var windUpdateTime = 0

        constructor(gui: idUserInterfaceLocal?) : super(gui) {
            this.gui = gui
            CommonInit()
        }

        constructor(dc: idDeviceContext?, gui: idUserInterfaceLocal?) : super(dc, gui) {
            this.dc = dc
            this.gui = gui
            CommonInit()
        }

        override fun WriteToSaveGame(savefile: idFile?) {
            super.WriteToSaveGame(savefile)
            gamerunning.WriteToSaveGame(savefile)
            onFire.WriteToSaveGame(savefile)
            onContinue.WriteToSaveGame(savefile)
            onNewGame.WriteToSaveGame(savefile)
            savefile.WriteFloat(timeSlice)
            savefile.WriteFloat(timeRemaining)
            savefile.WriteBool(gameOver)
            savefile.WriteInt(currentLevel)
            savefile.WriteInt(goalsHit)
            savefile.WriteBool(updateScore)
            savefile.WriteBool(bearHitTarget)
            savefile.WriteFloat(bearScale)
            savefile.WriteBool(bearIsShrinking)
            savefile.WriteInt(bearShrinkStartTime)
            savefile.WriteFloat(turretAngle)
            savefile.WriteFloat(turretForce)
            savefile.WriteFloat(windForce)
            savefile.WriteInt(windUpdateTime)
            val numberOfEnts = entities.Num()
            savefile.WriteInt(numberOfEnts)
            for (i in 0 until numberOfEnts) {
                entities.get(i).WriteToSaveGame(savefile)
            }
            var index: Int
            index = entities.FindIndex(turret)
            savefile.WriteInt(index)
            index = entities.FindIndex(bear)
            savefile.WriteInt(index)
            index = entities.FindIndex(helicopter)
            savefile.WriteInt(index)
            index = entities.FindIndex(goal)
            savefile.WriteInt(index)
            index = entities.FindIndex(wind)
            savefile.WriteInt(index)
            index = entities.FindIndex(gunblast)
            savefile.WriteInt(index)
        }

        override fun ReadFromSaveGame(savefile: idFile?) {
            super.ReadFromSaveGame(savefile)

            // Remove all existing entities
            entities.DeleteContents(true)
            gamerunning.ReadFromSaveGame(savefile)
            onFire.ReadFromSaveGame(savefile)
            onContinue.ReadFromSaveGame(savefile)
            onNewGame.ReadFromSaveGame(savefile)
            timeSlice = savefile.ReadFloat()
            timeRemaining = savefile.ReadInt().toFloat()
            gameOver = savefile.ReadBool()
            currentLevel = savefile.ReadInt()
            goalsHit = savefile.ReadInt()
            updateScore = savefile.ReadBool()
            bearHitTarget = savefile.ReadBool()
            bearScale = savefile.ReadFloat()
            bearIsShrinking = savefile.ReadBool()
            bearShrinkStartTime = savefile.ReadInt()
            turretAngle = savefile.ReadFloat()
            turretForce = savefile.ReadFloat()
            windForce = savefile.ReadFloat()
            windUpdateTime = savefile.ReadInt()
            val numberOfEnts: Int
            numberOfEnts = savefile.ReadInt()
            for (i in 0 until numberOfEnts) {
                var ent: BSEntity
                ent = BSEntity(this)
                ent.ReadFromSaveGame(savefile, this)
                entities.Append(ent)
            }
            var index: Int
            index = savefile.ReadInt()
            turret = entities.get(index)
            index = savefile.ReadInt()
            bear = entities.get(index)
            index = savefile.ReadInt()
            helicopter = entities.get(index)
            index = savefile.ReadInt()
            goal = entities.get(index)
            index = savefile.ReadInt()
            wind = entities.get(index)
            index = savefile.ReadInt()
            gunblast = entities.get(index)
        }

        override fun HandleEvent(event: sysEvent_s?, updateVisuals: BooleanArray?): String? {
            val key = event.evValue

            // need to call this to allow proper focus and capturing on embedded children
            val ret = super.HandleEvent(event, updateVisuals)
            if (event.evType == sysEventType_t.SE_KEY) {
                if (0 == event.evValue2) {
                    return ret
                }
                if (key == KeyInput.K_MOUSE1) {
                    // Mouse was clicked
                } else {
                    return ret
                }
            }
            return ret
        }

        override fun Draw(time: Int, x: Float, y: Float) {
            var i: Int

            //Update the game every frame before drawing
            UpdateGame()
            i = entities.Num() - 1
            while (i >= 0) {
                entities.get(i).Draw(dc)
                i--
            }
        }

        fun Activate(activate: Boolean): String? {
            return ""
        }

        override fun GetWinVarByName(
            _name: String?,
            winLookup: Boolean /*= false*/,
            owner: Array<drawWin_t?>? /*= NULL*/
        ): idWinVar? {
            var retVar: idWinVar? = null
            if (idStr.Companion.Icmp(_name, "gamerunning") == 0) {
                retVar = gamerunning
            } else if (idStr.Companion.Icmp(_name, "onFire") == 0) {
                retVar = onFire
            } else if (idStr.Companion.Icmp(_name, "onContinue") == 0) {
                retVar = onContinue
            } else if (idStr.Companion.Icmp(_name, "onNewGame") == 0) {
                retVar = onNewGame
            }
            return retVar ?: super.GetWinVarByName(_name, winLookup, owner)
        }

        private fun CommonInit() {
            var ent: BSEntity

            // Precache sounds
            DeclManager.declManager.FindSound("arcade_beargroan")
            DeclManager.declManager.FindSound("arcade_sargeshoot")
            DeclManager.declManager.FindSound("arcade_balloonpop")
            DeclManager.declManager.FindSound("arcade_levelcomplete1")

            // Precache dynamically used materials
            DeclManager.declManager.FindMaterial("game/bearshoot/helicopter_broken")
            DeclManager.declManager.FindMaterial("game/bearshoot/goal_dead")
            DeclManager.declManager.FindMaterial("game/bearshoot/gun_blast")
            ResetGameState()
            ent = BSEntity(this)
            turret = ent
            ent.SetMaterial("game/bearshoot/turret")
            ent.SetSize(272f, 144f)
            ent.position.x = -44f
            ent.position.y = 260f
            entities.Append(ent)
            ent = BSEntity(this)
            ent.SetMaterial("game/bearshoot/turret_base")
            ent.SetSize(144f, 160f)
            ent.position.x = 16f
            ent.position.y = 280f
            entities.Append(ent)
            ent = BSEntity(this)
            bear = ent
            ent.SetMaterial("game/bearshoot/bear")
            ent.SetSize(BEAR_SIZE, BEAR_SIZE)
            ent.SetVisible(false)
            ent.position.x = 0f
            ent.position.y = 0f
            entities.Append(ent)
            ent = BSEntity(this)
            helicopter = ent
            ent.SetMaterial("game/bearshoot/helicopter")
            ent.SetSize(64f, 64f)
            ent.position.x = 550f
            ent.position.y = 100f
            entities.Append(ent)
            ent = BSEntity(this)
            goal = ent
            ent.SetMaterial("game/bearshoot/goal")
            ent.SetSize(64f, 64f)
            ent.position.x = 550f
            ent.position.y = 164f
            entities.Append(ent)
            ent = BSEntity(this)
            wind = ent
            ent.SetMaterial("game/bearshoot/wind")
            ent.SetSize(100f, 40f)
            ent.position.x = 500f
            ent.position.y = 430f
            entities.Append(ent)
            ent = BSEntity(this)
            gunblast = ent
            ent.SetMaterial("game/bearshoot/gun_blast")
            ent.SetSize(64f, 64f)
            ent.SetVisible(false)
            entities.Append(ent)
        }

        private fun ResetGameState() {
            gamerunning.data = false
            gameOver = false
            onFire.data = false
            onContinue.data = false
            onNewGame.data = false

            // Game moves forward 16 milliseconds every frame
            timeSlice = 0.016f
            timeRemaining = 60f
            goalsHit = 0
            updateScore = false
            bearHitTarget = false
            currentLevel = 1
            turretAngle = 0f
            turretForce = 200f
            windForce = 0f
            windUpdateTime = 0
            bearIsShrinking = false
            bearShrinkStartTime = 0
            bearScale = 1f
        }

        private fun UpdateBear() {
            val time = gui.GetTime()
            var startShrink = false

            // Apply gravity
            bear.velocity.y += BEAR_GRAVITY * timeSlice

            // Apply wind
            bear.velocity.x += windForce * timeSlice

            // Check for collisions
            if (!bearHitTarget && !gameOver) {
                val bearCenter = idVec2()
                var collision = false
                bearCenter.x = bear.position.x + bear.width / 2
                bearCenter.y = bear.position.y + bear.height / 2
                if (bearCenter.x > helicopter.position.x + 16 && bearCenter.x < helicopter.position.x + helicopter.width - 29) {
                    if (bearCenter.y > helicopter.position.y + 12 && bearCenter.y < helicopter.position.y + helicopter.height - 7) {
                        collision = true
                    }
                }
                if (collision) {
                    // balloons pop and bear tumbles to ground
                    helicopter.SetMaterial("game/bearshoot/helicopter_broken")
                    helicopter.velocity.y = 230f
                    goal.velocity.y = 230f
                    Session.Companion.session.sw.PlayShaderDirectly("arcade_balloonpop")
                    bear.SetVisible(false)
                    if (bear.velocity.x > 0) {
                        bear.velocity.x *= -1f
                    }
                    bear.velocity.oMulSet(0.666f)
                    bearHitTarget = true
                    updateScore = true
                    startShrink = true
                }
            }

            // Check for ground collision
            if (bear.position.y > 380) {
                bear.position.y = 380f
                if (bear.velocity.Length() < 25) {
                    bear.velocity.Zero()
                } else {
                    startShrink = true
                    bear.velocity.y *= -1f
                    bear.velocity.oMulSet(0.5f)
                    if (bearScale != 0f) {
                        Session.Companion.session.sw.PlayShaderDirectly("arcade_balloonpop")
                    }
                }
            }

            // Bear rotation is based on velocity
            val angle: Float
            val dir: idVec2?
            dir = bear.velocity
            dir.NormalizeFast()
            angle = Math_h.RAD2DEG(Math.atan2(dir.x.toDouble(), dir.y.toDouble()).toFloat())
            bear.rotation = angle - 90

            // Update Bear scale
            if (bear.position.x > 650) {
                startShrink = true
            }
            if (!bearIsShrinking && bearScale != 0f && startShrink) {
                bearShrinkStartTime = time
                bearIsShrinking = true
            }
            if (bearIsShrinking) {
                bearScale = if (bearHitTarget) {
                    1 - (time - bearShrinkStartTime).toFloat() / BEAR_SHRINK_TIME
                } else {
                    1 - (time - bearShrinkStartTime).toFloat() / 750
                }
                bearScale *= BEAR_SIZE
                bear.SetSize(bearScale, bearScale)
                if (bearScale < 0) {
                    gui.HandleNamedEvent("EnableFireButton")
                    bearIsShrinking = false
                    bearScale = 0f
                    if (bearHitTarget) {
                        goal.SetMaterial("game/bearshoot/goal")
                        goal.position.x = 550f
                        goal.position.y = 164f
                        goal.velocity.Zero()
                        goal.velocity.y = ((currentLevel - 1) * 30).toFloat()
                        goal.entColor.w = 0f
                        goal.fadeIn = true
                        goal.fadeOut = false
                        helicopter.SetVisible(true)
                        helicopter.SetMaterial("game/bearshoot/helicopter")
                        helicopter.position.x = 550f
                        helicopter.position.y = 100f
                        helicopter.velocity.Zero()
                        helicopter.velocity.y = goal.velocity.y
                        helicopter.entColor.w = 0f
                        helicopter.fadeIn = true
                        helicopter.fadeOut = false
                    }
                }
            }
        }

        private fun UpdateHelicopter() {
            if (bearHitTarget && bearIsShrinking) {
                if (helicopter.velocity.y != 0f && helicopter.position.y > 264) {
                    helicopter.velocity.y = 0f
                    goal.velocity.y = 0f
                    helicopter.SetVisible(false)
                    goal.SetMaterial("game/bearshoot/goal_dead")
                    Session.Companion.session.sw.PlayShaderDirectly("arcade_beargroan", 1)
                    helicopter.fadeOut = true
                    goal.fadeOut = true
                }
            } else if (currentLevel > 1) {
                val height = helicopter.position.y.toInt()
                val speed = ((currentLevel - 1) * 30).toFloat()
                if (height > 240) {
                    helicopter.velocity.y = -speed
                    goal.velocity.y = -speed
                } else if (height < 30) {
                    helicopter.velocity.y = speed
                    goal.velocity.y = speed
                }
            }
        }

        private fun UpdateTurret() {
            var pt: idVec2? = idVec2()
            val turretOrig = idVec2()
            val right = idVec2()
            val dot: Float
            val angle: Float
            pt.x = gui.CursorX()
            pt.y = gui.CursorY()
            turretOrig.set(80f, 348f)
            pt = pt.oMinus(turretOrig)
            pt.NormalizeFast()
            right.x = 1f
            right.y = 0f
            dot = pt.oMultiply(right)
            angle = Math_h.RAD2DEG(Math.acos(dot.toDouble()).toFloat())
            turretAngle = idMath.ClampFloat(0f, 90f, angle)
        }

        private fun UpdateButtons() {
            if (onFire.oCastBoolean()) {
                val vec = idVec2()
                gui.HandleNamedEvent("DisableFireButton")
                Session.Companion.session.sw.PlayShaderDirectly("arcade_sargeshoot")
                bear.SetVisible(true)
                bearScale = 1f
                bear.SetSize(BEAR_SIZE, BEAR_SIZE)
                vec.x = idMath.Cos(Math_h.DEG2RAD(turretAngle))
                vec.x += (1 - vec.x) * 0.18f
                vec.y = -idMath.Sin(Math_h.DEG2RAD(turretAngle))
                turretForce = bearTurretForce.GetFloat()
                bear.position.x = 80 + 96 * vec.x
                bear.position.y = 334 + 96 * vec.y
                bear.velocity.x = vec.x * turretForce
                bear.velocity.y = vec.y * turretForce
                gunblast.position.x = 55 + 96 * vec.x
                gunblast.position.y = 310 + 100 * vec.y
                gunblast.SetVisible(true)
                gunblast.entColor.w = 1f
                gunblast.rotation = turretAngle
                gunblast.fadeOut = true
                bearHitTarget = false
                onFire.data = false
            }
        }

        private fun UpdateGame() {
            var i: Int
            if (onNewGame.oCastBoolean()) {
                ResetGameState()
                goal.position.x = 550f
                goal.position.y = 164f
                goal.velocity.Zero()
                helicopter.position.x = 550f
                helicopter.position.y = 100f
                helicopter.velocity.Zero()
                bear.SetVisible(false)
                bearTurretAngle.SetFloat(0f)
                bearTurretForce.SetFloat(200f)
                gamerunning.data = true
            }
            if (onContinue.oCastBoolean()) {
                gameOver = false
                timeRemaining = 60f
                onContinue.data = false
            }
            if (gamerunning.oCastBoolean() == true) {
                val current_time = gui.GetTime()
                val rnd = idRandom(current_time)

                // Check for button presses
                UpdateButtons()
                if (bear != null) {
                    UpdateBear()
                }
                if (helicopter != null && goal != null) {
                    UpdateHelicopter()
                }

                // Update Wind
                if (windUpdateTime < current_time) {
                    val scale: Float
                    val width: Int
                    windForce = rnd.CRandomFloat() * (MAX_WINDFORCE * 0.75f)
                    if (windForce > 0) {
                        windForce += MAX_WINDFORCE * 0.25f
                        wind.rotation = 0f
                    } else {
                        windForce -= MAX_WINDFORCE * 0.25f
                        wind.rotation = 180f
                    }
                    scale = 1f - (MAX_WINDFORCE - Math.abs(windForce)) / MAX_WINDFORCE
                    width = (100 * scale).toInt()
                    if (windForce < 0) {
                        wind.position.x = (500 - width + 1).toFloat()
                    } else {
                        wind.position.x = 500f
                    }
                    wind.SetSize(width.toFloat(), 40f)
                    windUpdateTime = current_time + 7000 + rnd.RandomInt(5000.0)
                }

                // Update turret rotation angle
                if (turret != null) {
                    turretAngle = bearTurretAngle.GetFloat()
                    turret.rotation = turretAngle
                }
                i = 0
                while (i < entities.Num()) {
                    entities.get(i).Update(timeSlice)
                    i++
                }

                // Update countdown timer
                timeRemaining -= timeSlice
                timeRemaining = idMath.ClampFloat(0f, 99999f, timeRemaining)
                gui.SetStateString("time_remaining", Str.va("%2.1f", timeRemaining))
                if (timeRemaining <= 0f && !gameOver) {
                    gameOver = true
                    updateScore = true
                }
                if (updateScore) {
                    UpdateScore()
                    updateScore = false
                }
            }
        }

        private fun UpdateScore() {
            if (gameOver) {
                gui.HandleNamedEvent("GameOver")
                return
            }
            goalsHit++
            gui.SetStateString("player_score", Str.va("%d", goalsHit))

            // Check for level progression
            if (0 == goalsHit % 5) {
                currentLevel++
                gui.SetStateString("current_level", Str.va("%d", currentLevel))
                Session.Companion.session.sw.PlayShaderDirectly("arcade_levelcomplete1", 3)
                timeRemaining += 30f
            }
        }

        override fun ParseInternalVar(_name: String?, src: idParser?): Boolean {
            if (idStr.Companion.Icmp(_name, "gamerunning") == 0) {
                gamerunning.oSet(src.ParseBool())
                return true
            }
            if (idStr.Companion.Icmp(_name, "onFire") == 0) {
                onFire.oSet(src.ParseBool())
                return true
            }
            if (idStr.Companion.Icmp(_name, "onContinue") == 0) {
                onContinue.oSet(src.ParseBool())
                return true
            }
            if (idStr.Companion.Icmp(_name, "onNewGame") == 0) {
                onNewGame.oSet(src.ParseBool())
                return true
            }
            return super.ParseInternalVar(_name, src)
        }
    }
}