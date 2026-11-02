# SafeNet Android — Network Engine & Packet Router Specification

## 1. VpnService Architecture

Android's `VpnService` provides a TUN (network TUNnel) virtual network interface backed by a Linux file descriptor (`ParcelFileDescriptor`). 

```
                                  +-----------------------------+
                                  |    Android Applications     |
                                  +-----------------------------+
                                                 |
                                                 v
+-----------------------------------------------------------------------------------------------+
|                                      Linux Kernel TUN Device                                  |
|   Virtual IPv4: 10.254.1.2/32 | Virtual IPv6: fd00::2/128 | Virtual DNS: 10.254.1.1, fd00::1  |
+-----------------------------------------------------------------------------------------------+
                                                 |
                                                 v
                                  +-----------------------------+
                                  |    SafeNet Packet Loop      |
                                  |  (Direct Memory ByteBuffer) |
                                  +-----------------------------+
                                         /               \
                           (Port 53 UDP)                   (Other IP Packets)
                                       v                                   v
                       +-------------------------------+   +-------------------------------+
                       |   DNS Filtering Engine        |   | Transparent Forwarding/Relay  |
                       |  - Parse DNS Query            |   | - Protected Socket Forwarding |
                       |  - Check Trie Rules           |   | - Standard OS Gateway Routing |
                       +-------------------------------+   +-------------------------------+
                                  /         \
                      (BLOCKED)               (ALLOWED)
                         v                        v
            +------------------------+   +-----------------------------+
            | Synthesize Response    |   | Query Upstream DNS          |
            | (0.0.0.0 / NXDOMAIN)   |   | via Protected Socket        |
            +------------------------+   +-----------------------------+
                         \                        /
                          v                      v
                       +-------------------------------+
                       | Write Raw Response to TUN FD  |
                       +-------------------------------+
```

---

## 2. TUN Configuration & Routing Parameters

The TUN interface is configured programmatically via `VpnService.Builder`:

```kotlin
val builder = Builder()
    .setSession("SafeNet-SecureTunnel")
    .setMtu(1500)
    .addAddress("10.254.1.2", 32)
    .addRoute("10.254.1.1", 32)
    .addDnsServer("10.254.1.1")
    .addAddress("fd00::2", 128)
    .addRoute("fd00::1", 128)
    .addDnsServer("fd00::1")
```

### Route Design & Non-DNS Traffic Handling
To ensure non-DNS network traffic (HTTPS, WebSockets, VoIP, Gaming) is never silently dropped or degraded:
1. **Targeted DNS Route Mode (Battery & Performance Optimized):**
   - Routes virtual DNS servers (`10.254.1.1`, `fd00::1`) and well-known public DNS resolvers (`8.8.8.8`, `8.8.4.4`, `1.1.1.1`, `1.0.0.1`, `9.9.9.9`, `208.67.222.222`) through TUN.
   - Non-DNS traffic flows directly through physical Wi-Fi/cellular radio interfaces without CPU overhead.
   - Combined with Device Owner **Private DNS Disabling** (`Settings.Global.PRIVATE_DNS_MODE = "off"`), all system DNS queries are forced into SafeNet's TUN interface.
2. **Lockdown & Fail-Closed Guarantee:**
   - Under `setAlwaysOnVpnPackage(admin, packageName, /* lockdownEnabled */ true)`, Android's `netd` firewall forbids any network egress if SafeNet's VPN is terminated.
   - Leak-proof: Even if an application attempts to bypass system DNS using raw sockets, well-known DNS IPs are routed to SafeNet TUN and blocked/filtered.

---

## 3. Raw Packet Parsing & Checksum Calculation

### IPv4 & UDP Header Parsing
For every packet read from `ParcelFileDescriptor`:
1. Read first byte: Version = `(byte >> 4) & 0x0F`. Must equal `4` (IPv4) or `6` (IPv6).
2. For IPv4:
   - Header Length (IHL) = `(byte & 0x0F) * 4`.
   - Protocol byte at offset 9: `0x11` (UDP) or `0x06` (TCP).
   - Source IP at offset 12..15, Destination IP at offset 16..19.
3. For UDP:
   - Source Port = 2 bytes at `ihl`.
   - Destination Port = 2 bytes at `ihl + 2`.
   - UDP Length = 2 bytes at `ihl + 4`.
   - Payload begins at `ihl + 8`.

### Synthetic DNS Response Construction
When a domain query is blocked:
1. Parse DNS Transaction ID (bytes 0..1 of payload) and Query Flags.
2. Create DNS Header:
   - QR bit = 1 (Response).
   - Opcode = 0 (Standard Query).
   - AA bit = 1 (Authoritative).
   - RA bit = 1 (Recursion Available).
   - RCODE = 0 (NoError with `0.0.0.0` sinkhole) or 3 (NXDOMAIN).
   - QDCOUNT = 1, ANCOUNT = 1 (for sinkhole A record) or 0 (for NXDOMAIN).
3. Swap IP Header Source & Destination.
4. Swap UDP Header Source & Destination Port.
5. Compute UDP Checksum using IPv4 Pseudo-Header (`SrcIP + DstIP + Zero/Protocol + UDPLen + UDPHeader + Payload`).
6. Write back directly to `FileOutputStream(vpnInterface.fileDescriptor)`.

---

## 4. Upstream DNS Resolution & Protected Sockets

For permitted domains:
1. SafeNet creates a `DatagramSocket` or TLS/HTTPS connection.
2. Calls `vpnService.protect(socket)`.
   *Critical Android Security Requirement:* Calling `protect()` binds the socket directly to the physical underlying network interface, bypassing the VPN routing table and preventing infinite routing recursion loops.
3. Transmits DNS query to configured upstream resolvers (Cloudflare 1.1.1.3 Family, Quad9 9.9.9.9, CleanBrowsing, or Custom).
4. Receives response, rewrites destination IP/port to original client TUN address/port, and injects back into TUN.

---

## 5. DNS Bypass Mitigation Matrix

| Potential Bypass Path | SafeNet Defense Layer |
|---|---|
| **Android Private DNS (DoT)** | Device Owner policy sets `Settings.Global.PRIVATE_DNS_MODE = "off"`. |
| **Hardcoded Public DNS (8.8.8.8, 1.1.1.1)** | TUN routing table traps well-known DNS IPs directly into the filter engine. |
| **Browser DoH (DNS-over-HTTPS)** | Local DNS trie blocks DoH bootstrap domains (`cloudflare-dns.com`, `dns.google`, etc.). |
| **VPN Disabling / Force-Stop** | Device Owner sets `DISALLOW_CONFIG_VPN` + `setUninstallBlocked` + Always-on lockdown. |
| **Safe Mode Reboot** | Device Owner sets `DISALLOW_SAFE_BOOT`. |
