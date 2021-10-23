package neo.Game.GameSys;

import neo.Game.Game_local;
import neo.idlib.containers.List.idList;
import neo.idlib.math.Matrix.idMat3;
import neo.idlib.math.Vector.idVec3;
import neo.idlib.math.Vector.idVec4;

import static neo.Game.Game_local.gameLocal;
import static neo.Game.Game_local.gameRenderWorld;

/**
 *
 */
public class DebugGraphy {

    public static class idDebugGraph {

        private int index;
        private idList<Float> samples;
        //
        //

        public idDebugGraph() {
            index = 0;
        }

        public void SetNumSamples(int num) {
            index = 0;
            samples.Clear();
            samples.SetNum(num);
//            memset(samples.Ptr(), 0, samples.MemoryUsed());
        }

        public void AddValue(float value) {
            samples.oSet(index++, value);
            if (index >= samples.Num()) {
                index = 0;
            }
        }

        public void Draw(final idVec4 color, float scale) {
            int i;
            float value1;
            float value2;
            final idVec3 vec1 = new idVec3();
            final idVec3 vec2 = new idVec3();

            idMat3 axis = gameLocal.GetLocalPlayer().viewAxis;
            final idVec3 pos = new idVec3(gameLocal.GetLocalPlayer().GetPhysics().GetOrigin().oPlus(axis.oGet(1).oMultiply(samples.Num() * 0.5f)));

            value1 = samples.oGet(index) * scale;
            for (i = 1; i < samples.Num(); i++) {
                value2 = samples.oGet((i + index) % samples.Num()) * scale;

                vec1.oSet(pos.oPlus(axis.oGet(2).oMultiply(value1).oMinus(axis.oGet(1).oMultiply(i - 1).oPlus(axis.oGet(0).oMultiply(samples.Num())))));
                vec2.oSet(pos.oPlus(axis.oGet(2).oMultiply(value2).oMinus(axis.oGet(1).oMultiply(i).oPlus(axis.oGet(0).oMultiply(samples.Num())))));

                gameRenderWorld.DebugLine(color, vec1, vec2, Game_local.idGameLocal.msec, false);
                value1 = value2;
            }
        }

    }

}
