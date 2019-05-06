package com.grimfox.gec.hdr;

/**
 * Contains a grayscale floating point image
 *
 * Created by Victor Arellano (Ivelate). This code is licensed under the MIT license - anybody can freely use or modify any part of this code and insert any license into it
 * without needing to attribute me for that. That's it!
 */
public class HDRImageGrayscale extends HDRImage
{
    public HDRImageGrayscale(int width,int height)
    {
        super(width,height,new float[width*height]);
    }
    public HDRImageGrayscale(int width,int height,float[] data)
    {
        super(width,height,data);
    }
    @Override
    public float getPixelValue(int x, int y, int c) {
        return data[y*this.getWidth()+x];
    }
    @Override
    public void setPixelValue(int x, int y, int c, float val) {
        data[y*this.getWidth()+x]=val;
    }
    @Override
    public int getChannels() {
        return 1;
    }

}