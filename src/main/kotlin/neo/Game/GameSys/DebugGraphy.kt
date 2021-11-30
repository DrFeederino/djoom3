package neo.Game.GameSys

import neo.Game.Game_local
import neo.Game.Game_local.idGameLocal
import neo.idlib.containers.List.idList
import neo.idlib.math.Vector.idVec3
import neo.idlib.math.Vector.idVec4

/**
 *
 */
class DebugGraphy {
    class idDebugGraph     //
    //
    {
        private var index = 0
        private val samples: idList<Float?>? = idList<Any?>()
        fun SetNumSamples(num: Int) {
            index = 0
            samples.Clear()
            samples.SetNum(num)
            //            memset(samples.Ptr(), 0, samples.MemoryUsed());
        }

        fun AddValue(value: Float) {
            samples.set(index++, value)
            if (index >= samples.Num()) {
                index = 0
            }
        }

        fun Draw(color: idVec4?, scale: Float) {
            var i: Int
            var value1: Float
            var value2: Float
            val vec1 = idVec3()
            val vec2 = idVec3()
            val axis = Game_local.gameLocal.GetLocalPlayer().viewAxis
            val pos = idVec3(
                Game_local.gameLocal.GetLocalPlayer().GetPhysics().GetOrigin()
                    .oPlus(axis.get(1).times(samples.Num() * 0.5f))
            )
            value1 = samples.get(index) * scale
            i = 1
            while (i < samples.Num()) {
                value2 = samples.get((i + index) % samples.Num()) * scale
                vec1.set(
                    pos.oPlus(
                        axis.get(2).times(value1).minus(
                            axis.get(1).times((i - 1).toFloat())
                                .oPlus(axis.get(0).times(samples.Num().toFloat()))
                        )
                    )
                )
                vec2.set(
                    pos.oPlus(
                        axis.get(2).times(value2).minus(
                            axis.get(1).times(i.toFloat()).oPlus(axis.get(0).times(samples.Num().toFloat()))
                        )
                    )
                )
                Game_local.gameRenderWorld.DebugLine(color, vec1, vec2, idGameLocal.Companion.msec, false)
                value1 = value2
                i++
            }
        }
    }
}