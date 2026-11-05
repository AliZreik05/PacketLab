import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class BatchTest {
    public static void main(String[] args)throws Exception {
        var good=new BatchRunner.Condition(0,0,0);
        var bad=new BatchRunner.Condition(1,0,0);
        var stats=new RunStatistics.Snapshot(1,2,1,0,0,0);
        var more=new RunStatistics.Snapshot(1,4,1,0,0,0);
        var failed=new RunStatistics.Snapshot(1,9,1,0,0,0);
        List<BatchRunner.Trial> values=List.of(
                new BatchRunner.Trial(good,1,BatchRunner.Outcome.VERIFIED,2,stats,"",null),
                new BatchRunner.Trial(good,2,BatchRunner.Outcome.VERIFIED,4,more,"",null),
                new BatchRunner.Trial(good,3,BatchRunner.Outcome.FAILED,99,failed,"failure",null),
                new BatchRunner.Trial(good,4,BatchRunner.Outcome.CANCELLED,99,failed,"cancelled",null));
        var average=BatchRunner.aggregate(values,good);
        check(average.finished()==3&&average.cancelled()==1&&average.failed()==1,"Cancellation must not be counted as failure");
        check(Math.abs(average.successPercent()-200.0/3)<.001&&average.averageSeconds()==3&&average.averageRetries()==5,"Average denominators must exclude cancelled and unstarted trials");
        check(Double.isNaN(BatchRunner.aggregate(List.of(values.get(2)),good).averageSeconds()),"No verified runs means no successful duration");
        check(BatchRunner.conditions("0, 10, 10","0-0, 10-80").size()==4,"Matrix planning and duplicate removal");
        for(String loss:List.of("NaN","101","-1","5,,6"))try{BatchRunner.conditions(loss,"0-1");throw new AssertionError("Invalid loss accepted");}catch(IllegalArgumentException expected){}
        try{BatchRunner.conditions("0","80-10");throw new AssertionError("Invalid delay accepted");}catch(IllegalArgumentException expected){}
        Files.createDirectories(Path.of("runs"));Path root=Files.createTempDirectory(Path.of("runs").toAbsolutePath(),"batch-tests-");
        byte[] bytes=new byte[1025];new Random(9).nextBytes(bytes);Path input=root.resolve("sample.bin");Files.write(input,bytes);
        try(BatchRunner runner=new BatchRunner()) {
            var report=runner.run(input,root,List.of(good),2,trial->{
                try{check(Arrays.equals(bytes,Files.readAllBytes(trial.output())),"Every trial must use the original frozen input");Files.writeString(input,"changed after snapshot");}
                catch(Exception e){throw new RuntimeException(e);}
            },message->{});
            check(!report.cancelled()&&report.trials().size()==2&&BatchRunner.aggregate(report.trials(),good).verified()==2,"Repeats must verify independently");
        }
        System.out.println("PASS repeated verified transfers and stable input snapshot");
        Files.write(input,bytes);
        try(BatchRunner runner=new BatchRunner()) {
            var report=runner.run(input,root,List.of(bad,good),1,trial->{},message->{});
            check(report.trials().size()==2&&report.trials().get(0).outcome()==BatchRunner.Outcome.FAILED&&report.trials().get(1).outcome()==BatchRunner.Outcome.VERIFIED,"Failure must not abort subsequent conditions");
            check(Double.isNaN(BatchRunner.aggregate(report.trials(),bad).averageSeconds()),"Failed durations must not bias verified duration");
        }
        System.out.println("PASS retry-exhaustion accounting and continuation after failure");
        CountDownLatch sent=new CountDownLatch(1);ExecutorService executor=Executors.newSingleThreadExecutor();
        try(BatchRunner runner=new BatchRunner(line->{if(line.contains("[DATA TRANSMISSION]"))sent.countDown();})) {
            Future<BatchRunner.Report> task=executor.submit(()->runner.run(input,root,List.of(bad,good),2,trial->{},message->{}));
            check(sent.await(15,TimeUnit.SECONDS),"Cancelled trial must first reach actual UDP transmission");runner.close();
            var report=task.get(15,TimeUnit.SECONDS);
            check(report.cancelled()&&report.trials().size()==1&&report.trials().get(0).outcome()==BatchRunner.Outcome.CANCELLED,"Cancellation must stop active and queued trials");
            check(BatchRunner.aggregate(report.trials(),bad).finished()==0,"Cancelled trial excluded from completion rate");
        }finally{executor.shutdownNow();}
        System.out.println("PASS active cancellation and unstarted-trial exclusion");
        System.out.println("ALL BATCH CHECKS PASSED");
    }
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}