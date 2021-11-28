package neo.Tools.Compilers.AAS

import neo.Tools.Compilers.AAS.AASFile.idAASFile
import neo.Tools.Compilers.AAS.AASFileManager
import neo.Tools.Compilers.AAS.AASFile_local.idAASFileLocal
import neo.idlib.Text.Str.idStr

/**
 *
 */
object AASFileManager {
    private val AASFileManagerLocal: idAASFileManagerLocal? = idAASFileManagerLocal()
    var AASFileManager: idAASFileManager? = AASFileManager.AASFileManagerLocal
    fun setAASFileManager(AASFileManager: idAASFileManager?) {
        AASFileManager.AASFileManagerLocal = AASFileManager as idAASFileManagerLocal?
        AASFileManager.AASFileManager = AASFileManager.AASFileManagerLocal
    }

    /*
     ===============================================================================

     AAS File Manager

     ===============================================================================
     */
    abstract class idAASFileManager {
        //	virtual						~idAASFileManager( void ) {}
        abstract fun LoadAAS(fileName: String?,    /*unsigned int*/mapFileCRC: Long): idAASFile?
        abstract fun FreeAAS(file: idAASFile?)
    }

    /*
     ===============================================================================

     AAS File Manager

     ===============================================================================
     */
    internal class idAASFileManagerLocal : idAASFileManager() {
        //        virtual						~idAASFileManagerLocal( void ) {}
        override fun LoadAAS(fileName: String?, mapFileCRC: Long): idAASFile? {
            val file = idAASFileLocal()
            return if (!file.Load(idStr(fileName), mapFileCRC)) {
//		delete file;
                null
            } else file
        }

        override fun FreeAAS(file: idAASFile?) {
//            delete file
        }
    }
}