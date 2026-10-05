package avt;

import java.io.File;

public class ListNetworkClasses {
    public static void main(String[] args) {
        File dir = new File("f:/game/3x116/scratch/jar_unpacked/avt/network");
        if (dir.exists()) {
            for (File f : dir.listFiles()) {
                System.out.println("Network class: " + f.getName());
            }
        }
    }
}
