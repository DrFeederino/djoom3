package neo.idlib.containers;

// This class represents how floats are treated in C with pointers
public class CFloat {
    private float val = 0.0f;

    public CFloat() {
    }

    public CFloat(float val) {
        this.val = val;
    }

    public float getVal() {
        return val;
    }

    public void setVal(float val) {
        this.val = val;
    }


}
