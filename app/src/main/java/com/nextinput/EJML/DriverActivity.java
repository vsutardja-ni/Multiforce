package com.nextinput.EJML;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.ToggleButton;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

//import com.amazonaws.auth.CognitoCachingCredentialsProvider;
//import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
//import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
//import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
//import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
//import com.amazonaws.regions.Regions;
//import com.amazonaws.services.s3.AmazonS3;
//import com.amazonaws.services.s3.AmazonS3Client;

public class DriverActivity extends AppCompatActivity {
    //private static String driverPath = "/sys/bus/i2c/devices/6-0034/";
    private static String driverPath = "/sys/devices/f9968000.i2c/i2c-12/12-0034/";
    private static String vDevicePath = "/sys/devices/virtual/ni_device/ni_device_driver/";
    private final static String[] FP1000_MODE = {"0","1","2","3","31","40","255"};
    private final static int REQUEST_RESULT = 100;
    private AlertDialog alertDialog;
    private SharedPreferences sharedPreferences;
    private ArrayList<String> permissionsToRequest;
    private ArrayList<String> permissionsRejected;
    private Button showFirmware;
    private Button powerOff;
    private Button powerOn;
    private ToggleButton dataCapOffOn;
    private Button dataCapShow;
    private ToggleButton pEventOnOff;
    private Button updateDriver;
    private Spinner cmbMode;
    private ToggleButton cmdKernelLog;
    private Button cmdShowLog;
    private Button factoryFirmware;
    private Button factoryCalibration;
    private Button factorySettings;
    private boolean driverDownloaded = false;
    private boolean hashDownloaded = false;
    private boolean deviceDownloaded = false;
    private boolean devicehashDownloaded = false;
    private String hashString = "";
    private String devicehashString = "";
    private ProgressDialog progress;
    private boolean logDebugData = false;
    private boolean logDebugDataRunning = false;
    private String logDebugFileName = "";
    private static int[] app_data_array;
    private static boolean  pollAppData = false;
    private static boolean pollAppDataRunning = false;
    private static Handler debugDataHandler = new Handler();
    private Runnable pipeDebugData = new Runnable() {
        @Override
        public void run() {
            if(logDebugData && logDebugFileName.length() > 0 && !logDebugDataRunning) {
                logDebugDataRunning = true;
                pipeFile("debug_data", new File(logDebugFileName), false);
                logDebugDataRunning = false;
                debugDataHandler.removeCallbacks(pipeDebugData);
                debugDataHandler.postDelayed(pipeDebugData, 100);
            }
        }
    };
    private static Runnable appDataThread = new Runnable() {
        @Override
        public void run() {
            if(pollAppData && !pollAppDataRunning) {
                pollAppDataRunning = true;
                app_data_array = getAppData(true);
                pollAppDataRunning = false;
                if(pollAppData)
                    debugDataHandler.postDelayed(appDataThread, 20);
            }
        }
    };
    public static int[] getAppData() {
        return app_data_array;
    }
    public static void setPollAppData(boolean pollAppData) {
        DriverActivity.pollAppData = pollAppData;
        if(pollAppData) {
            writeFile("device_mode", "255");
            debugDataHandler.postDelayed(DriverActivity.appDataThread, 20);
        }
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver);
        if(hasNoPermissions() && false) {
            ArrayList<String> permissions = new ArrayList<>();
            permissions.add(WRITE_EXTERNAL_STORAGE);
            permissions.add(READ_EXTERNAL_STORAGE);
            requestPermissions(permissions.toArray(new String[permissions.size()]), REQUEST_RESULT);
        }
        showFirmware = (Button) findViewById(R.id.showFirmware);
        powerOff = (Button) findViewById(R.id.powerOff);
        powerOn = (Button) findViewById(R.id.powerOn);
        updateDriver = (Button) findViewById(R.id.updateDriver);
        dataCapOffOn = (ToggleButton) findViewById(R.id.dataCapOffOn);
        dataCapShow = (Button) findViewById(R.id.dataCapShow);
        pEventOnOff = (ToggleButton) findViewById(R.id.pEventOffOn);
        cmbMode = (Spinner) findViewById(R.id.cmbMode);
        cmdKernelLog = (ToggleButton) findViewById(R.id.cmdKernelLog);
        cmdShowLog = (Button) findViewById(R.id.cmdShowLog);
        factoryFirmware = (Button) findViewById(R.id.factoryFirmware);
        factoryCalibration = (Button) findViewById(R.id.factoryCalibration);
        factorySettings = (Button) findViewById(R.id.factorySettings);
        showFirmware.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDialog("Current Firmware", readFile("firmware"));
            }
        });
        factoryFirmware.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                writeFile("firmware", "1");
                showDialog("Loading Factory Firmware", "Wait 5 Seconds");
            }
        });
        factoryCalibration.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                writeFile("device_mode", "31");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {

                    e.printStackTrace();
                }
                writeFile("device_mode", "255");
                showDialog("Factory Calibration Complete", "Press Ok to Exit");
            }
        });
        factorySettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                writeFile("config_data", "24 0");           // Number of Samples and shift
                writeFile("config_data", "25 160");         // INT
                writeFile("config_data", "26 72");          // ADC
                writeFile("config_data", "27 78");          // SORDER
                writeFile("config_data", "28 0");           // SGMUX
                writeFile("config_data", "29 27");          // SNMIX
                writeFile("config_data", "30 228");         // SPMUX

                writeFile("calibration_data", "1  120");    // TOUCH_THRESH
                writeFile("calibration_data", "2  100");    // RELEASE_THRESH
                writeFile("calibration_data", "3  60");     // AUTO_CAL_THRESH
                writeFile("calibration_data", "4  120");    // DF_THRESH
                writeFile("calibration_data", "5  10");     // LIFT_OFF_OF
                writeFile("calibration_data", "6  250");    // FALSE_FORCE_OF
                writeFile("calibration_data", "7  2");      // BOUND_MIN
                writeFile("calibration_data", "8  50");     // XY_LPF_ALPHA_MAX
                writeFile("calibration_data", "9  10");     // XY_LPF_ALPHA_MIN
                writeFile("calibration_data", "10 80");     // SENSOR_LPF_ALPHA
                writeFile("calibration_data", "11 10");     // AUTO_CAL_OF

                showDialog("Factory Settings Written", "Press Ok to Exit");
            }
        });

        powerOff.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                writeFile("power_ctrl", "0");
            }
        });
        powerOn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                writeFile("power_ctrl", "1");
            }
        });
        updateDriver.setOnClickListener(driverDownloadClickListener);
        dataCapOffOn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    android.text.format.DateFormat df = new android.text.format.DateFormat();
                    logDebugFileName = DriverActivity.this.getFilesDir().getAbsolutePath() + "/log_"
                            + df.format("yyyy_MM-dd_hh-mm", new Date()) + ".log";
                    logDebugData = true;
                    if (!logDebugDataRunning)
                        debugDataHandler.postDelayed(pipeDebugData, 10);
                } else {
                    logDebugData = false;
                }

            }
        });

        pEventOnOff.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    writeDeviceFile("force_input_events", "1");         // Turn on P events
                } else {
                    writeDeviceFile("force_input_events", "0");         // Turn off P events
                }

            }
        });

        dataCapShow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDialog("Latest Debug Data", readFile(logDebugFileName));
            }
        });
        cmbMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                writeFile("device_mode", FP1000_MODE[position]);
                for (int x = 0; x < FP1000_MODE.length; x++) {
                    if (getAppData(false)[0] == Integer.parseInt(FP1000_MODE[x])) {
                        cmbMode.setSelection(x);
                        break;
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                for (int x = 0; x < FP1000_MODE.length; x++) {
                    if (getAppData(false)[0] == Integer.parseInt(FP1000_MODE[x])) {
                        cmbMode.setSelection(x);
                        break;
                    }
                }
            }
        });
        cmdKernelLog.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    writeFile("debug_data", "31");
                } else {
                    writeFile("debug_data", "0");
                }
            }
        });
        cmdShowLog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Intent launchNewIntent = new Intent(DriverActivity.this, KernelLogActivity.class);
                //startActivityForResult(launchNewIntent, 0);
            }
        });
        try {
            String debug_setting = readFile("/sys/module/ni_fp1000_ts/parameters/debug_mask").trim();
            if(Integer.parseInt(debug_setting) > 0) {
                cmdKernelLog.setChecked(true);
            }
        } catch (Exception e) {

        }
        for(int x = 0;x<FP1000_MODE.length;x++) {
            if(getAppData(false)[0] == Integer.parseInt(FP1000_MODE[x])) {
                cmbMode.setSelection(x);
                break;
            }
        }
    }

    private static int[] getAppData(boolean ni_device) {
        String appData = "";
        //ni_device = false; //JOM added because we no longer have the ni_device driver
        if(ni_device)
            appData = readFile(vDevicePath + "app_data");
        else
            appData = readFile("app_data");
        String[] strArray = appData.split(",");
        int[] intArray = new int[strArray.length];
        for(int i = 0; i < strArray.length; i++) {
            try {
                intArray[i] = Integer.parseInt(strArray[i].trim());
            } catch (NumberFormatException e) {
                intArray[i] = -1;
            }
        }

        return intArray;
    }

    private boolean checkHash (File file, String hash) {
        try {
            InputStream fis = new FileInputStream(file);
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[65536]; //created at start.
            int n = 0;
            while (n != -1) {
                n = fis.read(buffer);
                if (n > 0) {
                    digest.update(buffer, 0, n);
                }
            }
            byte[] digestResult = digest.digest();
            String newhash = toHexString(digestResult).trim();
//            System.out.println(newhash);
//            System.out.println(hash);
            if(newhash.equalsIgnoreCase(hash)) {
                return true;
            }
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }
    //HELP FUNCTIONS
    public static void F(String file, String content) {
        try {
            File filehandler;
            if(file.contains("/")) {
                filehandler = new File(file);
            } else {
                filehandler = new File(vDevicePath + file);
            }
            FileOutputStream fops = new FileOutputStream(filehandler);
            OutputStreamWriter outputWriter = new OutputStreamWriter(fops);
            outputWriter.write(content);
            outputWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void writeFile(String file, String content) {
        try {
            File filehandler;
            if(file.contains("/")) {
                filehandler = new File(file);
            } else {
                filehandler = new File(driverPath + file);
            }
            FileOutputStream fops = new FileOutputStream(filehandler);
            OutputStreamWriter outputWriter = new OutputStreamWriter(fops);
            outputWriter.write(content);
            outputWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void writeDeviceFile(String file, String content) {
        try {
            File filehandler;
            if(file.contains("/")) {
                filehandler = new File(file);
            } else {
                filehandler = new File(vDevicePath + file);
            }
            FileOutputStream fops = new FileOutputStream(filehandler);
            OutputStreamWriter outputWriter = new OutputStreamWriter(fops);
            outputWriter.write(content);
            outputWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void pipeFile(String file, File saveFile, boolean addTimestamp) {
        File filehandler = new File(driverPath + file);
        //Read text from file
        BufferedInputStream bis = null;
        BufferedOutputStream bos = null;
        try {
            bis = new BufferedInputStream(new FileInputStream(filehandler));
            if(!saveFile.exists())
                saveFile.createNewFile();
            bos = new BufferedOutputStream(new FileOutputStream(saveFile, true));
            int data;
            if(addTimestamp)
                bos.write(new String(new SimpleDateFormat("HH:mm:ss.SSS").format(new Date())+"-").getBytes());
            while ((data = bis.read()) != -1) {
                bos.write(data);
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (bis != null)
                    bis.close();
                if (bos != null)
                    bos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return;
    }

    public static String readFile(String file) {
        return readFile(file, null);
    }

    public static String readFile(String file, String filter) {
        File filehandler;
        if(file.contains("/")) {
            filehandler = new File(file);
        } else {
            filehandler = new File(driverPath + file);
        }
        //Read text from file
        StringBuilder text = new StringBuilder();

        try {
            BufferedReader br = new BufferedReader(new FileReader(filehandler));
            String line;
            while ((line = br.readLine()) != null) {
                boolean useline = true;
                if(filter != null) {
                    if(!line.contains(filter))
                        useline = false;
                }
                if(useline) {
                    text.append(line);
                    text.append('\n');
                }
            }
            br.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return text.toString();
    }

    private void showDialog(String title, String message) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(DriverActivity.this);

        // set title
        alertDialogBuilder.setTitle(title);
        // set dialog message
        alertDialogBuilder
                .setMessage(message)
                .setCancelable(false)
                .setNeutralButton("Ok",new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });

        // create alert dialog
        alertDialog = alertDialogBuilder.create();

        // show it
        alertDialog.show();
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if(hasNoPermissions()) {
            showDialog("File Permissions", "You need to allow file writes!");
        }
    }

    private boolean hasNoPermissions() {
        return checkSelfPermission(WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED ||
                checkSelfPermission(READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED;
    }

    public static String toHexString(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();

        for (int i = 0; i < bytes.length; i++) {
            String hex = Integer.toHexString(0xFF & bytes[i]);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }

        return hexString.toString();
    }

    private View.OnClickListener driverDownloadClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            // IDC: REmoved since not needed
//            driverDownloaded = false;
//            hashDownloaded = false;
//            hashString = "";
//            CognitoCachingCredentialsProvider credentialsProvider = new CognitoCachingCredentialsProvider(
//                    getApplicationContext(),
//                    "us-east-1:7bfc6f83-0fe8-40a9-9a44-77390a931032", // Identity Pool ID
//                    Regions.US_EAST_1 // Region
//            );
//
//            final ProgressDialog progress = new ProgressDialog(DriverActivity.this);
//            progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
//            progress.setTitle("NextInput Driver Update");
//            progress.setMessage("Authenticating with Amazon");
//            progress.setIndeterminate(false);
//            progress.setCancelable(true);
//            progress.setMax(100);
//            progress.setCancelable(true);
//            AmazonS3 s3 = new AmazonS3Client(credentialsProvider);
//            final TransferUtility transferUtility = new TransferUtility(s3, getApplicationContext());
//            final File driver = new File(DriverActivity.this.getFilesDir(), "ni_fp1000_ts.ko");
//            final File device = new File(DriverActivity.this.getFilesDir(), "ni_device.ko");
//            driver.delete();
//            device.delete();
//            final File driver_temp = new File(DriverActivity.this.getFilesDir(), "ni_fp1000_ts.ko.temp");
//            final File driver_hash = new File(DriverActivity.this.getFilesDir(), "driver_hash");
//            final File device_temp = new File(DriverActivity.this.getFilesDir(), "ni_device.ko.temp");
//            final File device_hash = new File(DriverActivity.this.getFilesDir(), "ni_device_hash");
//            final TransferObserver to = transferUtility.download("ni-android", "driver/ni_fp1000_ts.ko", driver_temp);
//            final TransferObserver tohash = transferUtility.download("ni-android", "driver/driver_hash", driver_hash);
//            final TransferObserver deviceto = transferUtility.download("ni-android", "driver/ni_device.ko", device_temp);
//            final TransferObserver devicehash = transferUtility.download("ni-android", "driver/ni_device_hash", device_hash);
//            final TransferListener transferListener = new TransferListener() {
//                @Override
//                public void onStateChanged(int id, TransferState state) {
//                    if(id == to.getId()) {
//                        System.out.println("gpld " + state.toString());
//                        if (state == TransferState.CANCELED) {
//                            progress.setProgress(100);
//                            progress.setMessage("Download Cancelled");
//                        }
//                        if (state == TransferState.COMPLETED) {
//                            driverDownloaded = true;
//                            if(hashDownloaded) {
//                                if (checkHash(driver_temp,hashString)) {
//                                    driver_temp.renameTo(driver);
//                                } else {
//                                    driverDownloaded = false;
//                                    progress.setMessage("Download File Hash Mismatch");
//                                }
//                                if(deviceDownloaded && driverDownloaded) {
//                                    progress.setMessage("Download Finished\nReboot Device");
//                                    progress.setProgress(100);
//                                }
//                            } else {
//                                progress.setMessage("Confirming Driver");
//                            }
//                        }
//                        if (state == TransferState.IN_PROGRESS) {
//                            progress.setMessage("Driver Downloading");
//                        }
//                        if (state == TransferState.FAILED) {
//                            progress.setProgress(100);
//                            progress.setTitle("NextInput Driver Download Failed");
//                        }
//                        if (state == TransferState.WAITING_FOR_NETWORK) {
//                            progress.setMessage("Searching for driver.\nAre you online?");
//                        }
//                    } else if(id == tohash.getId()) {
//                        System.out.println("gplhash " + state.toString());
//                        if (state == TransferState.COMPLETED) {
//                            try {
//                                FileInputStream fin = new FileInputStream(driver_hash);
//                                BufferedReader reader = new BufferedReader(new InputStreamReader(fin));
//                                StringBuilder sb = new StringBuilder();
//                                String line = null;
//                                while ((line = reader.readLine()) != null) {
//                                    sb.append(line).append("\n");
//                                }
//                                reader.close();
//                                hashString = sb.toString().trim();
//                                //Make sure you close all streams.
//                                fin.close();
//                                hashDownloaded = true;
//                            } catch (IOException e) {
//                                e.printStackTrace();
//                            }
//                            if (driverDownloaded) {
//                                if (checkHash(driver_temp, hashString)) {
//                                    driver_temp.renameTo(driver);
//                                } else {
//                                    driverDownloaded = false;
//                                    progress.setMessage("Download File Hash Mismatch");
//                                }
//                                if(deviceDownloaded && driverDownloaded) {
//                                    progress.setMessage("Download Finished\nReboot Device");
//                                    progress.setProgress(100);
//                                }
//                            }
//                        }
//                    } else if(id == deviceto.getId()) {
//                        System.out.println("device " + state.toString());
//                        if (state == TransferState.CANCELED) {
//                            progress.setProgress(100);
//                            progress.setMessage("Download Cancelled");
//                        }
//                        if (state == TransferState.COMPLETED) {
//                            deviceDownloaded = true;
//                            if(devicehashDownloaded) {
//                                if (checkHash(device_temp,devicehashString)) {
//                                    device_temp.renameTo(device);
//                                } else {
//                                    deviceDownloaded = false;
//                                    progress.setMessage("Download File Hash Mismatch");
//                                }
//                                if(deviceDownloaded && driverDownloaded) {
//                                    progress.setMessage("Download Finished\nReboot Device");
//                                    progress.setProgress(100);
//                                }
//                            } else {
//                                progress.setMessage("Confirming Driver");
//                            }
//                        }
//                        if (state == TransferState.IN_PROGRESS) {
//                            progress.setMessage("Driver Downloading");
//                        }
//                        if (state == TransferState.FAILED) {
//                            progress.setProgress(100);
//                            progress.setTitle("NextInput Driver Download Failed");
//                        }
//                        if (state == TransferState.WAITING_FOR_NETWORK) {
//                            progress.setMessage("Searching for driver.\nAre you online?");
//                        }
//                    } else if(id == devicehash.getId()) {
//                        System.out.println("devicehash " + state.toString());
//                        if (state == TransferState.COMPLETED) {
//                            try {
//                                FileInputStream fin = new FileInputStream(device_hash);
//                                BufferedReader reader = new BufferedReader(new InputStreamReader(fin));
//                                StringBuilder sb = new StringBuilder();
//                                String line = null;
//                                while ((line = reader.readLine()) != null) {
//                                    sb.append(line).append("\n");
//                                }
//                                reader.close();
//                                devicehashString = sb.toString().trim();
//                                //Make sure you close all streams.
//                                fin.close();
//                                devicehashDownloaded = true;
//                            } catch (IOException e) {
//                                e.printStackTrace();
//                            }
//                            if(devicehashDownloaded) {
//                                if (checkHash(device_temp,devicehashString)) {
//                                    device_temp.renameTo(device);
//                                } else {
//                                    deviceDownloaded = false;
//                                    progress.setMessage("Download File Hash Mismatch");
//                                }
//                                if(deviceDownloaded && driverDownloaded) {
//                                    progress.setMessage("Download Finished\nReboot Device");
//                                    progress.setProgress(100);
//                                }
//                            }
//                        }
//                    }
//                }
//
//                @Override
//                public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
//                    progress.setProgress((int) (bytesCurrent/bytesTotal*100));
//                }
//
//                @Override
//                public void onError(int id, Exception ex) {
//                    progress.setMessage(ex.getMessage());
//                }
//            };
//            tohash.setTransferListener(transferListener);
//            to.setTransferListener(transferListener);
//            deviceto.setTransferListener(transferListener);
//            devicehash.setTransferListener(transferListener);
//            progress.setOnCancelListener(new DialogInterface.OnCancelListener() {
//                @Override
//                public void onCancel(DialogInterface dialog) {
//                    transferUtility.cancel(to.getId());
//                }
//            });
//            progress.show();
        }
    };
}
