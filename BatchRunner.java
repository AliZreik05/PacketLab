import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Sequential, isolated trials over one frozen copy of the selected file. */
public final class BatchRunner implements AutoCloseable {
    public record Condition(double loss,int minDelay,int maxDelay) {
        public Condition {
            if(!Double.isFinite(loss)||loss<0||loss>1||minDelay<0||maxDelay<minDelay||maxDelay>5000)
                throw new IllegalArgumentException("Loss must be 0-100%; delay must be an ordered range within 0-5000 ms.");
        }
    }
    public enum Outcome { VERIFIED,FAILED,CANCELLED }
    public record Trial(Condition condition,int repeat,Outcome outcome,double seconds,RunStatistics.Snapshot stats,String error,Path output) {}
    public record Aggregate(int verified,int failed,int cancelled,double successfulSeconds,double finishedRetries) {
        public int finished(){return verified+failed;}
        public double successPercent(){return finished()==0?Double.NaN:100.0*verified/finished();}
        public double averageSeconds(){return verified==0?Double.NaN:successfulSeconds/verified;}
        public double averageRetries(){return finished()==0?Double.NaN:finishedRetries/finished();}
    }
    public record Report(Path directory,List<Trial> trials,boolean cancelled) {}
    private volatile boolean cancelled;
    private LabSession active;
    private final Consumer<String> observer;
    public BatchRunner(){this(line->{});}
    BatchRunner(Consumer<String> observer){this.observer=observer;}

    public Report run(Path input,Path destination,List<Condition> conditions,int repeats,Consumer<Trial> completed,Consumer<String> progress)throws Exception {
        conditions=List.copyOf(conditions);
        if(conditions.isEmpty()||repeats<1||repeats>20||(long)conditions.size()*repeats>100)
            throw new IllegalArgumentException("Choose 1-100 total trials, with 1-20 repeats per condition.");
        if(!Files.isRegularFile(input))throw new IOException("Choose an existing file before starting the batch.");
        Files.createDirectories(destination);Path directory=Files.createTempDirectory(destination,"batch-");
        List<Trial> trials=new ArrayList<>();
        if(cancelled)return new Report(directory,List.of(),true);
        progress.accept("Preparing one fixed file snapshot for all trials...");
        Path snapshotFolder=Files.createDirectory(directory.resolve("input"));Path snapshot=snapshotFolder.resolve(input.getFileName());
        String before=LabSession.hash(input);
        try(InputStream source=Files.newInputStream(input);OutputStream target=Files.newOutputStream(snapshot)) {
            byte[] buffer=new byte[65536];int count;
            while((count=source.read(buffer))!=-1){if(cancelled)break;target.write(buffer,0,count);}
        }
        if(cancelled)return new Report(directory,List.of(),true);
        if(!before.equals(LabSession.hash(snapshot))||!before.equals(LabSession.hash(input)))throw new IOException("The input file changed while preparing the batch. Select a stable file and retry.");
        int total=conditions.size()*repeats;
        outer:for(Condition condition:conditions)for(int repeat=1;repeat<=repeats;repeat++) {
            if(cancelled)break outer;
            progress.accept("Running trial "+(trials.size()+1)+" / "+total);
            LabSession session;
            synchronized(this){if(cancelled)break outer;session=new LabSession(observer);active=session;}
            long started=System.nanoTime();Trial trial;
            try {
                LabSession.Result result=session.transfer(snapshot,directory,condition.loss(),condition.minDelay(),condition.maxDelay());
                trial=new Trial(condition,repeat,Outcome.VERIFIED,result.seconds(),result.statistics(),"",result.file());
            } catch(Exception error) {
                trial=new Trial(condition,repeat,cancelled||session.isCancelled()?Outcome.CANCELLED:Outcome.FAILED,
                        (System.nanoTime()-started)/1_000_000_000.0,session.statistics(),Objects.toString(error.getMessage(),error.getClass().getSimpleName()),null);
            } finally {
                session.close();synchronized(this){if(active==session)active=null;}
            }
            trials.add(trial);completed.accept(trial);
            if(cancelled)break outer;
        }
        return new Report(directory,List.copyOf(trials),cancelled);
    }
    public static Aggregate aggregate(List<Trial> trials,Condition condition) {
        int verified=0,failed=0,aborted=0;double duration=0,retries=0;
        for(Trial trial:trials)if(trial.condition().equals(condition)) {
            if(trial.outcome()==Outcome.CANCELLED){aborted++;continue;}
            retries+=trial.stats().retransmissions();
            if(trial.outcome()==Outcome.VERIFIED){verified++;duration+=trial.seconds();}else failed++;
        }
        return new Aggregate(verified,failed,aborted,duration,retries);
    }
    public static List<Condition> conditions(String losses,String ranges) {
        LinkedHashSet<Double> probabilities=new LinkedHashSet<>();
        try {
            for(String token:losses.split(",",-1)) {
                double value=Double.parseDouble(token.strip());
                if(!Double.isFinite(value)||value<0||value>100)throw new IllegalArgumentException();probabilities.add(value/100.0);
            }
            LinkedHashSet<Condition> combinations=new LinkedHashSet<>();
            for(String token:ranges.split(",",-1)) {
                String[] ends=token.strip().split("-",-1);if(ends.length!=2)throw new IllegalArgumentException();
                int low=Integer.parseInt(ends[0].strip()),high=Integer.parseInt(ends[1].strip());
                for(double probability:probabilities)combinations.add(new Condition(probability,low,high));
            }
            if(combinations.size()>100)throw new IllegalArgumentException();return List.copyOf(combinations);
        }catch(IllegalArgumentException error){throw new IllegalArgumentException("Use loss percentages such as 0, 5, 10 and delay ranges such as 10-80, 80-150 (maximum 5000 ms).");}
    }
    @Override public void close(){LabSession session;synchronized(this){cancelled=true;session=active;}if(session!=null)session.close();}
}