package com.nextinput.EJML;

/**
 * Created by Ian Campbell on 12/20/2016.
 *
 * Purpose of this class is to provide a convenient means to store data from FW
 */

public class ForceTouchData {

    public class Sensor {
        int raw;            // Sensor raw data
        int scaled;         // Sensor scaled data
        int min;            // Sensor min cal data
        int variance;       // Sensor max cal data
        byte status;        // Sensor status (see bit description below)
        int range = 2000;   // Default range

        private int past_scaled;
        private int raw_for_variance;
        private int past_raw_for_variance;
        private int[] past_mins = new int[50];
    }

    public class Settings {
        int force_threshold = 200;
        int release_threshold = 150;
        int auto_cal_threshold = 100;
        int variance_threshold = 200;
        int scaled_value_divider = 300;

        int auto_cal_of = 10;
        int false_force_of = 100;
        int lift_off_overflow = 10;

        float alpha_min = 0.3f;
        float alpha_variance = 0.2f;
        float alpha_scaled = 0.5f;
    }

    public class Values{
        int auto_cal_counter;
        int false_force_counter;
        int lift_off_counter;

    }

    public class ForceEvents{
        boolean past_event = false;
        boolean current_event = false;
    }

    public Sensor sensors[];
    public Values values;
    public Settings settings;
    public ForceEvents forceEvents;

    public int mode;

    public int f;

    public int loop_counter;    // Debug value to count INT cycles
    public int event_counter;    // Debug value to count valid force events

    public int num_sensors;

    public boolean is_captouch_event;

    private boolean is_first_read = true;

    // Primary instantiation - requires
    ForceTouchData(int num_sensors)
    {
        values = new Values();
        settings = new Settings();
        forceEvents = new ForceEvents();

        this.num_sensors = num_sensors;
        sensors = new Sensor[num_sensors];

        for(int i = 0; i < num_sensors; i ++)
        {
            sensors[i] = new Sensor();
        }
    }

    // Gets sensor data directly from driver
    public void GetData()
    {
        int[] data = DriverActivity.getAppData();

        int index = 5;

        for(int i = 0; i < num_sensors; i ++)
        {
            sensors[i].raw = data[index++];
//            sensors[i].scaled = data[index++];
//            sensors[i].min = data[index++];
//            sensors[i].variance = data[index++];
//            sensors[i].status = (byte)data[index++];
            index++;
            index++;
            index++;
            index++;
        }
    }

    // Sets up for ProcessRawData()
    private void InitializeSensors()
    {
        if(sensors != null) {
            for(int i = 0; i < num_sensors; i ++)
            {
                sensors[i].min = sensors[i].raw;
                sensors[i].raw_for_variance = sensors[i].raw;
                sensors[i].past_raw_for_variance = sensors[i].raw;

                sensors[i].scaled = 0;
                sensors[i].variance = 0;
                sensors[i].status = 0;

                sensors[i].past_scaled = 0;

                for(int j = 0; j < sensors[i].past_mins.length; j++)
                {
                    sensors[i].past_mins[j] = sensors[i].raw;
                }
            }
        }
    }

    // Use this function to process raw data locally within the app
    public void ProcessRawData()
    {
        f = 0;

        if(is_first_read)
        {
            InitializeSensors();
            is_first_read = false;
        }
        else
        {
            for(int i = 0; i < num_sensors; i ++)
            {
                // Calculate variance
                sensors[i].raw_for_variance = (int)((float)sensors[i].raw * settings.alpha_variance +
                        (float)sensors[i].past_raw_for_variance * (1 - settings.alpha_variance));

                sensors[i].variance = sensors[i].raw - sensors[i].raw_for_variance;

                // Set Sensor Status Pins
                if(sensors[i].raw - sensors[i].min > settings.force_threshold)
                    SetSensorStatusBit(sensors[i], (byte) 0);
                else
                    ClearSensorStatusBit(sensors[i], (byte)0);

                if(sensors[i].raw - sensors[i].min > settings.auto_cal_threshold)
                    SetSensorStatusBit(sensors[i], (byte)1);
                else
                    ClearSensorStatusBit(sensors[i], (byte)1);

                if(Math.abs(sensors[i].variance) > settings.variance_threshold)
                    SetSensorStatusBit(sensors[i], (byte)2);
                else
                    ClearSensorStatusBit(sensors[i], (byte)2);

                ClearSensorStatusBit(sensors[i], (byte)3);

                // Scale Data
                sensors[i].past_scaled = sensors[i].scaled;
                int new_scaled = (sensors[i].raw - sensors[i].min) * settings.scaled_value_divider / sensors[i].range;
                sensors[i].scaled = (int)((float)new_scaled * settings.alpha_scaled + (float)sensors[i].past_scaled * (1-settings.alpha_scaled));

                f += sensors[i].scaled;
            }
        }

        loop_counter ++;

        EventDetection();
    }

