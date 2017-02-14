package com.nextinput.EJML;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.icu.text.SimpleDateFormat;
import android.icu.util.Calendar;
import android.os.Environment;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.OutputStreamWriter;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by Ian Campbell on 12/14/2016.
 * This is the primary data drawing view
 */
public class DemoView extends View {
    public int demo_mode = 0;           // Default is debug
    ForceTouchData myForceTouchData;
    AlgoMultiForce myAlgo;
    int pointerCount;
    float x1, y1, x2, y2;

    // Settings
    public boolean IsLoggingData = false;

    OutputStreamWriter outputWriter;

    Context mContext;

    public DemoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;

        // Set back button to invisible initially
        HideSystemUI();

        myForceTouchData = new ForceTouchData(4);

        // Set force threshold to pretty high
        myForceTouchData.settings.force_threshold = 500;

        myAlgo = new AlgoMultiForce();

        // Read in XYZ Algo coefficients from file
        // TODO: Read in settings like the below method
        float[] coefficients = ReadCoefficients();

        if(coefficients != null)
        {
            myAlgo.SetCoefficients(coefficients);

            String str_coefficients = "";

            for(int i = 0; i < coefficients.length; i++)
            {
                str_coefficients += coefficients[i] + ",";
            }

            Log.d("NextInput", "Coefficients loaded: " + str_coefficients);
        }

