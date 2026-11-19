import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;

// Temporary probe for issue #299.
//   Probe jna            loads kernel32 through JNA (set jna.boot.library.path and jna.debug_load* on the command line)
//   Probe load <dll>     calls System.load on the DLL directly, which shows the error JNA swallows
public class Probe {
    public static void main(String[] args) {
        System.out.println("java.version=" + System.getProperty("java.version")
            + " java.vm.vendor=" + System.getProperty("java.vm.vendor")
            + " os.arch=" + System.getProperty("os.arch")
            + " jna.boot.library.path=" + System.getProperty("jna.boot.library.path"));
        try {
            if (args[0].equals("load")) {
                System.load(args[1]);
                System.out.println("System.load OK: " + args[1]);
            } else {
                System.out.println("JNA " + Native.VERSION + " native API " + Native.VERSION_NATIVE
                    + " RESOURCE_PREFIX=" + Platform.RESOURCE_PREFIX);
                Native.load("kernel32", Library.class);
                System.out.println("Native.load(kernel32) OK");
            }
        } catch (Throwable t) {
            System.out.println("FAILED:");
            t.printStackTrace(System.out);
        }
    }
}
