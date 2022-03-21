package neo.Renderer

import neo.Renderer.Model.dynamicModel_t
import neo.Renderer.Model.idRenderModel
import neo.Renderer.Model.modelSurface_s
import neo.Renderer.Model.srfTriangles_s
import neo.Renderer.Model_local.idRenderModelStatic
import neo.Renderer.RenderWorld.renderEntity_s
import neo.Renderer.tr_local.viewDef_s
import neo.framework.DeclManager
import neo.framework.DeclManager.declType_t
import neo.framework.DeclParticle.idDeclParticle
import neo.framework.DeclParticle.particleGen_t
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.CInt
import neo.idlib.math.Random.idRandom
import java.util.*

/**
 *
 */
object Model_prt {
    val parametricParticle_SnapshotName: String? = "_ParametricParticle_Snapshot_"

    /*
     ===============================================================================

     PRT model

     ===============================================================================
     */
    class idRenderModelPrt : idRenderModelStatic() {
        //
        private var particleSystem: idDeclParticle? = null
        override fun InitFromFile(fileName: String?) {
            name = idStr(fileName)
            particleSystem = DeclManager.declManager.FindType(declType_t.DECL_PARTICLE, fileName) as idDeclParticle
        }

        override fun TouchData() {
            // Ensure our particle system is added to the list of referenced decls
            particleSystem = DeclManager.declManager.FindType(declType_t.DECL_PARTICLE, name) as idDeclParticle
        }

        override fun IsDynamicModel(): dynamicModel_t? {
            return dynamicModel_t.DM_CONTINUOUS
        }

