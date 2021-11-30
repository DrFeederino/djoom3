package neo.ui

import neo.Renderer.Image_files
import neo.Renderer.Material
import neo.Renderer.Material.idMaterial
import neo.framework.Common
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
object GameBustOutWindow {
    const val BALL_MAXSPEED = 450f
    const val BALL_RADIUS = 12f
    const val BALL_SPEED = 250f

    //
    const val BOARD_ROWS = 12

    //
    //
    const val S_UNIQUE_CHANNEL = 6

    enum class collideDir_t {
        COLLIDE_NONE,  // = 0,
        COLLIDE_DOWN, COLLIDE_UP, COLLIDE_LEFT, COLLIDE_RIGHT
    }

    enum class powerupType_t {
        POWERUP_NONE,  //= 0,
        POWERUP_BIGPADDLE, POWERUP_MULTIBALL
    }

    internal class BOEntity(  //
        var game: idGameBustOutWindow?
    ) {
        var color: idVec4?
        var fadeOut: Boolean
        var material: idMaterial?

        //
        var materialName: idStr?
        var position: idVec2? = null

        //
        var powerup: powerupType_t?

        //
        var removed: Boolean
        var velocity: idVec2? = null
        var visible = true
        var width: Float
        var height: Float

        // virtual					~BOEntity();
        fun WriteToSaveGame(savefile: idFile?) {
            savefile.WriteBool(visible)
            game.WriteSaveGameString(materialName.toString(), savefile)
            savefile.WriteFloat(width)
            savefile.WriteFloat(height)
            savefile.Write(color)
            savefile.Write(position)
            savefile.Write(velocity)
            savefile.WriteInt(powerup)
            savefile.WriteBool(removed)
            savefile.WriteBool(fadeOut)
        }

        fun ReadFromSaveGame(savefile: idFile?, _game: idGameBustOutWindow?) {
            game = _game
            visible = savefile.ReadBool()
            game.ReadSaveGameString(materialName, savefile)
            SetMaterial(materialName.toString())
            width = savefile.ReadFloat()
            height = savefile.ReadFloat()
            savefile.Read(color)
            savefile.Read(position)
            savefile.Read(velocity)
            powerup = powerupType_t.values()[savefile.ReadInt()]
            removed = savefile.ReadBool()
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

        fun SetColor(r: Float, g: Float, b: Float, a: Float) {
            color.x = r
            color.y = g
            color.z = b
            color.w = a
        }

        fun SetVisible(isVisible: Boolean) {
            visible = isVisible
        }

        fun Update(timeslice: Float, guiTime: Int) {
            if (!visible) {
                return
            }

            // Move the entity
            position.plusAssign(velocity.oMultiply(timeslice))

            // Fade out the ent
            if (fadeOut) {
                color.w -= (timeslice * 2.5).toFloat()
                if (color.w <= 0f) {
                    color.w = 0f
                    removed = true
                }
            }
        }

        fun Draw(dc: idDeviceContext?) {
            if (visible) {
                dc.DrawMaterialRotated(
                    position.x,
                    position.y,
                    width,
                    height,
                    material,
                    color,
                    1.0f,
                    1.0f,
                    Math_h.DEG2RAD(0f)
                )
            }
        }

        //
        init {
            materialName = idStr("")
            material = null
            height = 8f
            width = height
            color = Lib.Companion.colorWhite
            powerup = powerupType_t.POWERUP_NONE
            position.Zero()
            velocity.Zero()
            removed = false
            fadeOut = false //0;
        }
    }

    /*
     *****************************************************************************
     * BOBrick
     ****************************************************************************
     */
    internal class BOBrick {
        //
        var ent: BOEntity?
        var height: Float

        //
        var isBroken: Boolean
        var powerup: powerupType_t?
        var width: Float
        var x: Float
        var y: Float

        //
        constructor() {
            ent = null
            height = 0f
            width = height
            y = width
            x = y
            powerup = powerupType_t.POWERUP_NONE
            isBroken = false
        }

        constructor(_ent: BOEntity?, _x: Float, _y: Float, _width: Float, _height: Float) {
            ent = _ent
            x = _x
            y = _y
            width = _width
            height = _height
            powerup = powerupType_t.POWERUP_NONE
            isBroken = false
            ent.position.x = x
            ent.position.y = y
            ent.SetSize(width, height)
            ent.SetMaterial("game/bustout/brick")
            ent.game.entities.Append(ent)
        }

        // ~BOBrick();
        fun WriteToSaveGame(savefile: idFile?) {
            savefile.WriteFloat(x)
            savefile.WriteFloat(y)
            savefile.WriteFloat(width)
            savefile.WriteFloat(height)
            savefile.WriteInt(powerup)
            savefile.WriteBool(isBroken)
            val index = ent.game.entities.FindIndex(ent)
            savefile.WriteInt(index)
        }

        fun ReadFromSaveGame(savefile: idFile?, game: idGameBustOutWindow?) {
            x = savefile.ReadFloat()
            y = savefile.ReadFloat()
            width = savefile.ReadFloat()
            height = savefile.ReadFloat()
            powerup = powerupType_t.values()[savefile.ReadInt()]
            isBroken = savefile.ReadBool()
            val index: Int
            index = savefile.ReadInt()
            ent = game.entities.get(index)
        }

        fun SetColor(bcolor: idVec4?) {
            ent.SetColor(bcolor.x, bcolor.y, bcolor.z, bcolor.w)
        }

        fun checkCollision(pos: idVec2?, vel: idVec2?): collideDir_t? {
            val ptA = idVec2()
            val ptB = idVec2()
            var dist: Float
            var result = collideDir_t.COLLIDE_NONE
            if (isBroken) {
                return result
            }

            // Check for collision with each edge
            var vec: idVec2?

            // Bottom
            ptA.x = x
            ptA.y = y + height
            ptB.x = x + width
            ptB.y = y + height
            if (vel.y < 0 && pos.y > ptA.y) {
                if (pos.x > ptA.x && pos.x < ptB.x) {
                    dist = pos.y - ptA.y
                    if (dist < BALL_RADIUS) {
                        result = collideDir_t.COLLIDE_DOWN
                    }
                } else {
                    vec = if (pos.x <= ptA.x) {
                        pos.oMinus(ptA)
                    } else {
                        pos.oMinus(ptB)
                    }
                    if (Math.abs(vec.y) > Math.abs(vec.x) && vec.LengthFast() < BALL_RADIUS) {
                        result = collideDir_t.COLLIDE_DOWN
                    }
                }
            }
            if (result == collideDir_t.COLLIDE_NONE) {
                // Top
                ptA.y = y
                ptB.y = y
                if (vel.y > 0 && pos.y < ptA.y) {
                    if (pos.x > ptA.x && pos.x < ptB.x) {
                        dist = ptA.y - pos.y
                        if (dist < BALL_RADIUS) {
                            result = collideDir_t.COLLIDE_UP
                        }
                    } else {
                        vec = if (pos.x <= ptA.x) {
                            pos.oMinus(ptA)
                        } else {
                            pos.oMinus(ptB)
                        }
                        if (Math.abs(vec.y) > Math.abs(vec.x) && vec.LengthFast() < BALL_RADIUS) {
                            result = collideDir_t.COLLIDE_UP
                        }
                    }
                }
                if (result == collideDir_t.COLLIDE_NONE) {
                    // Left side
                    ptA.x = x
                    ptA.y = y
                    ptB.x = x
                    ptB.y = y + height
                    if (vel.x > 0 && pos.x < ptA.x) {
                        if (pos.y > ptA.y && pos.y < ptB.y) {
                            dist = ptA.x - pos.x
                            if (dist < BALL_RADIUS) {
                                result = collideDir_t.COLLIDE_LEFT
                            }
                        } else {
                            vec = if (pos.y <= ptA.y) {
                                pos.oMinus(ptA)
                            } else {
                                pos.oMinus(ptB)
                            }
                            if (Math.abs(vec.x) >= Math.abs(vec.y) && vec.LengthFast() < BALL_RADIUS) {
                                result = collideDir_t.COLLIDE_LEFT
                            }
                        }
                    }
                    if (result == collideDir_t.COLLIDE_NONE) {
                        // Right side
                        ptA.x = x + width
                        ptB.x = x + width
                        if (vel.x < 0 && pos.x > ptA.x) {
                            if (pos.y > ptA.y && pos.y < ptB.y) {
                                dist = pos.x - ptA.x
                                if (dist < BALL_RADIUS) {
                                    result = collideDir_t.COLLIDE_LEFT
                                }
                            } else {
                                vec = if (pos.y <= ptA.y) {
                                    pos.oMinus(ptA)
                                } else {
                                    pos.oMinus(ptB)
                                }
                                if (Math.abs(vec.x) >= Math.abs(vec.y) && vec.LengthFast() < BALL_RADIUS) {
                                    result = collideDir_t.COLLIDE_LEFT
                                }
                            }
                        }
                    }
                }
            }
            return result
        }
    }

    //    
    /*
     *****************************************************************************
     * idGameBustOutWindow
     ****************************************************************************
     */
    class idGameBustOutWindow : idWindow {
        //
        val entities: idList<BOEntity?>? = idList()
        private var ballHitCeiling = false

        //
        private var ballSpeed = 0f

        //
        private val balls: idList<BOEntity?>? = idList()
        private var ballsInPlay = 0
        private var ballsRemaining = 0

        //
        private var bigPaddleTime = 0
        private val board: Array<idList<BOBrick?>?>? = arrayOfNulls<idList<*>?>(BOARD_ROWS)
        private var boardDataLoaded = false
        private var currentLevel = 0
        private var gameOver = false
        private var gameScore = 0
        private val gamerunning: idWinBool? = null
        private var levelBoardData: ByteArray?
        private var nextBallScore = 0

        //
        private var numBricks = 0

        //
        private var numLevels = 0
        private val onContinue: idWinBool? = null
        private val onFire: idWinBool? = null
        private val onNewGame: idWinBool? = null
        private val onNewLevel: idWinBool? = null

        //
        private var paddle: BOBrick? = null
        private var paddleVelocity = 0f
        private val powerUps: idList<BOEntity?>? = idList()

        //
        //
        //
        private var timeSlice = 0f

        //
        private var updateScore = false

        //	// ~idGameBustOutWindow();
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
            onNewLevel.WriteToSaveGame(savefile)
            savefile.WriteFloat(timeSlice)
            savefile.WriteBool(gameOver)
            savefile.WriteInt(numLevels)

            // Board Data is loaded when GUI is loaded, don't need to save
            savefile.WriteInt(numBricks)
            savefile.WriteInt(currentLevel)
            savefile.WriteBool(updateScore)
            savefile.WriteInt(gameScore)
            savefile.WriteInt(nextBallScore)
            savefile.WriteInt(bigPaddleTime)
            savefile.WriteFloat(paddleVelocity)
            savefile.WriteFloat(ballSpeed)
            savefile.WriteInt(ballsRemaining)
            savefile.WriteInt(ballsInPlay)
            savefile.WriteBool(ballHitCeiling)

            // Write Entities
            var i: Int
            var numberOfEnts = entities.Num()
            savefile.WriteInt(numberOfEnts)
            i = 0
            while (i < numberOfEnts) {
                entities.get(i).WriteToSaveGame(savefile)
                i++
            }

            // Write Balls
            numberOfEnts = balls.Num()
            savefile.WriteInt(numberOfEnts)
            i = 0
            while (i < numberOfEnts) {
                val ballIndex = entities.FindIndex(balls.get(i))
                savefile.WriteInt(ballIndex)
                i++
            }

            // Write Powerups
            numberOfEnts = powerUps.Num()
            savefile.WriteInt(numberOfEnts)
            i = 0
            while (i < numberOfEnts) {
                val powerIndex = entities.FindIndex(powerUps.get(i))
                savefile.WriteInt(powerIndex)
                i++
            }

            // Write paddle
            paddle.WriteToSaveGame(savefile)

            // Write Bricks
            var row: Int
            row = 0
            while (row < BOARD_ROWS) {
                numberOfEnts = board.get(row).Num()
                savefile.WriteInt(numberOfEnts)
                i = 0
                while (i < numberOfEnts) {
                    board.get(row).get(i).WriteToSaveGame(savefile)
                    i++
                }
                row++
            }
        }

        override fun ReadFromSaveGame(savefile: idFile?) {
            super.ReadFromSaveGame(savefile)

            // Clear out existing paddle and entities from GUI load
//	delete paddle;
            entities.DeleteContents(true)
            gamerunning.ReadFromSaveGame(savefile)
            onFire.ReadFromSaveGame(savefile)
            onContinue.ReadFromSaveGame(savefile)
            onNewGame.ReadFromSaveGame(savefile)
            onNewLevel.ReadFromSaveGame(savefile)
            timeSlice = savefile.ReadFloat()
            gameOver = savefile.ReadBool()
            numLevels = savefile.ReadInt()

            // Board Data is loaded when GUI is loaded, don't need to save
            numBricks = savefile.ReadInt()
            currentLevel = savefile.ReadInt()
            updateScore = savefile.ReadBool()
            gameScore = savefile.ReadInt()
            nextBallScore = savefile.ReadInt()
            bigPaddleTime = savefile.ReadInt()
            paddleVelocity = savefile.ReadFloat()
            ballSpeed = savefile.ReadFloat()
            ballsRemaining = savefile.ReadInt()
            ballsInPlay = savefile.ReadInt()
            ballHitCeiling = savefile.ReadBool()
            var i: Int
            var numberOfEnts: Int

            // Read entities
            numberOfEnts = savefile.ReadInt()
            i = 0
            while (i < numberOfEnts) {
                var ent: BOEntity
                ent = BOEntity(this)
                ent.ReadFromSaveGame(savefile, this)
                entities.Append(ent)
                i++
            }

            // Read balls
            numberOfEnts = savefile.ReadInt()
            i = 0
            while (i < numberOfEnts) {
                var ballIndex: Int
                ballIndex = savefile.ReadInt()
                balls.Append(entities.get(ballIndex))
                i++
            }

            // Read powerups
            numberOfEnts = savefile.ReadInt()
            i = 0
            while (i < numberOfEnts) {
                var powerIndex: Int
                powerIndex = savefile.ReadInt()
                balls.Append(entities.get(powerIndex))
                i++
            }

            // Read paddle
            paddle = BOBrick()
            paddle.ReadFromSaveGame(savefile, this)

            // Read board
            var row: Int
            row = 0
            while (row < BOARD_ROWS) {
                numberOfEnts = savefile.ReadInt()
                i = 0
                while (i < numberOfEnts) {
                    val brick = BOBrick()
                    brick.ReadFromSaveGame(savefile, this)
                    board.get(row).Append(brick)
                    i++
                }
                row++
            }
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
                    if (ballsInPlay == 0) {
                        val ball = CreateNewBall()
                        ball.SetVisible(true)
                        ball.position.x = paddle.ent.position.x + 48f
                        ball.position.y = 430f
                        ball.velocity.x = ballSpeed
                        ball.velocity.y = -ballSpeed * 2f
                        ball.velocity.NormalizeFast()
                        ball.velocity.oMulSet(ballSpeed)
                    }
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

        //        
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
            } else if (idStr.Companion.Icmp(_name, "onNewLevel") == 0) {
                retVar = onNewLevel
            }
            return retVar ?: super.GetWinVarByName(_name, winLookup, owner)
        }

