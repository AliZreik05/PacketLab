import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;

/** Uses Windows' Common Item Dialog; Java's platform dialog is the portable fallback. */
public final class FilePicker {
    public static Path choose(JFrame owner,Path initial) {
        Path helper=Path.of("build","native","file-picker.exe").toAbsolutePath();
        if(!System.getProperty("os.name").startsWith("Windows")||!Files.isRegularFile(helper))return platform(owner,initial);
        AtomicReference<Path> selection=new AtomicReference<>();AtomicReference<Exception> failure=new AtomicReference<>();
        SecondaryLoop loop=Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
        owner.setEnabled(false);
        new SwingWorker<Void,Void>() {
            @Override protected Void doInBackground()throws Exception {
                Process process=new ProcessBuilder(helper.toString(),initial==null?"":initial.toString(),Long.toString(ProcessHandle.current().pid())).redirectErrorStream(true).start();
                try {
                    String output=new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
                    selection.set(result(process.waitFor(),output));return null;
                } finally { if(process.isAlive())process.destroyForcibly(); }
            }
            @Override protected void done(){
                try{get();}catch(Exception e){failure.set(e.getCause() instanceof Exception cause?cause:e);}
                finally{owner.setEnabled(true);owner.toFront();loop.exit();}
            }
        }.execute();
        loop.enter();
        if(failure.get()!=null)JOptionPane.showMessageDialog(owner,failure.get().getMessage(),"Choose file",JOptionPane.ERROR_MESSAGE);
        return selection.get();
    }
    static Path result(int code,String output)throws IOException {
        if(code==1)return null;
        if(code!=0)throw new IOException(output.isBlank()?"The Windows file dialog could not open.":output.strip());
        if(output.isBlank())throw new IOException("The file dialog returned no selected file.");
        return Path.of(output.strip()).toAbsolutePath();
    }
    private static Path platform(JFrame owner,Path initial) {
        FileDialog dialog=new FileDialog(owner,"Choose a file to transfer",FileDialog.LOAD);
        try {
            if(initial!=null)dialog.setDirectory(initial.toString());
            dialog.setVisible(true);
            return dialog.getFile()==null?null:Path.of(dialog.getDirectory(),dialog.getFile()).toAbsolutePath();
        } finally {dialog.dispose();}
    }
}