        override fun InstantiateDynamicModel(
            renderEntity: renderEntity_s?,
            viewDef: viewDef_s?,
            cachedModel: idRenderModel?
        ): idRenderModel? {
            var cachedModel = cachedModel
            val staticModel: idRenderModelStatic?
            if (cachedModel != null && !RenderSystem_init.r_useCachedDynamicModels.GetBool()) {
//		delete cachedModel;
                cachedModel = null
            }

            // this may be triggered by a model trace or other non-view related source, to which we should look like an empty model
            if (renderEntity == null || viewDef == null) {
//		delete cachedModel;
                return null
            }
            if (RenderSystem_init.r_skipParticles.GetBool()) {
//		delete cachedModel;
                return null
            }

            /*
             // if the entire system has faded out
             if ( renderEntity.shaderParms[SHADERPARM_PARTICLE_STOPTIME] && viewDef.renderView.time * 0.001f >= renderEntity.shaderParms[SHADERPARM_PARTICLE_STOPTIME] ) {
             delete cachedModel;
             return null;
             }
             */if (cachedModel != null) {
                assert(cachedModel is idRenderModelStatic)
                assert(idStr.Companion.Icmp(cachedModel.Name(), Model_prt.parametricParticle_SnapshotName) == 0)
                staticModel = cachedModel as idRenderModelStatic?
            } else {
                staticModel = idRenderModelStatic()
                staticModel.InitEmpty(Model_prt.parametricParticle_SnapshotName)
            }
            val g = particleGen_t()
            g.renderEnt = renderEntity
            g.renderView = viewDef.renderView
            g.origin.Zero()
            g.axis.Identity()
            for (stageNum in 0 until particleSystem.stages.Num()) {
                val stage = particleSystem.stages.get(stageNum)
                if (null == stage.material) {
                    continue
                }
                if (0 == stage.cycleMsec) {
                    continue
                }
                if (stage.hidden) {        // just for gui particle editor use
                    staticModel.DeleteSurfaceWithId(stageNum)
                    continue
                }
                val steppingRandom = idRandom()
                val steppingRandom2 = idRandom()
                val stageAge =
                    (g.renderView.time + renderEntity.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] * 1000 - stage.timeOffset * 1000).toInt()
                val stageCycle = stageAge / stage.cycleMsec
                //                int inCycleTime = stageAge - stageCycle * stage.cycleMsec;

                // some particles will be in this cycle, some will be in the previous cycle
                steppingRandom.SetSeed(stageCycle shl 10 and idRandom.Companion.MAX_RAND xor (renderEntity.shaderParms[RenderWorld.SHADERPARM_DIVERSITY] * idRandom.Companion.MAX_RAND).toInt())
                steppingRandom2.SetSeed(stageCycle - 1 shl 10 and idRandom.Companion.MAX_RAND xor (renderEntity.shaderParms[RenderWorld.SHADERPARM_DIVERSITY] * idRandom.Companion.MAX_RAND).toInt())
                val count = stage.totalParticles * stage.NumQuadsPerParticle()
                val surfaceNum = CInt()
                var surf: modelSurface_s?
                if (staticModel.FindSurfaceWithId(stageNum, surfaceNum)) {
                    surf = staticModel.surfaces.get(surfaceNum._val)
                    tr_trisurf.R_FreeStaticTriSurfVertexCaches(surf.geometry)
                } else {
                    surf = staticModel.surfaces.Alloc()
                    surf.id = stageNum
                    surf.shader = stage.material
                    surf.geometry = srfTriangles_s() //R_AllocStaticTriSurf();
                    tr_trisurf.R_AllocStaticTriSurfVerts(surf.geometry, 4 * count)
                    tr_trisurf.R_AllocStaticTriSurfIndexes(surf.geometry, 6 * count)
                    tr_trisurf.R_AllocStaticTriSurfPlanes(surf.geometry, 6 * count)
                }
                var numVerts = 0
                val verts = surf.geometry.verts
                for (index in 0 until stage.totalParticles) {
                    g.index = index

                    // bump the random
                    steppingRandom.RandomInt()
                    steppingRandom2.RandomInt()

                    // calculate local age for this index
                    val bunchOffset =
                        (stage.particleLife * 1000 * stage.spawnBunching * index / stage.totalParticles).toInt()
                    val particleAge = stageAge - bunchOffset
                    val particleCycle = particleAge / stage.cycleMsec
                    if (particleCycle < 0) {
                        // before the particleSystem spawned
                        continue
                    }
                    if (stage.cycles != 0f && particleCycle >= stage.cycles) {
                        // cycled systems will only run cycle times
                        continue
                    }
                    if (particleCycle == stageCycle) {
                        g.random = idRandom(steppingRandom)
                    } else {
                        g.random = idRandom(steppingRandom2)
                    }
                    val inCycleTime = particleAge - particleCycle * stage.cycleMsec
                    if (renderEntity.shaderParms[RenderWorld.SHADERPARM_PARTICLE_STOPTIME] != 0
                        && g.renderView.time - inCycleTime >= renderEntity.shaderParms[RenderWorld.SHADERPARM_PARTICLE_STOPTIME] * 1000
                    ) {
                        // don't fire any more particles
                        continue
                    }

                    // supress particles before or after the age clamp
                    g.frac = inCycleTime.toFloat() / (stage.particleLife * 1000)
                    if (g.frac < 0.0f) {
                        // yet to be spawned
                        continue
                    }
                    if (g.frac > 1.0f) {
                        // this particle is in the deadTime band
                        continue
                    }

                    // this is needed so aimed particles can calculate origins at different times
                    g.originalRandom = idRandom(g.random)
                    g.age = g.frac * stage.particleLife

                    // if the particle doesn't get drawn because it is faded out or beyond a kill region, don't increment the verts
                    numVerts += stage.CreateParticle(g, Arrays.copyOfRange(verts, numVerts, verts.size))
                }
                assert(numVerts and 3 == 0 && numVerts <= 4 * count)

                // build the indexes
                var numIndexes = 0
                /*glIndex_t*/
                val indexes = surf.geometry.indexes
                var i = 0
                while (i < numVerts) {
                    indexes[numIndexes + 0] = i
                    indexes[numIndexes + 1] = i + 2
                    indexes[numIndexes + 2] = i + 3
                    indexes[numIndexes + 3] = i
                    indexes[numIndexes + 4] = i + 3
                    indexes[numIndexes + 5] = i + 1
                    numIndexes += 6
                    i += 4
                }
                surf.geometry.tangentsCalculated = false
                surf.geometry.facePlanesCalculated = false
                surf.geometry.numVerts = numVerts
                surf.geometry.numIndexes = numIndexes
                surf.geometry.bounds.set(stage.bounds) // just always draw the particles
                val a = 0
            }
            return staticModel
        }

        override fun Bounds(ent: renderEntity_s?): idBounds {
            return particleSystem.bounds
        }

        override fun DepthHack(): Float {
            return particleSystem.depthHack
        }

        override fun Memory(): Int {
            var total = 0
            total += super.Memory()
            return total
        }
    }
}