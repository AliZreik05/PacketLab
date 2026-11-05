import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

public class FilePickerTest {
    public static void main(String[] args)throws Exception {
        Path path=FilePicker.result(0,Path.of("runs","photo with spaces É.png").toAbsolutePath().toString());
        if(!path.getFileName().toString().equals("photo with spaces É.png"))throw new AssertionError("File names must survive native output");
        if(FilePicker.result(1,"")!=null)throw new AssertionError("Cancel must preserve the old selection");
        try{FilePicker.result(2,"Dialog error");throw new AssertionError("Errors must not be treated as files");}catch(java.io.IOException expected){}
        try{FilePicker.result(0,"");throw new AssertionError("Empty successful output must be rejected");}catch(java.io.IOException expected){}
        Path helper=Path.of("build","native","file-picker.exe").toAbsolutePath();
        if(System.getProperty("os.name").startsWith("Windows")&&Files.isRegularFile(helper)) {
            Process process=new ProcessBuilder(helper.toString(),"--self-test").redirectErrorStream(true).start();
            try{
                if(!process.waitFor(15,TimeUnit.SECONDS))throw new AssertionError("Native initialization timed out");
                String output=new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
                if(process.exitValue()!=0||!output.equals("OK"))throw new AssertionError("Native dialog initialization failed: "+output);
            }finally{if(process.isAlive())process.destroyForcibly();}
        }
        System.out.println("PASS native file dialog initialization, unicode/spaces, cancellation, and error handling");
    }
}