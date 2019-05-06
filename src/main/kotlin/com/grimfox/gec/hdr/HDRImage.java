package com.grimfox.gec.hdr;

/**
 * Contains a floating point 2D image with a variable amount of channels
 *
 * Created by Victor Arellano (Ivelate). This code is licensed under the MIT license - anybody can freely use or modify any part of this code and insert any license into it
 * without needing to attribute me for that. That's it!
 */
public abstract class HDRImage
{
    protected float[] data;
    private int width;
    private int height;

    public HDRImage(int width,int height,float[] data)
    {
        this.data=data;
        this.width=width;
        this.height=height;
    }

    public abstract float getPixelValue(int x,int y,int c);

    public abstract void setPixelValue(int x,int y,int c,float val);

    public float[] getInternalData()
    {
        return this.data;
    }

    public int getWidth()
    {
        return this.width;
    }
    public int getHeight()
    {
        return this.height;
    }

    public abstract int getChannels();
}