    private void EventDetection()
    {
        forceEvents.past_event = forceEvents.current_event;

        forceEvents.current_event = false;

        if(sensors != null)
        {
            for (int i = 0; i < num_sensors; i++)
            {
                // Get sensor status bit corresponding to Force Threshold
                if(GetSensorStatusBit(sensors[i], (byte)0) == 1)
                {
                    forceEvents.current_event = true;
                }
            }
        }

        if(!forceEvents.past_event && !forceEvents.current_event)
            NoTouchEvent();

        if(!forceEvents.past_event && forceEvents.current_event)
            StartTouchEvent();

        if(forceEvents.past_event && forceEvents.current_event)
            ContinueTouchEvent();

        if(forceEvents.past_event && !forceEvents.current_event)
            EndTouchEvent();
    }

    private void NoTouchEvent()
    {
        event_counter = 0;

        if(IsAutoCal())
            HandleAutoCalibration();
    }

    private void StartTouchEvent()
    {
        event_counter ++;

        DiscardMins();
    }

    private void ContinueTouchEvent()
    {
        event_counter ++;

    }

    private void EndTouchEvent()
    {
        event_counter = 0;
        values.lift_off_counter = 0;
    }

    private boolean IsAutoCal()
    {
        if(is_captouch_event)
        {
            values.auto_cal_counter = 0;
            values.lift_off_counter = 0;
            return false; // Don't do auto-cal if there's a captouch event
        }

        if(values.lift_off_counter < settings.lift_off_overflow)
        {
            values.lift_off_counter++;
            values.auto_cal_counter = 0;
            return false;
        }

        if(!AreSensorsAboveAutoCal())
            values.auto_cal_counter++;

        if(values.auto_cal_counter > settings.auto_cal_of)
        {
            values.auto_cal_counter = 0;
            return true;
        }

        return false;
    }

    private boolean AreSensorsAboveAutoCal()
    {
        for(int i = 0; i < num_sensors; i ++)
        {
            if((sensors[i].raw - sensors[i].min) > settings.auto_cal_threshold)
                return true;
        }

        return false;
    }

    private void HandleAutoCalibration()
    {
        for(int i = 0; i < num_sensors; i ++)
        {
            sensors[i].min = (int)((float)sensors[i].raw * settings.alpha_min +
                    (float)sensors[i].past_mins[0] * (1 - settings.alpha_min));

            // Make it really hard for sensors to increase
            float increase_factor = 0.25f;

            if(sensors[i].min - sensors[i].past_mins[0] > 50)
                sensors[i].min = (int)((float)(sensors[i].min - sensors[i].past_mins[0]) * increase_factor) +
                        sensors[i].past_mins[0];

            // Right shift past min buffer
            for(int j = 0; j < sensors[i].past_mins.length - 1; j++)
            {
                sensors[i].past_mins[j + 1] = sensors[i].past_mins[j];
            }

            // Store min into past min buffer - most recent min is always at the head (0 index)
            sensors[i].past_mins[0] = sensors[i].min;

            sensors[i].past_raw_for_variance = sensors[i].min;
        }
    }

    private void DiscardMins()
    {
        for(int i = 0; i < num_sensors; i ++)
        {
            // Find the minimum min in the buffer
            int min_min = Integer.MAX_VALUE;

            for(int j = 0; j < sensors[i].past_mins.length; j ++)
            {
                if(sensors[i].past_mins[j] < min_min)
                    min_min = sensors[i].past_mins[j];
            }

            sensors[i].min = min_min;
        }
    }

    private void SetSensorStatusBit(Sensor sensor, byte bit)
    {
        sensor.status |= (1 << bit);
    }

    private void ClearSensorStatusBit(Sensor sensor, byte bit)
    {
        sensor.status &= ~(1 << bit);
    }

    private byte GetSensorStatusBit(Sensor sensor, byte bit)
    {
        return (byte)(sensor.status >> bit & 1);
    }
}
