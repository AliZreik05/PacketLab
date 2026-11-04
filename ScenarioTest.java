import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/** Integration checks for controlled loss, including empty-file EOF handling. */
public class ScenarioTest {
    public static void main(String[] args)throws Exception {
        Files.createDirectories(Path.of("runs"));
        Path root=Files.createTempDirectory(Path.of("runs").toAbsolutePath(),"scenarios-");
        byte[] bytes=new byte[2049];new Random(7).nextBytes(bytes);
        for(Experiment scenario:List.of(Experiment.NORMAL,Experiment.DROP_DATA,Experiment.DROP_ACK)) verify(root,bytes,scenario);
        verify(root,new byte[0],Experiment.DROP_DATA);
        System.out.println("ALL SCENARIOS PASSED");
    }
    static void verify(Path root,byte[] bytes,Experiment scenario)throws Exception {
        Path input=root.resolve(scenario.name()+"-"+bytes.length+".bin");Files.write(input,bytes);
        List<String> events=new CopyOnWriteArrayList<>();
        try(LabSession session=new LabSession(events::add)) {
            // Presets must override custom conditions, even if those would make transfer impossible.
            LabSession.Result result=session.transfer(input,root,1,1000,2000,scenario);
            if(!Arrays.equals(bytes,Files.readAllBytes(result.file())))throw new AssertionError("File mismatch");
            List<ProtocolEvent> trace=events.stream().map(ProtocolEvent::parse).filter(Objects::nonNull).toList();
            List<ProtocolEvent> drops=trace.stream().filter(e->e.action().equals("DROP")).toList();
            RunStatistics.Snapshot counts=result.statistics();
            if(counts.attempts()!=trace.stream().filter(e->e.action().equals("SEND")).count())throw new AssertionError("Incomplete final attempt count");
            if(counts.duplicates()!=trace.stream().filter(e->e.action().equals("DUPLICATE")).count())throw new AssertionError("Incomplete final duplicate count");
            if(counts.dataDrops()+counts.ackDrops()!=drops.size())throw new AssertionError("Incorrect final drop count");
            if(scenario==Experiment.DROP_ACK&&(counts.retransmissions()<1||counts.duplicates()<1||counts.ackDrops()!=1))throw new AssertionError("Lost ACK summary must show recovery");
            int expected=scenario==Experiment.NORMAL?0:1;
            if(drops.size()!=expected)throw new AssertionError("Expected "+expected+" drops, got "+drops.size());
            if(expected==1) {
                String kind=scenario==Experiment.DROP_ACK?"ACK":bytes.length==0?"EOF":"DATA";
                if(!drops.getFirst().kind().equals(kind)||drops.getFirst().sequence()!=0)throw new AssertionError("Wrong packet dropped");
                if(trace.stream().noneMatch(e->e.action().equals("TIMEOUT")&&e.sequence()==0))throw new AssertionError("Missing timeout");
                if(scenario==Experiment.DROP_ACK&&trace.stream().noneMatch(e->e.action().equals("DUPLICATE")&&e.sequence()==0))throw new AssertionError("Missing duplicate handling");
            }
            System.out.println("PASS "+scenario.name()+" ("+bytes.length+" bytes)");
        }
    }
}