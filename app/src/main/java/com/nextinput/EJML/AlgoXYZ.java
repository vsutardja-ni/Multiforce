package com.nextinput.EJML;

/**
 * Created by Ian Campbell on 12/21/2016.
 */

public class AlgoXYZ {
    public boolean is_button_found;
    public int x;
    public int y;

    public float x_norm;
    public float y_norm;

    public float max_x_norm;
    public float min_x_norm;

    public float sum_norm_S1S2;    // Used for Y calc
    public float sum_norm_S1S3;    // Used for X calc

    float[] norm_y_coeffs = {
            0.9358f,
            -1.5478f,
            2.6702f,
            -2.0135f
    };

    float[] max_norm_x_coeffs = {
            0.8476f,
            -1.299f,
            1.3657f
    };

    float[] min_norm_x_coeffs = {
            0.088f,
            1.37f,
            -1.3837f
    };

    public void SetCoefficients(float[] coefficients)
    {
        if(coefficients != null)
        {
            if(coefficients.length == 10)
            {
                norm_y_coeffs[0] = coefficients[0];
                norm_y_coeffs[1] = coefficients[1];
                norm_y_coeffs[2] = coefficients[2];
                norm_y_coeffs[3] = coefficients[3];

                max_norm_x_coeffs[0] = coefficients[4];
                max_norm_x_coeffs[1] = coefficients[5];
                max_norm_x_coeffs[2] = coefficients[6];

                min_norm_x_coeffs[0] = coefficients[7];
                min_norm_x_coeffs[1] = coefficients[8];
                min_norm_x_coeffs[2] = coefficients[9];
            }
        }
    }

    public void ProcessData(ForceTouchData myForceTouchData)
    {
        int[] scaled = new int[4];

        for(int i = 0; i < myForceTouchData.num_sensors; i ++)
        {
            scaled[i] = myForceTouchData.sensors[i].scaled;
        }

        float[] norm_scaled_values = CalcNormValues(scaled);

        y_norm = CalcYNorm(norm_scaled_values);
        x_norm = CalcXNorm(norm_scaled_values, y_norm);

        x = (int)(x_norm * 1080);
        y = (int)(y_norm * 1920);
    }

    private float[] CalcNormValues(int[] scaled_values)
    {
        float[] norm_values = new float[scaled_values.length];
        float sum_scaled = 0;

        for(int i = 0; i < scaled_values.length; i ++)
        {
            sum_scaled += scaled_values[i];
        }

        for(int i = 0; i < scaled_values.length; i ++)
        {
            norm_values[i] = scaled_values[i] / sum_scaled;
        }

        return norm_values;
    }

    private float CalcYNorm(float[] norm_values)
    {
        sum_norm_S1S2 = norm_values[0] + norm_values[1];

        return CalcPoly(norm_y_coeffs, sum_norm_S1S2);
    }

    private float CalcXNorm(float[] norm_values, float y_norm)
    {
        sum_norm_S1S3 =  norm_values[0] + norm_values[2];
        max_x_norm = CalcPoly(max_norm_x_coeffs, y_norm);
        min_x_norm = CalcPoly(min_norm_x_coeffs, y_norm);

        //Linear interp between two points (TODO: change to poly interp
        return 1 - (sum_norm_S1S3 - min_x_norm) / (max_x_norm - min_x_norm);
    }

    private float CalcPoly(float[] cf, float value_in)
    {
        float value_out = 0;

        for(int i = 0; i < cf.length; i ++)
        {
            value_out += cf[i]*Math.pow(value_in, i);
        }

        return value_out;
    }
}
