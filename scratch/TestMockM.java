package avt;

import org.json.simple.S;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TestMockM {
    public static Object createMockT() throws Exception {
        Class<?> tClass = Class.forName("avt.t");
        Constructor<?> ctor = null;
        for (Constructor<?> c : tClass.getDeclaredConstructors()) {
            if (c.getParameterTypes().length == 11) {
                ctor = c;
                break;
            }
        }
        ctor.setAccessible(true);
        
        String key = "bypass_key";
        int accountId = 0;
        int accountLimit = -1;
        String expiresAt = "2099-12-31";
        String timeRemaining = "Không giới hạn (Vĩnh viễn)";
        String role = "user";
        List accounts = new ArrayList();
        S management = new S();
        List allowedModes = Arrays.asList("fishTool", "fishermanTool", "farmer", "combinedTool");
        S updateInfo = new S();
        updateInfo.put("hasUpdate", false);
        updateInfo.put("forceUpdate", false);
        updateInfo.put("currentVersion", "1.1.6");
        updateInfo.put("message", "Avatar 3x Tool - DebugToDeath v1.1.6");

        return ctor.newInstance(key, accountId, accountLimit, expiresAt, timeRemaining, role, accounts, management, allowedModes, updateInfo, null);
    }

    public static void main(String[] args) throws Exception {
        Object t = createMockT();
        System.out.println("Created mock t: " + t);
    }
}
