package com.nextinput.EJML;

import org.ejml.data.DenseMatrix64F;
import org.ejml.factory.LinearSolverFactory;
import org.ejml.interfaces.linsol.LinearSolver;

/**
 * Created by vsutardja on 2/10/2017.
 */

public class AlgoMultiForce {
    public int f1 = 0;
    public int f2 = 0;

    public int[] tri1 = new int[6];
    public int[] tri2 = new int[6];

    public float[] uvw1 = new float[3];
    public float[] uvw2 = new float[3];

    public float f1u = 0;
    public float f2u = 0;

    public float[] fc1 = new float[2];
    public float[] fc2 = new float[2];

    public

    float[][] fc2N = new float[12][8];
    float[][] fc4N = new float[12][8];
    float[][] w_S1 = new float[12][8];
    float[][] w_S2 = new float[12][8];
    float[][] w_S3 = new float[12][8];
    float[][] w_S4 = new float[12][8];

    public void SetCoefficients(float[] coefficients) {
        if (coefficients != null) {
            if (coefficients.length == 576) {
                for (int i = 0; i < 96; i++) {
                    int r = i / 8;
                    int c = i % 8;
                    fc2N[r][c] = coefficients[i];
                    fc4N[r][c] = coefficients[96+i];
                    w_S1[r][c] = coefficients[96*2+i];
                    w_S2[r][c] = coefficients[96*3+i];
                    w_S3[r][c] = coefficients[96*4+i];
                    w_S4[r][c] = coefficients[96*5+i];
                }
            }
        }
    }

    public void ProcessData(ForceTouchData myForceTouchData, float x, float y) {
        // Clamp coordinates
        if (x < 67.5) {
            x = 68;
        }
        if (x > 1012.5) {
            x = 1012;
        }
        if (y < 80) {
            y = 81;
        }
        if (y > 1840) {
            y = 1839;
        }

        // Get bounding triangle
        tri1 = BoundingTriangle(x, y);
        tri2 = new int[] {0, 0, 0, 0, 0, 0};

        // Get barycentric coordinates
        uvw1 = BarycentricCoords(tri1, x, y);
        uvw2 = new float[] {0, 0, 0};

        // Correct that shit
        f1u = 0;
        f2u = 0;
        for (int i = 0; i < 4; i++) {
            f1u += myForceTouchData.sensors[i].scaled;
        }

        fc1 = SingleTouchCorrect(tri1, uvw1);
        fc2 = new float[] {0, 0};

        if (f1u < fc1[0]) {
            f1 = (int)(f1u / fc1[0] * 512);
        } else if (f1u > fc1[1]) {
            f1 = (int)(f1u / fc1[1] * 1024);
        } else {
            float w1 = (f1u - fc1[0]) / (fc1[1] - fc1[0]);
            f1 = (int)(w1 * f1u / fc1[1] * 1024 + (1 - w1) * f1u / fc1[0] * 512);
        }
    }