        private fun CommonInit() {
            val ent: BOEntity

            // Precache images
            DeclManager.declManager.FindMaterial("game/bustout/ball")
            DeclManager.declManager.FindMaterial("game/bustout/doublepaddle")
            DeclManager.declManager.FindMaterial("game/bustout/powerup_bigpaddle")
            DeclManager.declManager.FindMaterial("game/bustout/powerup_multiball")
            DeclManager.declManager.FindMaterial("game/bustout/brick")

            // Precache sounds
            DeclManager.declManager.FindSound("arcade_ballbounce")
            DeclManager.declManager.FindSound("arcade_brickhit")
            DeclManager.declManager.FindSound("arcade_missedball")
            DeclManager.declManager.FindSound("arcade_sadsound")
            DeclManager.declManager.FindSound("arcade_extraball")
            DeclManager.declManager.FindSound("arcade_powerup")
            ResetGameState()
            numLevels = 0
            boardDataLoaded = false
            levelBoardData = null

            // Create Paddle
            ent = BOEntity(this)
            paddle = BOBrick(ent, 260f, 440f, 96f, 24f)
            paddle.ent.SetMaterial("game/bustout/paddle")
        }

        private fun ResetGameState() {
            gamerunning.data = false
            gameOver = false
            onFire.data = false
            onContinue.data = false
            onNewGame.data = false
            onNewLevel.data = false

            // Game moves forward 16 milliseconds every frame
            timeSlice = 0.016f
            ballsRemaining = 3
            ballSpeed = BALL_SPEED
            ballsInPlay = 0
            updateScore = false
            numBricks = 0
            currentLevel = 1
            gameScore = 0
            bigPaddleTime = 0
            nextBallScore = gameScore + 10000
            ClearBoard()
        }

