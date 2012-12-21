package org.drown.FourSixFourXlat;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Scanner;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class Tethering {
	private static String MobileIPv6Address = null;
	private static String IPv6TetherInterface = null;
	private static boolean ClatdWasRunning = false;
	private static boolean hasTetheringState = false;
	private static File TetheringState = new File(InstallBinary.DATA_DIR, "tether.state");
	
	private static void write_radvd_conf(File radvd_conf_file, String interfaceName) throws Exception {
		StringBuffer radvd_conf = new StringBuffer();
		radvd_conf.append("interface "+interfaceName+"\n");
	    radvd_conf.append("{\n");
	    radvd_conf.append("AdvSendAdvert on;\n");
	    radvd_conf.append("MinRtrAdvInterval 20;\n");
	    radvd_conf.append("AdvDefaultLifetime 60;\n");
	    radvd_conf.append("MaxRtrAdvInterval 100;\n");
	    radvd_conf.append("prefix "+MobileIPv6Address+"/64\n");
	    radvd_conf.append("{\n");
	    radvd_conf.append("AdvOnLink on;\n");
	    radvd_conf.append("AdvAutonomous on;\n");
	    radvd_conf.append("AdvRouterAddr off;\n");
	    radvd_conf.append("AdvValidLifetime 120;\n");
	    radvd_conf.append("AdvPreferredLifetime 60;\n");
	    radvd_conf.append("};\n");
	    radvd_conf.append("};\n");
	    
		FileOutputStream radvd_conf_fs = new FileOutputStream(radvd_conf_file, false);
		radvd_conf_fs.write(radvd_conf.toString().getBytes());
		radvd_conf_fs.close();
	}
	
	private static void WriteTetheringState(String interfaceName, String v6address, boolean ClatRunning) {
		try {
			FileOutputStream TetheringState_out = new FileOutputStream(TetheringState.getPath(), false);
			String state = interfaceName+"\n"+v6address+"\n"+(ClatRunning ? "1" : "0")+"\n";
			TetheringState_out.write(state.getBytes());
			TetheringState_out.close();
		} catch (Exception e) {
			Log.e("Tethering/writestate", e.toString());
		}
	}
	
	private static void ReadTetheringState() {
		if(!TetheringState.exists()) {
			return;
		}
		try {
			Scanner TetheringState_in = new Scanner(TetheringState);
			if(TetheringState_in.hasNextLine()) {
				IPv6TetherInterface = TetheringState_in.nextLine();
			}
			if(TetheringState_in.hasNextLine()) {
				MobileIPv6Address = TetheringState_in.nextLine();
			}
			if(TetheringState_in.hasNextLine()) {
				String ClatdWasRunning_str = TetheringState_in.nextLine();
				ClatdWasRunning = ClatdWasRunning_str.equals("1");
			}
			hasTetheringState = true;
			TetheringState_in.close();
		} catch (Exception e) {
			Log.e("Tethering/readstate", e.toString());
		}
	}
	
	public static void InitFromDisk() {
		if(!hasTetheringState) {
			ReadTetheringState();
		}
	}
	
	public static String setupIPv6(Context context, String interfaceName, String v6address, int MobileIPv6SubnetLength, String MobileInterfaceName) {
		File radvd_conf_file = new File(InstallBinary.DATA_DIR, "radvd.conf");
		File radvd_pid_file = new File(InstallBinary.DATA_DIR, "radvd.pid");
		
		if(v6address == null) {
			return "no v6address";
		}
		
		MobileIPv6Address = v6address;
		IPv6TetherInterface = interfaceName;
		ClatdWasRunning = Clat.ClatRunning();
		hasTetheringState = true;
		WriteTetheringState(interfaceName, v6address, ClatdWasRunning);
		
		StringBuffer Script = new StringBuffer();
		Script.append("#!/system/bin/sh\n");
		Script.append("cat /proc/sys/net/ipv6/conf/"+interfaceName+"/disable_ipv6 >"+InstallBinary.DATA_DIR+"disable_ipv6\n");
		Script.append("echo 0 >/proc/sys/net/ipv6/conf/"+interfaceName+"/disable_ipv6\n");
		Script.append("ip addr add "+MobileIPv6Address+"/64 dev "+interfaceName+"\n");
		if(MobileIPv6SubnetLength == 64) {
			// there's a /64 on the mobile interface, make a higher priority interface route on the tethered interface
			Script.append("ip -6 route add "+MobileIPv6Address+"/64 dev "+interfaceName+" metric 1\n");
		}
		Script.append(InstallBinary.BIN_DIR+"radvd -C "+radvd_conf_file.getPath()+" -p "+InstallBinary.DATA_DIR+"radvd.pid\n");
		Script.append("echo 1 >/proc/sys/net/ipv6/conf/all/forwarding\n");
		Script.append("ip -6 route add default dev "+MobileInterfaceName+"\n");
		
		if(ClatdWasRunning) {
			Script.append("iptables -t nat -A POSTROUTING -o clat4 -j MASQUERADE\n");
			Script.append("iptables -I natctrl_FORWARD -i clat4 -o "+interfaceName+" -j ACCEPT\n");
			Script.append("iptables -I natctrl_FORWARD -o clat4 -i "+interfaceName+" -j ACCEPT\n");
		}
		
		if(radvd_pid_file.exists()) {
			radvd_pid_file.delete();
		}

		try {
			write_radvd_conf(radvd_conf_file, interfaceName);
		} catch (Exception e1) {
			return "setup radvd.conf failed: "+e1.toString();
		}
		
		Intent startTethering = new Intent(context, RunAsRoot.class);
		startTethering.putExtra(RunAsRoot.EXTRA_STAGE_NAME, "start tethering");
		startTethering.putExtra(RunAsRoot.EXTRA_SCRIPT_CONTENTS, Script.toString());
		context.startService(startTethering);
		
		return null;
	}
	
	public static String teardownIPv6(Context context) {
		File radvd_pid_file = new File(InstallBinary.DATA_DIR, "radvd.pid");
		if(!radvd_pid_file.exists()) {
			return "radvd not running";
		}

		StringBuffer Script = new StringBuffer();
		Script.append("#!/system/bin/sh\n");
		Script.append("kill `cat "+InstallBinary.DATA_DIR+"radvd.pid`\n");
		if(!Clat.ClatRunning()) {
			Script.append("echo 0 >/proc/sys/net/ipv6/conf/all/forwarding\n");	
		}
		Script.append("cat "+InstallBinary.DATA_DIR+"disable_ipv6 >/proc/sys/net/ipv6/conf/"+IPv6TetherInterface+"/disable_ipv6\n");
		if(ClatdWasRunning) {
			Script.append("iptables -t nat -D POSTROUTING -o clat4 -j MASQUERADE\n");
			Script.append("iptables -D natctrl_FORWARD -i clat4 -o "+IPv6TetherInterface+" -j ACCEPT\n");
			Script.append("iptables -D natctrl_FORWARD -o clat4 -i "+IPv6TetherInterface+" -j ACCEPT\n");
		}

		Intent stopTethering = new Intent(context, RunAsRoot.class);
		stopTethering.putExtra(RunAsRoot.EXTRA_STAGE_NAME, "stop tethering");
		stopTethering.putExtra(RunAsRoot.EXTRA_SCRIPT_CONTENTS, Script.toString());
		context.startService(stopTethering);

		IPv6TetherInterface = null;
		ClatdWasRunning = false;
		MobileIPv6Address = null;
		
		return null;
	}

	public static String InterfaceName() {
		InitFromDisk();
		return (IPv6TetherInterface == null) ? "" : IPv6TetherInterface;
	}

	public static boolean TetheringOnInterface(String interfaceName) {
		InitFromDisk();
		return (IPv6TetherInterface != null) && IPv6TetherInterface.equals(interfaceName);
	}

	public static boolean NoTethering() {
		InitFromDisk();
		return IPv6TetherInterface == null;
	}

	public static void teardownIfUp(Context context) {
		if(!hasTetheringState) {
			ReadTetheringState();
		}
		TetheringState.delete();
		if(IPv6TetherInterface != null) {
			teardownIPv6(context);
		}
	}
}