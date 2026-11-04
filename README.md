# PacketLab

A Java desktop lab for exploring reliable file transfers over an unreliable UDP channel.
Choose a file, configure packet loss and delay, and watch stop-and-wait acknowledgements
and retransmissions. A run succeeds only when the original and received SHA-256 hashes match.

## Run

Requires JDK 21 or newer. No third-party Java dependencies.

On Windows, double-click **PacketLab.cmd** in this folder. It compiles the project and opens the app.
You can also run `./run.ps1` in PowerShell. Select **Use demo file**, then **Start transfer**.
Use **Cancel** to stop an experiment. Verified output is saved in a unique directory under `runs/`.

On other platforms:

```sh
mkdir -p build runs
javac -encoding UTF-8 -d build *.java
java -cp build PacketLab
```

Run the integration checks with `./run.ps1 -Test`, or `java -cp build LabSmokeTest`
after compiling. They exercise empty and binary files, loss and delay, cancellation,
retry exhaustion, and partial-file cleanup using real local UDP sockets.

## Demonstration presets

Choose a scenario in **Network conditions** before starting:

- **Successful delivery:** no random loss; observe the normal data/ACK exchange.
- **Lose a data packet:** drop the first data packet once, then deliver retries normally.
- **Lose an ACK:** drop the first ACK once; the receiver re-acknowledges the duplicate data.
- **Random loss (custom):** use the editable loss and delay settings for free experiments.

Each preset shows a short explanation in the status line and uses 0% random loss and
10-80 ms channel delay. Preset conditions are enforced by the session and relay as well
as shown in the GUI. For an empty file, the first data-side packet is its EOF marker.
Custom settings are restored when returning to random mode. Drop/delay events in the
visualization still come from the real relay, including each deliberate drop.

After compiling, run `java -cp build ScenarioTest` to verify all three scenarios and
the empty-file case using actual UDP transfers and matching output bytes.

## Run summary

The **Run summary** tab shows the latest run's duration, sender transmission attempts,
retransmissions, receiver duplicates, and separate data/EOF and ACK drops. Counts use
all observed protocol events rather than the capped replay history; data includes
filename metadata and EOF, while session-close messages are excluded. A timeout is
counted separately because the final failed attempt can time out without another retry.

Verified results include the received path, SHA-256 hash, and verified byte count.
Failed and cancelled runs show captured counts and no verified output. Starting a new
run clears the previous result. Duration covers setup, transfer, verification and cleanup;
playback speed does not affect it. Viewing the summary does not interrupt packet replay.
These are measured facts, not inferred explanations of why a timeout happened.

## How it works

### Live diagram and teaching replay

The **Packet flow** tab plays real events emitted by the endpoints and channel at a
readable pace. Blue marks data in transit, green marks ACKs, amber marks a packet
waiting in the channel, red marks a drop or timeout, and purple marks a duplicate.
The last data packet and ACK stay visible on their own lanes; inactive markers are
dimmed for context. Labels explain each state so color is not the only signal.

Use **Pause view**, **Next step**, **Previous**, **First step**, or select an
event in the history to inspect a run. **Latest event** jumps to the newest captured
event. Playback speed, pause/resume, and the real transfer operate independently;
changing speed or resuming preserves the animation position. A transfer can finish
while its visual recording is still playing, and both statuses are shown explicitly.

These are slowed observations, not a latency measurement. Events carry source timestamps
from the local clock with causal ordering stamps propagated through relay packets.
Playback sorts by these stamps and briefly buffers live input. The UCP wrapper field
previously used for its unused sequence number now stores a 64-bit ordering stamp;
run matching PacketLab endpoint/relay versions together. RUDP sequence numbers stay
in the data payload.
Send/forward events are recorded before transmission. Paused steps show the start of
a movement; Play animates it. The first
10,000 events are retained and truncation is labeled. The **Technical log** tab
retains text output. DATA includes the legacy filename metadata; EOF ends a stream,
not necessarily the entire experiment. Run `java -Djava.awt.headless=true -cp build
TracePanelTest` after compilation for the playback regression checks.

The GUI starts an unreliable relay and two transfer endpoints in separate JVMs.
Endpoints use the original UCP packet wrapper and stop-and-wait RUDP implementation.
Each data packet carries a sequence number; the sender advances after receiving the
matching acknowledgement. Lost data or acknowledgements cause retransmission.
The receiver re-acknowledges duplicates without writing duplicate data.

The loss percentage applies independently to packets in both directions. Delay can
cause duplicates and reordering when it exceeds the 500 ms acknowledgement timeout.
The UI reports sender transmissions (including metadata and EOF), retries, and relay
drops (including control packets). Throughput includes startup, completion, and verification.

## Project origins and attribution

PacketLab extends the collaborative AUB CMPS 242 ReliableFileTransfer course project:
[original repository](https://github.com/MarcAbouSerhal/ReliableFileTransfer).
The original ten commits through `50dd41d` are preserved with their authors and dates.
Recorded contributors include Marc Abou Serhal, AliHaddam, and AliZreik05.
Git history records commit attribution and does not necessarily capture every collaborator's work.

The independent extension adds the desktop experiment interface, process lifecycle and
cancellation, bounded waits/retries, streaming reception, local output-path control,
SHA-256 verification, and integration checks. These additions are separate from the course baseline.

## Current scope

- Localhost, one file per experiment; this is a teaching lab, not a production transfer service.
- Stop-and-wait only. Sliding-window comparisons and adaptive timeouts are future work.
- Forty attempts per packet, 15-second initial handshake timeout, 30-second receiver idle
  timeout, and five-minute experiment limit. High loss or delay can cause a clear failure.
- The original handshake is bounded but not retransmitted. The finite EOF grace period can
  fail under prolonged acknowledgement loss; the GUI never labels failed runs as verified.
- The file hash is checked locally after transfer; this is not authentication or encryption.
- The separate course HTTP/TCP server is not integrated into this UDP lab.
- Legacy `RUDPSource`, `RUDPDestination`, and `UCPClient` remain for reference; use the GUI
  for the new managed lifecycle, streaming output, and verification.