        private fun ClearBoard() {
            var i: Int
            var j: Int
            ClearPowerups()
            ballHitCeiling = false
            i = 0
            while (i < BOARD_ROWS) {
                j = 0
                while (j < board.get(i).Num()) {
                    val brick = board.get(i).get(j)
                    brick.ent.removed = true
                    j++
                }
                board.get(i).DeleteContents(true)
                i++
            }
        }

        private fun ClearPowerups() {
            while (powerUps.Num() != 0) {
                powerUps.get(0).removed = true
                powerUps.RemoveIndex(0)
            }
        }

        private fun ClearBalls() {
            while (balls.Num() != 0) {
                balls.get(0).removed = true
                balls.RemoveIndex(0)
            }
            ballsInPlay = 0
        }

        private fun LoadBoardFiles() {
            var i: Int
            val w = IntArray(1)
            val h = IntArray(1)
            val   /*ID_TIME_T*/time = LongArray(1)
            val boardSize: Int
            val currentBoard: ByteArray?
            var boardIndex = 0
            if (boardDataLoaded) {
                return
            }
            boardSize = 9 * 12 * 4
            levelBoardData = ByteArray(boardSize * numLevels) // Mem_Alloc(boardSize * numLevels);
            currentBoard = levelBoardData
            i = 0
            while (i < numLevels) {
                var name = "guis/assets/bustout/level"
                name += i + 1
                name += ".tga"
                var pic = Image_files.R_LoadImage(name, w, h, time, false)
                if (pic != null) {
                    if (w[0] != 9 || h[0] != 12) {
                        Common.common.DWarning("Hell Bust-Out level image not correct dimensions! (%d x %d)", w, h)
                    }

//			memcpy( currentBoard, pic, boardSize );
                    System.arraycopy(pic.array(), 0, currentBoard, boardIndex, boardSize)
                    pic = null //Mem_Free(pic);
                }
                boardIndex += boardSize
                i++
            }
            boardDataLoaded = true
        }

