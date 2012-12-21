package org.drown.FourSixFourXlat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

public class RunAsRoot extends IntentService {
	public static final String EXTRA_SCRIPT_CONTENTS = "ScriptContents";
	public static final String EXTRA_STAGE_NAME = "StageName";
	
	public RunAsRoot() {
		super("RunAsRoot");
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		String ScriptContents = intent.getExtras().getString(EXTRA_SCRIPT_CONTENTS);
		String StageName = intent.getExtras().getString(EXTRA_STAGE_NAME);
		try {
			run_script(ScriptContents);
		} catch (Exception e) {
			Log.e("RunAsRoot", StageName+": "+e.toString());
		}
	}
	
	private void run_script(String ScriptContents) throws Exception {
		File RootShellScript = new File(InstallBinary.BIN_DIR, "tether.sh");
		FileOutputStream RootShellScript_out = new FileOutputStream(RootShellScript.getPath(), false);
		RootShellScript_out.write(ScriptContents.getBytes());
		RootShellScript_out.close();
		
		InstallBinary.chmod("755",RootShellScript.getPath());
		
		Process process = Runtime.getRuntime().exec("/system/xbin/su -c "+RootShellScript.getPath());
        if(process.waitFor() > 0) {
        	throw new IOException("shell exit status != 0");
        }
	}
}
