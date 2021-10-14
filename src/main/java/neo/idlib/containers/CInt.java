package neo.idlib.containers;

public class CInt {
    private int val = 0;

    public CInt(int val) {
        this.val = val;
    }

    public CInt() {
    }

    public int getVal() {
        return val;
    }

    public void setVal(int val) {
        this.val = val;
    }

    public int increment() {
        return this.val++;
    }

    public int decrement() {
        return this.val--;
    }

    public void rightShift(int power) {
        this.val >>= power;
    }

    public void leftShit(int power) {
        this.val <<= power;
    }
}