        private fun SetCurrentBoard() {
            var i: Int
            var j: Int
            val realLevel = (currentLevel - 1) % numLevels
            val boardSize: Int
            val currentBoard: Int
            var bx = 11f
            var by = 24f
            val stepx = 619f / 9f
            val stepy = 256 / 12f
            boardSize = 9 * 12 * 4
            currentBoard = realLevel * boardSize
            j = 0
            while (j < BOARD_ROWS) {
                bx = 11f
                i = 0
                while (i < 9) {
                    val pixelindex = j * 9 * 4 + i * 4
                    if (levelBoardData.get(currentBoard + pixelindex + 3) != 0) {
                        val bcolor = idVec4()
                        var pType: Float //= 0f;
                        val bent = BOEntity(this)
                        val brick = BOBrick(bent, bx, by, stepx, stepy)
                        bcolor.x = levelBoardData.get(currentBoard + pixelindex + 0) / 255f
                        bcolor.y = levelBoardData.get(currentBoard + pixelindex + 1) / 255f
                        bcolor.z = levelBoardData.get(currentBoard + pixelindex + 2) / 255f
                        bcolor.w = 1f
                        brick.SetColor(bcolor)
                        pType = levelBoardData.get(pixelindex + 3) / 255f
                        if (pType > 0f && pType < 1f) {
                            if (pType < 0.5f) {
                                brick.powerup = powerupType_t.POWERUP_BIGPADDLE
                            } else {
                                brick.powerup = powerupType_t.POWERUP_MULTIBALL
                            }
                        }
                        board.get(j).Append(brick)
                        numBricks++
                    }
                    bx += stepx
                    i++
                }
                by += stepy
                j++
            }
        }

