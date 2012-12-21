package org.drown.FourSixFourXlat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;

public class InstallBinary implements Runnable {
    public static final String ACTION_INSTALL_BINARY = "org.drown.464xlat.InstallBinary";
	public final static String EXTRA_MESSAGE = "message";
	public final static String DATA_DIR = "/data/data/org.drown.FourSixFourXlat/files/";
	public final static String BIN_DIR = DATA_DIR + "bin/";
	private File data_dir;
    private File bindir;
    private Context context;

	private void sendInstallBinaryIntent(String message) {
		Intent intent = new Intent(ACTION_INSTALL_BINARY);
		intent.putExtra(EXTRA_MESSAGE, message);
		LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
	}
	
	public InstallBinary(Context context) {
		this.context = context;
        data_dir = new File(DATA_DIR);
        bindir = new File(BIN_DIR);
	}
	
	public void go() {
        Thread installThread = new Thread(this);
        installThread.setName("Install Thread");
        installThread.start();
	}
	
	@Override
	public void run() {
		if(!data_dir.exists()) {
			if(!data_dir.mkdir()) {
				sendInstallBinaryIntent("mkdir "+data_dir.toString()+" failed");
			}
		}
        if(!bindir.exists()) {
            if(!bindir.mkdir()) {
            	sendInstallBinaryIntent("mkdir "+bindir.toString()+" failed");
            }
        }

        install_radvd();
        install_clatd();
        install_clatd_conf();
        sendInstallBinaryIntent("finished");
	}
	
	private void install_clatd() {
		File clatd_path = new File(bindir, "clatd");
		install_file(clatd_path, R.raw.clatd, "755", "clatd");
	}
	
	private void install_radvd() {
        File radvd_path = new File(bindir, "radvd");
        install_file(radvd_path, R.raw.radvd, "755", "radvd");
	}
	
	private void install_clatd_conf() {
		File clatd_conf_path = new File(data_dir, "clatd.conf");
		install_file(clatd_conf_path, R.raw.clatd_conf, "644", "clatd.conf");
	}
	
	private void install_file(File path, int id, String mode, String filename) {
        if(path.exists()) {
        	return;
        }
        
        try {
            InputStream resource_data = context.getResources().openRawResource(id);
            FileOutputStream file_out = new FileOutputStream(path.getPath(), false);
            byte[] buffer = new byte[4096];
            int num;

            while((num = resource_data.read(buffer)) > 0) {
                file_out.write(buffer,0,num);
            }
            file_out.close();
            resource_data.close();
        } catch(IOException e) {
        	sendInstallBinaryIntent("binary install failed/"+filename+": "+e.toString());
            return;
        }
        
        try {
        	chmod(mode, path.getPath());
        } catch(Exception e) {
        	sendInstallBinaryIntent("binary install failed/"+filename+"+chmod: "+e.toString());
            return;
        }
	}
	
	public static void chmod(String mode, String path) throws Exception {
        File chmod_bin = new File("/system/bin/chmod");
        if(!chmod_bin.exists()) {
            chmod_bin = new File("/system/xbin/chmod");
            if(!chmod_bin.exists()) {
            	throw new FileNotFoundException("Unable to locate chmod");
            }
        }
        Process process = Runtime.getRuntime().exec(chmod_bin.getPath()+" "+mode+" "+path);
        if(process.waitFor() > 0) {
        	throw new IOException("chmod exit status != 0");
        }
	}
}
