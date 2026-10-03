package cn.local.automerge;
import java.io.PrintWriter;
import java.io.StringWriter;

final class ErrorReport {
    static String describe(Throwable error) {
        StringWriter writer=new StringWriter();
        error.printStackTrace(new PrintWriter(writer));
        String full=writer.toString();
        return full.length()>10000?full.substring(0,10000):full;
    }
}