        private fun UpdateGame() {
            var i: Int
            if (onNewGame.oCastBoolean()) {
                ResetGameState()

                // Create Board
                SetCurrentBoard()
                gamerunning.oSet(true)
            }
            if (onContinue.oCastBoolean()) {
                gameOver = false
                ballsRemaining = 3
                onContinue.oSet(false)
            }
            if (onNewLevel.oCastBoolean()) {
                currentLevel++
                ClearBoard()
                SetCurrentBoard()
                ballSpeed = BALL_SPEED * (1f + currentLevel.toFloat() / 5f)
                if (ballSpeed > BALL_MAXSPEED) {
                    ballSpeed = BALL_MAXSPEED
                }
                updateScore = true
                onNewLevel.oSet(false)
            }
            if (gamerunning.oCastBoolean() == true) {
                UpdatePaddle()
                UpdateBall()
                UpdatePowerups()
                i = 0
                while (i < entities.Num()) {
                    entities.get(i).Update(timeSlice, gui.GetTime())
                    i++
                }

                // Delete entities that need to be deleted
                i = entities.Num() - 1
                while (i >= 0) {
                    if (entities.get(i).removed) {
                        val ent = entities.get(i)
                        //				delete ent;
                        entities.RemoveIndex(i)
                    }
                    i--
                }
                if (updateScore) {
                    UpdateScore()
                    updateScore = false
                }
            }
        }

