package org.drown.FourSixFourXlat;

import java.io.File;

import android.os.Bundle;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.view.Menu;
import android.widget.TextView;

public class MainActivity extends Activity {
	TextView TetherStatus, MobileStatus, LastMessage, BinaryStatus, IPv4Address, IPv6Address, ClatStatus, Stdout, Stderr;
	
	private void UpdateText() {
		TetherStatus.setText(Tethering.InterfaceName());
		MobileStatus.setText(ConnectivityReceiver.getMobileStatus());
		IPv6Address.setText(ConnectivityReceiver.getMobileIPv6Address());
		IPv4Address.setText(ConnectivityReceiver.getMobileIPv4Address());
		ClatStatus.setText(Clat.getClatInterface());
	}
	
	private BroadcastReceiver mConnectionChanges = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if(intent.getAction().equals(ConnectivityReceiver.ACTION_CONNECTIVITY_CHANGE)) {
				String message = intent.getStringExtra("message");
			    LastMessage.setText(message);
			    UpdateText();
			} else if(intent.getAction().equals(InstallBinary.ACTION_INSTALL_BINARY)) {
				String message = intent.getStringExtra("message");
				BinaryStatus.setText(message);
			} else if(intent.getAction().equals(RunAsRoot.ACTION_ROOTSCRIPT_DONE)) {
				String StageName = intent.getStringExtra(RunAsRoot.EXTRA_STAGE_NAME);
				Stdout.setText(RunAsRoot.get_stdout(StageName));
				Stderr.setText(RunAsRoot.get_stderr(StageName));
				LastMessage.setText("Stage Script "+StageName+" completed");
			}
		}
	};
		
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

		InstallBinary install = new InstallBinary(this);
		install.go();
		
		ConnectivityReceiver.rescanNetworkStatus(this);
		
		IntentFilter messageFilter = new IntentFilter();
		messageFilter.addAction(ConnectivityReceiver.ACTION_CONNECTIVITY_CHANGE);
		messageFilter.addAction(InstallBinary.ACTION_INSTALL_BINARY);
		messageFilter.addAction(RunAsRoot.ACTION_ROOTSCRIPT_DONE);
		LocalBroadcastManager.getInstance(this).registerReceiver(mConnectionChanges, messageFilter);
		
		TetherStatus = (TextView) findViewById(R.id.TetherStatus);
		MobileStatus = (TextView) findViewById(R.id.MobileStatus);
		LastMessage = (TextView) findViewById(R.id.LastMessage);
		BinaryStatus = (TextView) findViewById(R.id.BinaryStatus);
		IPv6Address = (TextView) findViewById(R.id.IPv6Address);
		IPv4Address = (TextView) findViewById(R.id.IPv4Address);
		ClatStatus = (TextView) findViewById(R.id.ClatStatus);
		Stdout = (TextView) findViewById(R.id.Stdout);
		Stderr = (TextView) findViewById(R.id.Stderr);
		LastMessage.setText("");
		BinaryStatus.setText("");
		UpdateText();

		File system_xbin_su = new File("/system/xbin/su");
		if(!system_xbin_su.exists()) {
			LastMessage.setText("No /system/xbin/su found");
			return;
		}
		
		File clatd_conf_copied = new File(InstallBinary.DATA_DIR+"clatd_conf_copied");
		if(!clatd_conf_copied.exists()) {
			Intent firstRun = new Intent(this, RunAsRoot.class);
			firstRun.putExtra(RunAsRoot.EXTRA_STAGE_NAME, "Copy_clatd.conf");
			firstRun.putExtra(RunAsRoot.EXTRA_SCRIPT_CONTENTS, 
					"#!/system/bin/sh\n" + 
					"cp "+InstallBinary.DATA_DIR+"clatd.conf /data/misc/clatd.conf\n" +
					"chmod 644 /data/misc/clatd.conf\n" +
					"touch "+InstallBinary.DATA_DIR+"clatd_conf_copied\n"
					);
			startService(firstRun);
			LastMessage.setText("copied clatd.conf");
		}
    }
    
    @Override
	protected void onDestroy() {
		LocalBroadcastManager.getInstance(this).unregisterReceiver(mConnectionChanges);
		super.onDestroy();
	}

	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }
}