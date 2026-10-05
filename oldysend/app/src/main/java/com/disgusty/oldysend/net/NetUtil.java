package com.disgusty.oldysend.net;

import com.disgusty.oldysend.util.Log;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/** Local network addresses. Only Java 1.4 NetworkInterface methods are used (Android 1.0). */
public final class NetUtil {
    public static final class LocalAddress {
        public final String interfaceName;
        public final String ip;

        LocalAddress(String interfaceName, String ip) {
            this.interfaceName = interfaceName;
            this.ip = ip;
        }

        /** "192.168.1." — the /24 prefix scanned by HTTP discovery (netmasks need API 9). */
        public String subnetPrefix() {
            return ip.substring(0, ip.lastIndexOf('.') + 1);
        }
    }

    private NetUtil() {
    }

    /** All non-loopback IPv4 addresses, filtered by the interface white-/blacklist. */
    public static List<LocalAddress> localAddresses(List<String> whitelist, List<String> blacklist) {
        List<LocalAddress> out = new ArrayList<LocalAddress>();
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            if (ifaces == null) return out;
            while (ifaces.hasMoreElements()) {
                NetworkInterface ni = ifaces.nextElement();
                String name = ni.getName();
                if (!whitelist.isEmpty() && !whitelist.contains(name)) continue;
                if (blacklist.contains(name)) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (!(a instanceof Inet4Address) || a.isLoopbackAddress()) continue;
                    String ip = a.getHostAddress();
                    if (ip.startsWith("169.254.")) continue;
                    out.add(new LocalAddress(name, ip));
                }
            }
        } catch (Throwable e) {
            Log.w("Could not enumerate network interfaces", e);
        }
        // Wi-Fi and Ethernet first, mobile data (rmnet/ccmni) last.
        List<LocalAddress> sorted = new ArrayList<LocalAddress>();
        for (LocalAddress a : out) if (isLan(a.interfaceName)) sorted.add(a);
        for (LocalAddress a : out) if (!isLan(a.interfaceName)) sorted.add(a);
        return sorted;
    }

    /** Interface names, for the network interfaces page. */
    public static List<String> interfaceNames() {
        List<String> out = new ArrayList<String>();
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            if (ifaces == null) return out;
            while (ifaces.hasMoreElements()) {
                NetworkInterface ni = ifaces.nextElement();
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                boolean has = false;
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) has = true;
                }
                if (has) out.add(ni.getName());
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static boolean isLan(String name) {
        return name.startsWith("wlan") || name.startsWith("eth") || name.startsWith("ap") || name.startsWith("swlan")
                || name.startsWith("tiwlan") || name.startsWith("en") || name.startsWith("wl") || name.startsWith("rndis")
                || name.startsWith("usb");
    }

    public static boolean isValidIpv4(String s) {
        String[] parts = s.split("\\.");
        if (parts.length != 4) return false;
        for (String p : parts) {
            try {
                int v = Integer.parseInt(p);
                if (v < 0 || v > 255 || p.length() > 3) return false;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }
}