        private fun UpdatePowerups() {
            val pos = idVec2()
            for (i in 0 until powerUps.Num()) {
                val pUp = powerUps.get(i)

                // Check for powerup falling below screen
                if (pUp.position.y > 480) {
                    powerUps.RemoveIndex(i)
                    pUp.removed = true
                    continue
                }

                // Check for the paddle catching a powerup
                pos.x = pUp.position.x + pUp.width / 2
                pos.y = pUp.position.y + pUp.height / 2
                val collision = paddle.checkCollision(pos, pUp.velocity)
                if (collision != collideDir_t.COLLIDE_NONE) {
                    var ball: BOEntity?
                    when (pUp.powerup) {
                        powerupType_t.POWERUP_BIGPADDLE -> bigPaddleTime = gui.GetTime() + 15000
                        powerupType_t.POWERUP_MULTIBALL ->                             // Create 2 new balls in the spot of the existing ball
                        {
                            var b = 0
                            while (b < 2) {
                                ball = CreateNewBall()
                                ball.position = balls.get(0).position
                                ball.velocity = balls.get(0).velocity
                                if (b == 0) {
                                    ball.velocity.x -= 35f
                                } else {
                                    ball.velocity.x += 35f
                                }
                                ball.velocity.NormalizeFast()
                                ball.velocity.oMulSet(ballSpeed)
                                ball.SetVisible(true)
                                b++
                            }
                        }
                        else -> {}
                    }

                    // Play the sound
                    Session.Companion.session.sw.PlayShaderDirectly("arcade_powerup", S_UNIQUE_CHANNEL)

                    // Remove it
                    powerUps.RemoveIndex(i)
                    pUp.removed = true
                }
            }
        }

