package com.grimfox.gec.hdr;

/**
 * Contains a RGB floating point image
 *
 * Created by Victor Arellano (Ivelate). This code is licensed under the MIT license - anybody can freely use or modify any part of this code and insert any license into it
 * without needing to attribute me for that. That's it!
 */
public class HDRImageRGB extends HDRImage
{
    public HDRImageRGB(int width,int height)
    {
        super(width,height,new float[width*height*3]);
    }
    public HDRImageRGB(int width,int height,float[] data)
    {
        super(width,height,data);
    }
    @Override
    public float getPixelValue(int x, int y, int c) {
        return data[(y*this.getWidth()+x)*3+c];
    }
    @Override
    public void setPixelValue(int x, int y, int c, float val) {
        data[(y*this.getWidth()+x)*3+c]=val;
    }
    @Override
    public int getChannels() {
        return 3;
    }
}
