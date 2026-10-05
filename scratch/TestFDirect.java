package avt;

public class TestFDirect {
    public static void main(String[] args) {
        try {
            avt.Q.I(new Object[0]);
            String json = avt.Q.F(new Object[0]);
            System.out.println("DIRECT CALL avt.Q.F():\n" + json);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