    public void ProcessData(ForceTouchData myForceTouchData, float x1, float y1, float x2, float y2) {
        // Clamp coordinates
        if (x1 < 67.5) {
            x1 = 68;
        }
        if (x1 > 1012.5) {
            x1 = 1012;
        }
        if (y1 < 80) {
            y1 = 81;
        }
        if (y1 > 1840) {
            y1 = 1839;
        }
        if (x2 < 67.5) {
            x2 = 68;
        }
        if (x2 > 1012.5) {
            x2 = 1012;
        }
        if (y2 < 80) {
            y2 = 81;
        }
        if (y2 > 1840) {
            y2 = 1839;
        }


        // Get bounding triangle
        tri1 = BoundingTriangle(x1, y1);
        tri2 = BoundingTriangle(x2, y2);

        // Get barycentric coordinates
        uvw1 = BarycentricCoords(tri1, x1, y1);
        uvw2 = BarycentricCoords(tri2, x2, y2);

        // Get interpolated sensor weights
        float[] w_T1 = SingleTouchWeights(tri1, uvw1);
        float[] w_T2 = SingleTouchWeights(tri2, uvw2);

        // Separate the two forces
        float[] scaled = new float[4];
        float total = 0;
        for (int i = 0; i < 4; i++) {
            scaled[i] = myForceTouchData.sensors[i].scaled;
            total += scaled[i];
        }

        float[] w = new float[4];
        for (int i = 0; i < 4; i++) {
            w[i] = scaled[i] / total;
        }

        LinearSolver<DenseMatrix64F> solver = LinearSolverFactory.leastSquares(16, 8);
        DenseMatrix64F A = new DenseMatrix64F(new double[][] {{w[0] - 1,    w[0],        w[0],        w[0],        w[0] - 1,    w[0],        w[0],        w[0]},
                                                              {w[1],        w[1] - 1,    w[1],        w[1],        w[1],        w[1] - 1,    w[1],        w[1]},
                                                              {w[2],        w[2],        w[2] - 1,    w[2],        w[2],        w[2],        w[2] - 1,    w[2]},
                                                              {w[3],        w[3],        w[3],        w[3] - 1,    w[3],        w[3],        w[3],        w[3] - 1},
                                                              {w_T1[0] - 1, w_T1[0],     w_T1[0],     w_T1[0],     0,           0,           0,           0},
                                                              {w_T1[1],     w_T1[1] - 1, w_T1[1],     w_T1[1],     0,           0,           0,           0},
                                                              {w_T1[2],     w_T1[2],     w_T1[2] - 1, w_T1[2],     0,           0,           0,           0},
                                                              {w_T1[3],     w_T1[3],     w_T1[3],     w_T1[3] - 1, 0,           0,           0,           0},
                                                              {0,           0,           0,           0,           w_T2[0] - 1, w_T2[0],     w_T2[0],     w_T2[0]},
                                                              {0,           0,           0,           0,           w_T2[1],     w_T2[1] - 1, w_T2[1],     w_T2[1]},
                                                              {0,           0,           0,           0,           w_T2[2],     w_T2[2],     w_T2[2] - 1, w_T2[2]},
                                                              {0,           0,           0,           0,           w_T2[3],     w_T2[3],     w_T2[3],     w_T2[3] - 1},
                                                              {1,           0,           0,           0,           1,           0,           0,           0},
                                                              {0,           1,           0,           0,           0,           1,           0,           0},
                                                              {0,           0,           1,           0,           0,           0,           1,           0},
                                                              {0,           0,           0,           1,           0,           0,           0,           1}});
        DenseMatrix64F b = new DenseMatrix64F(new double[][] {{0},         {0},         {0},         {0},
                                                              {0},         {0},         {0},         {0},
                                                              {0},         {0},         {0},         {0},
                                                              {scaled[0]}, {scaled[1]}, {scaled[2]}, {scaled[3]}});
        DenseMatrix64F x = new DenseMatrix64F(8, 1);

        solver.setA(A);
        solver.solve(b, x);

        f1u = (int)(x.get(0, 0) + x.get(1, 0) + x.get(2, 0) + x.get(3, 0));
        f2u = (int)(x.get(4, 0) + x.get(5, 0) + x.get(6, 0) + x.get(7, 0));

        // Get interpolated force correct values
        fc1 = SingleTouchCorrect(tri1, uvw1);
        fc2 = SingleTouchCorrect(tri2, uvw2);

        // Correct that shit
        if (f1u < fc1[0]) {
            f1 = (int)(f1u / fc1[0] * 512);
        } else if (f1u > fc1[1]) {
            f1 = (int)(f1u / fc1[1] * 1024);
        } else {
            float w1 = (f1u - fc1[0]) / (fc1[1] - fc1[0]);
            f1 = (int)(w1 * f1u / fc1[1] * 1024 + (1 - w1) * f1u / fc1[0] * 512);
        }

        if (f2u < fc2[0]) {
            f2 = (int)(f2u / fc2[0] * 512);
        } else if (f2u > fc2[1]) {
            f2 = (int)(f2u / fc2[1] * 1024);
        } else {
            float w2 = (f2u - fc2[0]) / (fc2[1] - fc2[0]);
            f2 = (int)(w2 * f2u / fc2[1] * 1024 + (1 - w2) * f2u / fc2[0] * 512);
        }
    }

