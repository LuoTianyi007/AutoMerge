package cn.local.automerge;
import com.arthenica.smartexception.java.Exceptions;

/** Exercises the first call in FFmpegKitConfig's static initializer. */
public final class DependencySmokeTest {
    public static void main(String[] args) {
        Exceptions.registerRootPackage("com.arthenica");
        String report=Exceptions.getStackTraceString(new IllegalStateException("dependency-smoke",new RuntimeException("root-cause")));
        if(!report.contains("dependency-smoke")||!report.contains("root-cause"))throw new AssertionError(report);
        String detail=ErrorReport.describe(new NoClassDefFoundError("FFmpegKitConfig"));
        if(!detail.contains("NoClassDefFoundError")||!detail.contains("FFmpegKitConfig"))throw new AssertionError(detail);
        System.out.println("PASS: FFmpegKit initialization dependency and diagnostic smoke test");
    }
}