        // Setup Timer to refresh screen every 20 ms
        Timer timer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                postInvalidate();
            }
        };
        long whenToStart = 10L; // 10 ms
        long howOften = 10L;    // 10 ms
        timer.scheduleAtFixedRate(task, whenToStart, howOften);

        setupDrawing();
    }

    private float[] ReadCoefficients()
    {
        float[] coefficients = new float[576];
        int idx = 0;

        try
        {
            File sdCard = Environment.getExternalStorageDirectory();
            File file = new File(sdCard.getAbsolutePath() + "/nextinput/multiforce_coefficients.txt");

            //Read text from file
            BufferedReader br = new BufferedReader(new FileReader(file));
            String[] line;

            // Six 96pt tables
            for (int i = 0; i < 6; i++) {
                line = br.readLine().split(",");
                for (int j = 0; j < 96; j++) {
                    coefficients[idx++] = Float.parseFloat(line[j]);
                }
            }
        }
        catch (Exception ex)
        {
            Log.d("NextInput","Failed to read multiforce_coefficients.txt.");
            coefficients = null;
        }

        return coefficients;
    }
    public void LoadSettings(/*TODO: Demo Setting Variables*/ int demo_mode, float filter, int data_logging)
    {
        // TODO: Update Demo Settings Here
        this.demo_mode = demo_mode;

        myForceTouchData.settings.alpha_scaled = filter;

        if(data_logging == 1 && !IsLoggingData)
        {
            StartLoggingData();
        }
        else
        {
            StopLoggingData();
        }
    }

    // Drawing and canvas paint
    private Paint drawPaint, canvasPaint, textPaint, axisPaint;

    private void setupDrawing() {
        drawPaint = new Paint();
        drawPaint.setColor(Color.BLUE);
        drawPaint.setAntiAlias(true);
        drawPaint.setStyle(Paint.Style.STROKE);
        drawPaint.setStrokeWidth(2);
        drawPaint.setStrokeJoin(Paint.Join.ROUND);
        drawPaint.setStrokeCap(Paint.Cap.ROUND);

        textPaint = new Paint();
        textPaint.setColor(Color.GRAY);
        textPaint.setTextSize(30);

        canvasPaint = new Paint(Paint.DITHER_FLAG);

        axisPaint = new Paint();
        axisPaint.setColor(Color.GRAY);
        axisPaint.setAntiAlias(true);
        axisPaint.setStrokeWidth(1);
        axisPaint.setStyle(Paint.Style.STROKE);
    }

    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        text_height = 30;
        label_offset_x = 40;
        left_margin = 50;
        left_margin_bar = 175;
        right_margin = w - 100;
        right_margin_bar = w - 150;
        top_margin = text_height + 10;
        bottom_margin = h - text_height * 5;
    }

    protected void onDraw(Canvas canvas) {
        HideSystemUI();

        // Get data from DriverActivity
        myForceTouchData.GetData();

        myForceTouchData.ProcessRawData(); // Run auto-cal, event detection, etc.

        HandleAlgo();

        //if(demo_mode == 1)
            DrawDebugData(canvas);   // Can be commented out or use Setting to toggle

        // Draw demo
        //if(demo_mode == 0)
            DrawDemo(canvas);       // Can be commented out or use Setting to toggle

        //DrawCalDots(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            pointerCount = pointerCount - 1;
        } else {
            pointerCount = event.getPointerCount();
            switch (pointerCount) {
                case 2:
                    x2 = event.getX(1);
                    y2 = event.getY(1);
                case 1:
                    x1 = event.getX(0);
                    y1 = event.getY(0);
                default:
                    break;
            }
        }
        return true;
    }

    private void HandleAlgo()
    {
        // TODO: Update with your own demo and algo logic
        switch (pointerCount) {
            case 2:
                myAlgo.ProcessData(myForceTouchData, x1, y1, x2, y2);
                break;
            case 1:
                myAlgo.ProcessData(myForceTouchData, x1, y1);
                break;
            default:
                break;
        }
    }

    private void DrawDemo(Canvas canvas)
    {
        switch (pointerCount) {
            case 2:
                canvas.drawCircle(x2, y2, myAlgo.f2, drawPaint);
            case 1:
                canvas.drawCircle(x1, y1, myAlgo.f1, drawPaint);
            default:
                break;
        }
    }

    // Debug Data variables
    int text_height, label_offset_x, left_margin, left_margin_bar, right_margin, right_margin_bar, top_margin, bottom_margin;

    private void DrawDebugData(Canvas canvas)
    {
        DrawAxes(canvas);
        DrawSystemData(canvas);
        DrawSensorData(canvas);
        DrawCalDots(canvas);
    }

    private void DrawAxes(Canvas canvas)
    {
        switch (pointerCount) {
            case 2:
                canvas.drawLine(0.0f, y2, canvas.getWidth(), y2, axisPaint);
                canvas.drawLine(x2, 0.0f, x2, canvas.getHeight(), axisPaint);
            case 1:
                canvas.drawLine(0.0f, y1, canvas.getWidth(), y1, axisPaint);
                canvas.drawLine(x1, 0.0f, x1, canvas.getHeight(), axisPaint);
            default:
                break;
        }
    }

    private void DrawSystemData(Canvas canvas)
    {
        // ForceTouch data
        canvas.drawText("lc: " + myForceTouchData.loop_counter, left_margin, canvas.getHeight() / 2, textPaint);
        canvas.drawText("ec: " + myForceTouchData.event_counter, left_margin, canvas.getHeight() / 2 + text_height, textPaint);
        canvas.drawText("ac: " + myForceTouchData.values.auto_cal_counter, left_margin, canvas.getHeight() / 2 + text_height * 2, textPaint);
        canvas.drawText("lc: " + myForceTouchData.values.lift_off_counter, left_margin, canvas.getHeight() / 2 + text_height * 3, textPaint);
        canvas.drawText("fc: " + myForceTouchData.values.false_force_counter, left_margin, canvas.getHeight() / 2 + text_height * 4, textPaint);
        canvas.drawText("fs: " + myForceTouchData.settings.alpha_scaled, left_margin, canvas.getHeight() / 2 + text_height * 5, textPaint);

        canvas.drawText("f: " + myForceTouchData.f, canvas.getWidth() / 2 - 100, top_margin, textPaint);

        canvas.drawText("f1: " + myAlgo.f1, canvas.getWidth() / 2 - 100, top_margin + text_height, textPaint);
        canvas.drawText("f2: " + myAlgo.f2, canvas.getWidth() / 2 - 100, top_margin + text_height * 2, textPaint);

        canvas.drawText("x1:\t" + String.format("%.03f", x1), right_margin - 150, canvas.getHeight() / 2 - 300, textPaint);
        canvas.drawText("y1:\t" + String.format("%.03f", y1), right_margin - 150, canvas.getHeight() / 2 - 300 + text_height, textPaint);
        canvas.drawText("a1:\t" + myAlgo.tri1[0] + ", " + myAlgo.tri1[1], right_margin - 150, canvas.getHeight() / 2 - 300 + 2 * text_height, textPaint);
        canvas.drawText("b1:\t" + myAlgo.tri1[2] + ", " + myAlgo.tri1[3], right_margin - 150, canvas.getHeight() / 2 - 300 + 3 * text_height, textPaint);
        canvas.drawText("c1:\t" + myAlgo.tri1[4] + ", " + myAlgo.tri1[5], right_margin - 150, canvas.getHeight() / 2 - 300 + 4 * text_height, textPaint);
        canvas.drawText("u1:\t" + String.format("%.03f", myAlgo.uvw1[0]), right_margin - 150, canvas.getHeight() / 2 - 300 + 5 * text_height, textPaint);
        canvas.drawText("v1:\t" + String.format("%.03f", myAlgo.uvw1[1]), right_margin - 150, canvas.getHeight() / 2 - 300 + 6 * text_height, textPaint);
        canvas.drawText("w1:\t" + String.format("%.03f", myAlgo.uvw1[2]), right_margin - 150, canvas.getHeight() / 2 - 300 + 7 * text_height, textPaint);

        canvas.drawText("x2:\t" + String.format("%.03f", x2), right_margin - 150, canvas.getHeight() / 2, textPaint);
        canvas.drawText("y2:\t" + String.format("%.03f", y2), right_margin - 150, canvas.getHeight() / 2 + text_height, textPaint);
        canvas.drawText("a2:\t" + myAlgo.tri2[0] + ", " + myAlgo.tri2[1], right_margin - 150, canvas.getHeight() / 2 + 2 * text_height, textPaint);
        canvas.drawText("b2:\t" + myAlgo.tri2[2] + ", " + myAlgo.tri2[3], right_margin - 150, canvas.getHeight() / 2 + 3 * text_height, textPaint);
        canvas.drawText("c2:\t" + myAlgo.tri2[4] + ", " + myAlgo.tri2[5], right_margin - 150, canvas.getHeight() / 2 + 4 * text_height, textPaint);
        canvas.drawText("u2:\t" + String.format("%.03f", myAlgo.uvw2[0]), right_margin - 150, canvas.getHeight() / 2 + 5 * text_height, textPaint);
        canvas.drawText("v2:\t" + String.format("%.03f", myAlgo.uvw2[1]), right_margin - 150, canvas.getHeight() / 2 + 6 * text_height, textPaint);
        canvas.drawText("w2:\t" + String.format("%.03f", myAlgo.uvw2[2]), right_margin - 150, canvas.getHeight() / 2 + 7 * text_height, textPaint);

        canvas.drawText("pC:\t" + pointerCount, right_margin - 150, canvas.getHeight() / 2 + text_height * 9, textPaint);
    }

    private void DrawSensorData(Canvas canvas)
    {
        float BAR_SCALER = 100;
        Paint p = new Paint();
        p.setColor(Color.GREEN);
        p.setStyle(Paint.Style.FILL);

        for(int x = 0; x < 2; x ++)
        {
            for(int y = 0; y < 2; y++)
            {
                // Draw Text
                canvas.drawText("S:", (1-x) * left_margin + x * right_margin - label_offset_x, (1-y) * top_margin + y * bottom_margin, textPaint);
                canvas.drawText("R:", (1-x) * left_margin + x * right_margin - label_offset_x, (1-y) * top_margin + y * bottom_margin + text_height, textPaint);
                canvas.drawText("m:", (1-x) * left_margin + x * right_margin - label_offset_x, (1-y) * top_margin + y * bottom_margin + text_height * 2, textPaint);
                canvas.drawText("v:", (1-x) * left_margin + x * right_margin - label_offset_x, (1-y) * top_margin + y * bottom_margin + text_height * 3, textPaint);
                canvas.drawText("s:", (1-x) * left_margin + x * right_margin - label_offset_x, (1-y) * top_margin + y * bottom_margin + text_height * 4, textPaint);

                if(myForceTouchData.sensors != null)
                {
                    canvas.drawText("" + myForceTouchData.sensors[x + (y * 2)].scaled, (1-x) * left_margin + x * right_margin, (1-y) * top_margin + y * bottom_margin, textPaint);
                    canvas.drawText("" + myForceTouchData.sensors[x + (y * 2)].raw, (1-x) * left_margin + x * right_margin, (1-y) * top_margin + y * bottom_margin + text_height, textPaint);
                    canvas.drawText("" + myForceTouchData.sensors[x + (y * 2)].min, (1 - x) * left_margin + x * right_margin, (1 - y) * top_margin + y * bottom_margin + text_height * 2, textPaint);
                    canvas.drawText("" + myForceTouchData.sensors[x + (y * 2)].variance, (1-x) * left_margin + x * right_margin, (1-y) * top_margin + y * bottom_margin + text_height * 3, textPaint);
                    canvas.drawText("" + String.format("%4s", Integer.toBinaryString(myForceTouchData.sensors[x + (y * 2)].status)).replace(' ', '0'), (1 - x) * left_margin + x * right_margin, (1 - y) * top_margin + y * bottom_margin + text_height * 4, textPaint);
                }

                // Draw Sensor Columns
                //canvas.drawRect((1-x) * left_margin + x * right_margin - label_offset_x, (1-y) * top_margin + y * bottom_margin + text_height * 4, 20, 30, p);

                // Syntax is left, top, right, bottom, Paint
                //canvas.drawRect(100, 0, 100, 100, p);
                if(myForceTouchData.sensors != null) {
                    int bottom = (1 - y) * top_margin + y * bottom_margin + text_height * 4;
                    int top = bottom - (int) ((float) ((float) myForceTouchData.sensors[x + (y * 2)].scaled / (float) 2048.0f) * (float) (BAR_SCALER));


                    Rect barGraph = new Rect(
                            (1 - x) * left_margin_bar + x * right_margin_bar - label_offset_x,
                            top,
                            (1 - x) * left_margin_bar + x * right_margin_bar - label_offset_x + 20,
                            bottom);

                    canvas.drawRect(barGraph, p);
                }
            }
        }
    }

    private void DrawCalDots(Canvas canvas)
    {
        int dot_size = 10;

        int[] x_dots = new int[]{
                67,
                203,
                338,
                473,
                608,
                743,
                878,
                1013
        };

        int[] y_dots = new int[]{
                80,
                240,
                400,
                560,
                720,
                880,
                1040,
                1200,
                1360,
                1520,
                1680,
                1840
        };

        for(int x = 0; x < x_dots.length; x ++)
        {
            for(int y = 0; y < y_dots.length; y++)
            {
                canvas.drawOval(x_dots[x] - dot_size/2, y_dots[y] - dot_size /2, x_dots[x] + dot_size / 2, y_dots[y] + dot_size / 2, drawPaint);
            }
        }

    }

    public void StartLoggingData()
    {
        IsLoggingData = true;

        Calendar c = Calendar.getInstance();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd_HH_mm_ss");
        String currentDateandTime = df.format(c.getTime());

        File sdCard = Environment.getExternalStorageDirectory();
        File dir = new File(sdCard.getAbsolutePath() + "/nextinput");
        dir.mkdirs();
        File file = new File(dir, "Log_" + currentDateandTime);
        FileOutputStream f; // File stream for data log

        try {
            f = new FileOutputStream(file);
            outputWriter = new OutputStreamWriter(f);

        }
        catch (Exception e) {
            Log.d("NextInput", "Failed to make filestream for data logging: " + e.getMessage());
        }

        LogHeader(); // Creates header
    }

    public void StopLoggingData()
    {
        IsLoggingData = false;

        try
        {
            outputWriter.close();
        }
        catch (Exception e)
        {
            Log.d("NextInput", "Failed to close filestream for data logging: " + e.getMessage());
        }
    }

    private void LogHeader()
    {
        // TODO: Update this to match LogData below for better readability
        String header_string = "";

        // Basic sensor headers
        for(int i = 0; i < myForceTouchData.num_sensors; i ++)
        {
            // Log basic sensor data first
            // Make sure each of these blocks ends with a comma!
            header_string +=    "Raw " + (i+1) + "," +
                                "Scaled " + (i+1)  + "," +
                                "Min " + (i+1)  + "," +
                                "Var " + (i+1)  + "," +
                                "Status " + (i+1)  + ",";
        }

        // ForceTouch data headers
        header_string +=    "f," +
                            "CE," +
                            "PE,";

        // Algo data headers
        header_string +=    "ax," +
                            "ay," +
                            "S1S2," +
                            "S1S3," +
                            "xnorm," +
                            "max_xnorm," +
                            "min_xnorm,";

        header_string +=    "timestamp";

        try
        {
            outputWriter.write(header_string);
        }
        catch (Exception e)
        {
            Log.d("NextInput", "Error writing header: " + e.toString());
        }
    }

    private void LogData()
    {
        //TODO: Add your CSV string here:

        String data_string = "";

        // Log basic sensor data first
        // Make sure each of these blocks ends with a comma!
        for(int i = 0; i < myForceTouchData.num_sensors; i ++)
        {
            data_string +=  myForceTouchData.sensors[i].raw + "," +
                            myForceTouchData.sensors[i].scaled + "," +
                            myForceTouchData.sensors[i].min + "," +
                            myForceTouchData.sensors[i].variance + "," +
                            myForceTouchData.sensors[i].status + ",";

        }

        // Write additional ForceTouch data:
        data_string +=  myForceTouchData.f + "," +
                        myForceTouchData.forceEvents.current_event + "," +
                        myForceTouchData.forceEvents.past_event + ",";

        // Write Algo data
        // data_string +=  myAlgoXYZ.x + "," +
        //                 myAlgoXYZ.y + "," +
        //                 myAlgoXYZ.sum_norm_S1S2 + "," +
        //                 myAlgoXYZ.sum_norm_S1S3 + "," +
        //                 myAlgoXYZ.x_norm + "," +
        //                 myAlgoXYZ.max_x_norm + "," +
        //                 myAlgoXYZ.min_x_norm + ",";

        // Write timestamp and newline
        Calendar c = Calendar.getInstance();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss_SSS");
        String currentDateandTime = df.format(c.getTime());

        data_string += currentDateandTime + System.getProperty("line.separator"); // Last item

        try
        {
            outputWriter.write(data_string);
        }
        catch (Exception e)
        {
            Log.d("NextInput", "Error logging data: " + e.toString());
        }
    }

    // This snippet hides the system bars.
    public void HideSystemUI() {
        // Set the IMMERSIVE flag.
        // Set the content to appear under the system bars so that the content
        // doesn't resize when the system bars hide and show.
        setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                        | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                        | View.SYSTEM_UI_FLAG_IMMERSIVE);
    }
}
