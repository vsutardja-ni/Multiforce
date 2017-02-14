package com.nextinput.EJML;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    DemoView myDemoView;

    // Storage Permissions
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    /**
     * Checks if the app has permission to write to device storage
     * If the app does not has permission then the user will be prompted to grant permissions
     * @param activity
     */
    public static void verifyStoragePermissions(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }

    private long lastPressTime;
    private static final long DOUBLE_PRESS_INTERVAL = 250;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        verifyStoragePermissions(this);

        setContentView(R.layout.activity_main);

        // Start polling the Driver for new sensor and SAMD10 data
        DriverActivity.setPollAppData(true);

        // Set content view to the DrawView
        myDemoView = (DemoView) findViewById(R.id.drawingCanvas);

        // Set up the user interaction to manually show or hide the system UI.
        myDemoView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                long pressTime = System.currentTimeMillis();

                if(pressTime - lastPressTime <= DOUBLE_PRESS_INTERVAL)
                {
                    Intent launchNewIntent = new Intent(MainActivity.this, SettingsActivity.class);
                    startActivityForResult(launchNewIntent, 0);

                }

                lastPressTime = pressTime;
            }
        });

        // Set default settings and override user preferences (e.g. start out in demo mode, not data mode)
        //PreferenceManager.setDefaultValues(this, R.xml.preferences, true); // Comment out during development

        // Load settings into the demo
        LoadSettings();
    }

    @Override
    protected void onResume() {
        super.onResume();
        DriverActivity.setPollAppData(true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        LoadSettings();
    }

    private void LoadSettings()
    {
        // TODO: Load Default Settings and put them into the demo via myDrawView.LoadSettings
        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        String mode_string = SP.getString("demo", "0");
        String filter_string = SP.getString("filter", "0");
        String data_logging_string = SP.getString("data_logging", "0");

        Log.d("NextInput", "Mode = " + mode_string + "  Filter = " + filter_string + "  DataLogging = " + data_logging_string);

        try {
            int mode = Integer.parseInt(mode_string);
            float filter = Float.parseFloat(filter_string);
            int data_logging = Integer.parseInt(data_logging_string);

            myDemoView.LoadSettings(/*TODO: Update settings variables here, e.g.: mode, filter, scaler*/ mode, filter, data_logging);

        } catch (NumberFormatException nfe) {
            Log.d("NextInput", "Failed to set settings");
        }
    }
}