    private int[] BoundingTriangle(float x, float y) {
        int[] retVal = new int[6];

        int ax, ay, bx, by, cx, cy;

        for (ax = 0; ax < 8; ax++) {
            if (67.5 + ax * 135 > x) {
                ax--;
                break;
            }
        }
        for (ay = 0; ay < 12; ay++) {
            if (80 + ay * 160 > y) {
                ay--;
                break;
            }
        }

        bx = ax + 1;
        by = ay + 1;

        if ((x - 67.5 - ax * 135) * 160 > (y - 80 - ay * 160) * 135) {
            cx = bx;
            cy = ay;
        } else {
            cx = ax;
            cy = by;
        }

        retVal[0] = ax;
        retVal[1] = ay;
        retVal[2] = bx;
        retVal[3] = by;
        retVal[4] = cx;
        retVal[5] = cy;

        return retVal;
    }

    private float[] SingleTouchWeights(int[] tri, float[] uvw) {
        float[] retVal = new float[4];

        retVal[0] = uvw[0] * w_S1[tri[1]][tri[0]] + uvw[1] * w_S1[tri[3]][tri[2]] + uvw[2] * w_S1[tri[5]][tri[4]];
        retVal[1] = uvw[0] * w_S2[tri[1]][tri[0]] + uvw[1] * w_S2[tri[3]][tri[2]] + uvw[2] * w_S2[tri[5]][tri[4]];
        retVal[2] = uvw[0] * w_S3[tri[1]][tri[0]] + uvw[1] * w_S3[tri[3]][tri[2]] + uvw[2] * w_S3[tri[5]][tri[4]];
        retVal[3] = uvw[0] * w_S4[tri[1]][tri[0]] + uvw[1] * w_S4[tri[3]][tri[2]] + uvw[2] * w_S4[tri[5]][tri[4]];

        return retVal;
    }

    private float[] SingleTouchCorrect(int[] tri, float[] uvw) {
        float[] retVal = new float[2];

        retVal[0] = uvw[0] * fc2N[tri[1]][tri[0]] + uvw[1] * fc2N[tri[3]][tri[2]] + uvw[2] * fc2N[tri[5]][tri[4]];
        retVal[1] = uvw[0] * fc4N[tri[1]][tri[0]] + uvw[1] * fc4N[tri[3]][tri[2]] + uvw[2] * fc4N[tri[5]][tri[4]];

        return retVal;
    }

    private float[] BarycentricCoords(int[] tri, float px, float py) {
        int ax = (int)(tri[0] * 135 + 67.5);
        int ay = tri[1] * 160 + 80;
        int bx = (int)(tri[2] * 135 + 67.5);
        int by = tri[3] * 160 + 80;
        int cx = (int)(tri[4] * 135 + 67.5);
        int cy = tri[5] * 160 + 80;
        float v0x, v0y, v1x, v1y, v2x, v2y;
        float d00, d01, d11, d20, d21, denom;
        float[] retVal = new float[3];

        v0x = bx - ax;
        v0y = by - ay;
        v1x = cx - ax;
        v1y = cy - ay;
        v2x = px - ax;
        v2y = py - ay;

        d00 = v0x * v0x + v0y * v0y;
        d01 = v0x * v1x + v0y * v1y;
        d11 = v1x * v1x + v1y * v1y;
        d20 = v2x * v0x + v2y * v0y;
        d21 = v2x * v1x + v2y * v1y;
        denom = d00 * d11 - d01 * d01;

        retVal[1] = (d11 * d20 - d01 * d21) / denom;
        retVal[2] = (d00 * d21 - d01 * d20) / denom;
        retVal[0] = 1.0f - retVal[1] - retVal[2];

        return retVal;
    }
}
