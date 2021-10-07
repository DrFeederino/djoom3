package neo.Game.GameSys;

/**
 *
 */
public class NoGameTypeInfo {

    static classTypeInfo_t[] classTypeInfo = {
            new classTypeInfo_t(null, null, 0, null)
    };

    static constantInfo_t[] constantInfo = {
            new constantInfo_t(null, null, null)
    };

    static enumTypeInfo_t[] enumTypeInfo = {
            new enumTypeInfo_t(null, null)
    };

    /*
     ===================================================================================

     This file has been generated with the Type Info Generator v1.0 (c) 2004 id Software

     ===================================================================================
     */
    static class constantInfo_t {

        String name;
        String type;
        String value;

        public constantInfo_t(String name, String type, String value) {
            this.name = name;
            this.type = type;
            this.value = value;
        }
    }

    static class enumValueInfo_t {

        String name;
        int value;

        public enumValueInfo_t(String name, int value) {
            this.name = name;
            this.value = value;
        }
    }

    static class enumTypeInfo_t {

        String typeName;
        enumValueInfo_t[] values;

        public enumTypeInfo_t(String typeName, enumValueInfo_t[] values) {
            this.typeName = typeName;
            this.values = values;
        }
    }

    static class classVariableInfo_t {

        String name;
        int offset;
        int size;
        String type;
    }

    static class classTypeInfo_t {

        int size;
        String superType;
        String typeName;
        classVariableInfo_t[] variables;

        public classTypeInfo_t(String typeName, String superType, int size, classVariableInfo_t[] variables) {
            this.typeName = typeName;
            this.superType = superType;
            this.size = size;
            this.variables = variables;
        }
    }
}