        private fun UpdatePaddle() {
            val cursorPos = idVec2()
            val oldPos = paddle.x
            cursorPos.x = gui.CursorX()
            cursorPos.y = gui.CursorY()
            if (bigPaddleTime > gui.GetTime()) {
                paddle.x = cursorPos.x - 80f
                paddle.width = 160f
                paddle.ent.width = 160f
                paddle.ent.SetMaterial("game/bustout/doublepaddle")
            } else {
                paddle.x = cursorPos.x - 48f
                paddle.width = 96f
                paddle.ent.width = 96f
                paddle.ent.SetMaterial("game/bustout/paddle")
            }
            paddle.ent.position.x = paddle.x
            paddleVelocity = paddle.x - oldPos
        }

        private fun UpdateBall() {
            var ballnum: Int
            var i: Int
            var j: Int
            var playSoundBounce = false
            var playSoundBrick = false
            if (ballsInPlay == 0) {
                return
            }
            ballnum = 0
            while (ballnum < balls.Num()) {
                val ball = balls.get(ballnum)

                // Check for ball going below screen, lost ball
                if (ball.position.y > 480f) {
                    ball.removed = true
                    ballnum++
                    continue
                }

                // Check world collision
                if (ball.position.y < 20 && ball.velocity.y < 0) {
                    ball.velocity.y = -ball.velocity.y

                    // Increase ball speed when it hits ceiling
                    if (!ballHitCeiling) {
                        ballSpeed *= 1.25f
                        ballHitCeiling = true
                    }
                    playSoundBounce = true
                }
                if (ball.position.x > 608 && ball.velocity.x > 0) {
                    ball.velocity.x = -ball.velocity.x
                    playSoundBounce = true
                } else if (ball.position.x < 8 && ball.velocity.x < 0) {
                    ball.velocity.x = -ball.velocity.x
                    playSoundBounce = true
                }

                // Check for Paddle collision
                val ballCenter = ball.position.oPlus(idVec2(BALL_RADIUS, BALL_RADIUS))
                var collision = paddle.checkCollision(ballCenter, ball.velocity)
                if (collision == collideDir_t.COLLIDE_UP) {
                    if (ball.velocity.y > 0) {
                        val paddleVec = idVec2(paddleVelocity * 2, 0)
                        var centerX: Float
                        centerX = if (bigPaddleTime > gui.GetTime()) {
                            paddle.x + 80f
                        } else {
                            paddle.x + 48f
                        }
                        ball.velocity.y = -ball.velocity.y
                        paddleVec.x += (ball.position.x - centerX) * 2
                        ball.velocity.plusAssign(paddleVec)
                        ball.velocity.NormalizeFast()
                        ball.velocity.oMulSet(ballSpeed)
                        playSoundBounce = true
                    }
                } else if (collision == collideDir_t.COLLIDE_LEFT || collision == collideDir_t.COLLIDE_RIGHT) {
                    if (ball.velocity.y > 0) {
                        ball.velocity.x = -ball.velocity.x
                        playSoundBounce = true
                    }
                }
                collision = collideDir_t.COLLIDE_NONE

                // Check for collision with bricks
                i = 0
                while (i < BOARD_ROWS) {
                    val num = board.get(i).Num()
                    j = 0
                    while (j < num) {
                        val brick = board.get(i).get(j)
                        collision = brick.checkCollision(ballCenter, ball.velocity)
                        if (collision != null) {
                            // Now break the brick if there was a collision
                            brick.isBroken = true
                            brick.ent.fadeOut = true
                            if (brick.powerup.ordinal > powerupType_t.POWERUP_NONE.ordinal) {
                                val pUp = CreatePowerup(brick)
                            }
                            numBricks--
                            gameScore += 100
                            updateScore = true

                            // Go ahead an forcibly remove the last brick, no fade
                            if (numBricks == 0) {
                                brick.ent.removed = true
                            }
                            board.get(i).Remove(brick)
                            break
                        }
                        j++
                    }
                    if (collision != null) {
                        playSoundBrick = true
                        break
                    }
                    i++
                }
                if (collision == collideDir_t.COLLIDE_DOWN || collision == collideDir_t.COLLIDE_UP) {
                    ball.velocity.y *= -1f
                } else if (collision == collideDir_t.COLLIDE_LEFT || collision == collideDir_t.COLLIDE_RIGHT) {
                    ball.velocity.x *= -1f
                }
                if (playSoundBounce) {
                    Session.Companion.session.sw.PlayShaderDirectly("arcade_ballbounce", bounceChannel)
                } else if (playSoundBrick) {
                    Session.Companion.session.sw.PlayShaderDirectly("arcade_brickhit", bounceChannel)
                }
                if (playSoundBounce || playSoundBrick) {
                    bounceChannel++
                    if (bounceChannel == 4) {
                        bounceChannel = 1
                    }
                }
                ballnum++
            }

            // Check to see if any balls were removed from play
            ballnum = 0
            while (ballnum < balls.Num()) {
                if (balls.get(ballnum).removed) {
                    ballsInPlay--
                    balls.RemoveIndex(ballnum)
                }
                ballnum++
            }

            // If all the balls were removed, update the game accordingly
            if (ballsInPlay == 0) {
                if (ballsRemaining == 0) {
                    gameOver = true

                    // Game Over sound
                    Session.Companion.session.sw.PlayShaderDirectly("arcade_sadsound", S_UNIQUE_CHANNEL)
                } else {
                    ballsRemaining--

                    // Ball was lost, but game is not over
                    Session.Companion.session.sw.PlayShaderDirectly("arcade_missedball", S_UNIQUE_CHANNEL)
                }
                ClearPowerups()
                updateScore = true
            }
        }

