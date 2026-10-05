package avt;

public class TestHelperDirect {
    public static void main(String[] args) {
        try {
            System.out.println("Calling BypassHelper.apply()...");
            BypassHelper.apply();
            System.out.println("Calling avt.Q.F()...");
            MethodInvoker.callF();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
