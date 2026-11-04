# PacketLab

A Java desktop lab for exploring reliable file transfers over an unreliable UDP channel.
Choose a file, configure packet loss and delay, and watch stop-and-wait acknowledgements
and retransmissions. A run succeeds only when the original and received SHA-256 hashes match.

## Run

Requires JDK 21 or newer. No third-party Java dependencies.

On Windows, run `./run.ps1` in PowerShell. Select **Use demo file**, then **Start transfer**.
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

## How it works

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