        private fun UpdateScore() {
            if (gameOver) {
                gui.HandleNamedEvent("GameOver") //TODO:put text in property files for localization.
                return
            }

            // Check for level progression
            if (numBricks == 0) {
                ClearBalls()
                gui.HandleNamedEvent("levelComplete")
            }

            // Check for new ball score
            if (gameScore >= nextBallScore) {
                ballsRemaining++
                gui.HandleNamedEvent("extraBall")

                // Play sound
                Session.Companion.session.sw.PlayShaderDirectly("arcade_extraball", S_UNIQUE_CHANNEL)
                nextBallScore = gameScore + 10000
            }
            gui.SetStateString("player_score", Str.va("%d", gameScore))
            gui.SetStateString("balls_remaining", Str.va("%d", ballsRemaining))
            gui.SetStateString("current_level", Str.va("%d", currentLevel))
            gui.SetStateString("next_ball_score", Str.va("%d", nextBallScore))
        }

        private fun CreateNewBall(): BOEntity? {
            val ball: BOEntity
            ball = BOEntity(this)
            ball.position.x = 300f
            ball.position.y = 416f
            ball.SetMaterial("game/bustout/ball")
            ball.SetSize(BALL_RADIUS * 2f, BALL_RADIUS * 2f)
            ball.SetVisible(false)
            ballsInPlay++
            balls.Append(ball)
            entities.Append(ball)
            return ball
        }

        private fun CreatePowerup(brick: BOBrick?): BOEntity? {
            val powerEnt = BOEntity(this)
            powerEnt.position.x = brick.x
            powerEnt.position.y = brick.y
            powerEnt.velocity.x = 0f
            powerEnt.velocity.y = 64f
            powerEnt.powerup = brick.powerup
            when (powerEnt.powerup) {
                powerupType_t.POWERUP_BIGPADDLE -> powerEnt.SetMaterial("game/bustout/powerup_bigpaddle")
                powerupType_t.POWERUP_MULTIBALL -> powerEnt.SetMaterial("game/bustout/powerup_multiball")
                else -> powerEnt.SetMaterial("textures/common/nodraw")
            }
            powerEnt.SetSize((619 / 9).toFloat(), (256 / 12).toFloat())
            powerEnt.SetVisible(true)
            powerUps.Append(powerEnt)
            entities.Append(powerEnt)
            return powerEnt
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
            if (idStr.Companion.Icmp(_name, "onNewLevel") == 0) {
                onNewLevel.oSet(src.ParseBool())
                return true
            }
            if (idStr.Companion.Icmp(_name, "numLevels") == 0) {
                numLevels = src.ParseInt()

                // Load all the level images
                LoadBoardFiles()
                return true
            }
            return super.ParseInternalVar(_name, src)
        }

        companion object {
            private var bounceChannel = 1
        }
    }
}