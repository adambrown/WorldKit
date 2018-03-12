/*
 * (C) Copyright 2016-2018 Kai Burjack

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in
 all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 THE SOFTWARE.

 */
package com.grimfox.joml;

//#ifdef __HAS_NIO__
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
//#endif

/**
 * Helper class to do efficient memory operations on all JOML objects, NIO buffers and primitive arrays.
 * This class is used internally throughout JOML, is undocumented and is subject to change.
 * Use with extreme caution!
 * 
 * @author The LWJGL authors
 * @author Kai Burjack
 */
public abstract class MemUtil {
    public static final MemUtil INSTANCE = createInstance();

    private static MemUtil createInstance() {
        return new MemUtilNIO();
    }

//#ifdef __HAS_NIO__
    public abstract void put(Matrix4f m, int offset, FloatBuffer dest);
    public abstract void put(Matrix4f m, int offset, ByteBuffer dest);
    public abstract void putTransposed(Matrix4f m, int offset, FloatBuffer dest);
    public abstract void putTransposed(Matrix4f m, int offset, ByteBuffer dest);
    public abstract void put4x3Transposed(Matrix4f m, int offset, FloatBuffer dest);
    public abstract void put4x3Transposed(Matrix4f m, int offset, ByteBuffer dest);
    public abstract void putTransposed(Matrix3f m, int offset, ByteBuffer dest);
    public abstract void putTransposed(Matrix3f m, int offset, FloatBuffer dest);
    public abstract void put(Matrix3f m, int offset, FloatBuffer dest);
    public abstract void put(Matrix3f m, int offset, ByteBuffer dest);
    public abstract void put(Vector4f src, int offset, FloatBuffer dest);
    public abstract void put(Vector4f src, int offset, ByteBuffer dest);
    public abstract void put(Vector3f src, int offset, FloatBuffer dest);
    public abstract void put(Vector3f src, int offset, ByteBuffer dest);
    public abstract void get(Matrix4f m, int offset, FloatBuffer src);
    public abstract void get(Matrix4f m, int offset, ByteBuffer src);
    public abstract void get(Matrix3f m, int offset, FloatBuffer src);
    public abstract void get(Matrix3f m, int offset, ByteBuffer src);
    public abstract void get(Vector4f dst, int offset, FloatBuffer src);
    public abstract void get(Vector4f dst, int offset, ByteBuffer src);
    public abstract void get(Vector3f dst, int offset, FloatBuffer src);
    public abstract void get(Vector3f dst, int offset, ByteBuffer src);
//#endif

    public abstract void copy(Matrix4f src, Matrix4f dest);
    public abstract void copy(Matrix3f src, Matrix3f dest);
    public abstract void copy(Matrix3f src, Matrix4f dest);
    public abstract void copy(Matrix4f src, Matrix3f dest);
    public abstract void copy3x3(Matrix4f src, Matrix4f dest);
    public abstract void copy3x3(Matrix3f src, Matrix4f dest);
    public abstract void copy4x3(Matrix4f src, Matrix4f dest);
    public abstract void copy(Vector4f src, Vector4f dst);
    public abstract void copy(Quaternionf src, Quaternionf dst);
    public abstract void copy(float[] arr, int off, Matrix4f dest);
    public abstract void copy(float[] arr, int off, Matrix3f dest);
    public abstract void copy(Matrix4f src, float[] dest, int off);
    public abstract void copy(Matrix3f src, float[] dest, int off);
    public abstract void identity(Matrix4f dest);
    public abstract void identity(Matrix3f dest);
    public abstract void identity(Quaternionf dest);
    public abstract void swap(Matrix4f m1, Matrix4f m2);
    public abstract void swap(Matrix3f m1, Matrix3f m2);
    public abstract void zero(Matrix4f dest);
    public abstract void zero(Matrix3f dest);
    public abstract void zero(Vector4f dest);

//#ifdef __HAS_NIO__
    public abstract void putMatrix3f(Quaternionf q, int position, ByteBuffer dest);
    public abstract void putMatrix3f(Quaternionf q, int position, FloatBuffer dest);
    public abstract void putMatrix4f(Quaternionf q, int position, ByteBuffer dest);
    public abstract void putMatrix4f(Quaternionf q, int position, FloatBuffer dest);
    public abstract void putMatrix4x3f(Quaternionf q, int position, ByteBuffer dest);
    public abstract void putMatrix4x3f(Quaternionf q, int position, FloatBuffer dest);
//#endif

    public abstract void set(Matrix4f dest, Vector4f col0, Vector4f col1, Vector4f col2, Vector4f col3);
    public abstract void set(Matrix3f dest, Vector3f col0, Vector3f col1, Vector3f col2);

    public abstract void putColumn0(Matrix4f src, Vector4f dest);
    public abstract void putColumn1(Matrix4f src, Vector4f dest);
    public abstract void putColumn2(Matrix4f src, Vector4f dest);
    public abstract void putColumn3(Matrix4f src, Vector4f dest);

    public abstract void getColumn0(Matrix4f dest, Vector4f src);
    public abstract void getColumn1(Matrix4f dest, Vector4f src);
    public abstract void getColumn2(Matrix4f dest, Vector4f src);
    public abstract void getColumn3(Matrix4f dest, Vector4f src);

    public abstract void broadcast(float c, Vector4f dest);

    public static class MemUtilNIO extends MemUtil {
//#ifdef __HAS_NIO__
        public void put0(Matrix4f m, FloatBuffer dest) {
            dest.put(0,  m.m00());
            dest.put(1,  m.m01());
            dest.put(2,  m.m02());
            dest.put(3,  m.m03());
            dest.put(4,  m.m10());
            dest.put(5,  m.m11());
            dest.put(6,  m.m12());
            dest.put(7,  m.m13());
            dest.put(8,  m.m20());
            dest.put(9,  m.m21());
            dest.put(10, m.m22());
            dest.put(11, m.m23());
            dest.put(12, m.m30());
            dest.put(13, m.m31());
            dest.put(14, m.m32());
            dest.put(15, m.m33());
        }
        public void putN(Matrix4f m, int offset, FloatBuffer dest) {
            dest.put(offset,    m.m00());
            dest.put(offset+1,  m.m01());
            dest.put(offset+2,  m.m02());
            dest.put(offset+3,  m.m03());
            dest.put(offset+4,  m.m10());
            dest.put(offset+5,  m.m11());
            dest.put(offset+6,  m.m12());
            dest.put(offset+7,  m.m13());
            dest.put(offset+8,  m.m20());
            dest.put(offset+9,  m.m21());
            dest.put(offset+10, m.m22());
            dest.put(offset+11, m.m23());
            dest.put(offset+12, m.m30());
            dest.put(offset+13, m.m31());
            dest.put(offset+14, m.m32());
            dest.put(offset+15, m.m33());
        }
        public void put(Matrix4f m, int offset, FloatBuffer dest) {
            if (offset == 0)
                put0(m, dest);
            else
                putN(m, offset, dest);
        }

        public void put0(Matrix4f m, ByteBuffer dest) {
            dest.putFloat(0,  m.m00());
            dest.putFloat(4,  m.m01());
            dest.putFloat(8,  m.m02());
            dest.putFloat(12, m.m03());
            dest.putFloat(16, m.m10());
            dest.putFloat(20, m.m11());
            dest.putFloat(24, m.m12());
            dest.putFloat(28, m.m13());
            dest.putFloat(32, m.m20());
            dest.putFloat(36, m.m21());
            dest.putFloat(40, m.m22());
            dest.putFloat(44, m.m23());
            dest.putFloat(48, m.m30());
            dest.putFloat(52, m.m31());
            dest.putFloat(56, m.m32());
            dest.putFloat(60, m.m33());
        }
        private void putN(Matrix4f m, int offset, ByteBuffer dest) {
            dest.putFloat(offset,    m.m00());
            dest.putFloat(offset+4,  m.m01());
            dest.putFloat(offset+8,  m.m02());
            dest.putFloat(offset+12, m.m03());
            dest.putFloat(offset+16, m.m10());
            dest.putFloat(offset+20, m.m11());
            dest.putFloat(offset+24, m.m12());
            dest.putFloat(offset+28, m.m13());
            dest.putFloat(offset+32, m.m20());
            dest.putFloat(offset+36, m.m21());
            dest.putFloat(offset+40, m.m22());
            dest.putFloat(offset+44, m.m23());
            dest.putFloat(offset+48, m.m30());
            dest.putFloat(offset+52, m.m31());
            dest.putFloat(offset+56, m.m32());
            dest.putFloat(offset+60, m.m33());
        }
        public void put(Matrix4f m, int offset, ByteBuffer dest) {
            if (offset == 0)
                put0(m, dest);
            else
                putN(m, offset, dest);
        }

        public void putTransposed(Matrix4f m, int offset, FloatBuffer dest) {
            dest.put(offset,    m.m00());
            dest.put(offset+1,  m.m10());
            dest.put(offset+2,  m.m20());
            dest.put(offset+3,  m.m30());
            dest.put(offset+4,  m.m01());
            dest.put(offset+5,  m.m11());
            dest.put(offset+6,  m.m21());
            dest.put(offset+7,  m.m31());
            dest.put(offset+8,  m.m02());
            dest.put(offset+9,  m.m12());
            dest.put(offset+10, m.m22());
            dest.put(offset+11, m.m32());
            dest.put(offset+12, m.m03());
            dest.put(offset+13, m.m13());
            dest.put(offset+14, m.m23());
            dest.put(offset+15, m.m33());
        }

        public void putTransposed(Matrix4f m, int offset, ByteBuffer dest) {
            dest.putFloat(offset,    m.m00());
            dest.putFloat(offset+4,  m.m10());
            dest.putFloat(offset+8,  m.m20());
            dest.putFloat(offset+12, m.m30());
            dest.putFloat(offset+16, m.m01());
            dest.putFloat(offset+20, m.m11());
            dest.putFloat(offset+24, m.m21());
            dest.putFloat(offset+28, m.m31());
            dest.putFloat(offset+32, m.m02());
            dest.putFloat(offset+36, m.m12());
            dest.putFloat(offset+40, m.m22());
            dest.putFloat(offset+44, m.m32());
            dest.putFloat(offset+48, m.m03());
            dest.putFloat(offset+52, m.m13());
            dest.putFloat(offset+56, m.m23());
            dest.putFloat(offset+60, m.m33());
        }

        public void put4x3Transposed(Matrix4f m, int offset, FloatBuffer dest) {
            dest.put(offset,    m.m00());
            dest.put(offset+1,  m.m10());
            dest.put(offset+2,  m.m20());
            dest.put(offset+3,  m.m30());
            dest.put(offset+4,  m.m01());
            dest.put(offset+5,  m.m11());
            dest.put(offset+6,  m.m21());
            dest.put(offset+7,  m.m31());
            dest.put(offset+8,  m.m02());
            dest.put(offset+9,  m.m12());
            dest.put(offset+10, m.m22());
            dest.put(offset+11, m.m32());
        }

        public void put4x3Transposed(Matrix4f m, int offset, ByteBuffer dest) {
            dest.putFloat(offset,    m.m00());
            dest.putFloat(offset+4,  m.m10());
            dest.putFloat(offset+8,  m.m20());
            dest.putFloat(offset+12, m.m30());
            dest.putFloat(offset+16, m.m01());
            dest.putFloat(offset+20, m.m11());
            dest.putFloat(offset+24, m.m21());
            dest.putFloat(offset+28, m.m31());
            dest.putFloat(offset+32, m.m02());
            dest.putFloat(offset+36, m.m12());
            dest.putFloat(offset+40, m.m22());
            dest.putFloat(offset+44, m.m32());
        }

        public void putTransposed(Matrix3f m, int offset, FloatBuffer dest) {
            dest.put(offset,   m.m00());
            dest.put(offset+1, m.m10());
            dest.put(offset+2, m.m20());
            dest.put(offset+3, m.m01());
            dest.put(offset+4, m.m11());
            dest.put(offset+5, m.m21());
            dest.put(offset+6, m.m02());
            dest.put(offset+7, m.m12());
            dest.put(offset+8, m.m22());
        }

        public void putTransposed(Matrix3f m, int offset, ByteBuffer dest) {
            dest.putFloat(offset,    m.m00());
            dest.putFloat(offset+4,  m.m10());
            dest.putFloat(offset+8,  m.m20());
            dest.putFloat(offset+12, m.m01());
            dest.putFloat(offset+16, m.m11());
            dest.putFloat(offset+20, m.m21());
            dest.putFloat(offset+24, m.m02());
            dest.putFloat(offset+28, m.m12());
            dest.putFloat(offset+32, m.m22());
        }

        public void put(Matrix3f m, int offset, FloatBuffer dest) {
            dest.put(offset,   m.m00());
            dest.put(offset+1, m.m01());
            dest.put(offset+2, m.m02());
            dest.put(offset+3, m.m10());
            dest.put(offset+4, m.m11());
            dest.put(offset+5, m.m12());
            dest.put(offset+6, m.m20());
            dest.put(offset+7, m.m21());
            dest.put(offset+8, m.m22());
        }

        public void put(Matrix3f m, int offset, ByteBuffer dest) {
            dest.putFloat(offset,    m.m00());
            dest.putFloat(offset+4,  m.m01());
            dest.putFloat(offset+8,  m.m02());
            dest.putFloat(offset+12, m.m10());
            dest.putFloat(offset+16, m.m11());
            dest.putFloat(offset+20, m.m12());
            dest.putFloat(offset+24, m.m20());
            dest.putFloat(offset+28, m.m21());
            dest.putFloat(offset+32, m.m22());
        }

        public void put(Vector4f src, int offset, FloatBuffer dest) {
            dest.put(offset,   src.x);
            dest.put(offset+1, src.y);
            dest.put(offset+2, src.z);
            dest.put(offset+3, src.w);
        }

        public void put(Vector4f src, int offset, ByteBuffer dest) {
            dest.putFloat(offset,    src.x);
            dest.putFloat(offset+4,  src.y);
            dest.putFloat(offset+8,  src.z);
            dest.putFloat(offset+12, src.w);
        }

        public void put(Vector3f src, int offset, FloatBuffer dest) {
            dest.put(offset,   src.x);
            dest.put(offset+1, src.y);
            dest.put(offset+2, src.z);
        }

        public void put(Vector3f src, int offset, ByteBuffer dest) {
            dest.putFloat(offset,   src.x);
            dest.putFloat(offset+4, src.y);
            dest.putFloat(offset+8, src.z);
        }

        public void get(Matrix4f m, int offset, FloatBuffer src) {
            m._m00(src.get(offset));
            m._m01(src.get(offset+1));
            m._m02(src.get(offset+2));
            m._m03(src.get(offset+3));
            m._m10(src.get(offset+4));
            m._m11(src.get(offset+5));
            m._m12(src.get(offset+6));
            m._m13(src.get(offset+7));
            m._m20(src.get(offset+8));
            m._m21(src.get(offset+9));
            m._m22(src.get(offset+10));
            m._m23(src.get(offset+11));
            m._m30(src.get(offset+12));
            m._m31(src.get(offset+13));
            m._m32(src.get(offset+14));
            m._m33(src.get(offset+15));
        }

        public void get(Matrix4f m, int offset, ByteBuffer src) {
            m._m00(src.getFloat(offset));
            m._m01(src.getFloat(offset+4));
            m._m02(src.getFloat(offset+8));
            m._m03(src.getFloat(offset+12));
            m._m10(src.getFloat(offset+16));
            m._m11(src.getFloat(offset+20));
            m._m12(src.getFloat(offset+24));
            m._m13(src.getFloat(offset+28));
            m._m20(src.getFloat(offset+32));
            m._m21(src.getFloat(offset+36));
            m._m22(src.getFloat(offset+40));
            m._m23(src.getFloat(offset+44));
            m._m30(src.getFloat(offset+48));
            m._m31(src.getFloat(offset+52));
            m._m32(src.getFloat(offset+56));
            m._m33(src.getFloat(offset+60));
        }

        public void get(Matrix3f m, int offset, FloatBuffer src) {
            m._m00(src.get(offset));
            m._m01(src.get(offset+1));
            m._m02(src.get(offset+2));
            m._m10(src.get(offset+3));
            m._m11(src.get(offset+4));
            m._m12(src.get(offset+5));
            m._m20(src.get(offset+6));
            m._m21(src.get(offset+7));
            m._m22(src.get(offset+8));
        }

        public void get(Matrix3f m, int offset, ByteBuffer src) {
            m._m00(src.getFloat(offset));
            m._m01(src.getFloat(offset+4));
            m._m02(src.getFloat(offset+8));
            m._m10(src.getFloat(offset+12));
            m._m11(src.getFloat(offset+16));
            m._m12(src.getFloat(offset+20));
            m._m20(src.getFloat(offset+24));
            m._m21(src.getFloat(offset+28));
            m._m22(src.getFloat(offset+32));
        }

        public void get(Vector4f dst, int offset, FloatBuffer src) {
            dst.x = src.get(offset);
            dst.y = src.get(offset+1);
            dst.z = src.get(offset+2);
            dst.w = src.get(offset+3);
        }

        public void get(Vector4f dst, int offset, ByteBuffer src) {
            dst.x = src.getFloat(offset);
            dst.y = src.getFloat(offset+4);
            dst.z = src.getFloat(offset+8);
            dst.w = src.getFloat(offset+12);
        }

        public void get(Vector3f dst, int offset, FloatBuffer src) {
            dst.x = src.get(offset);
            dst.y = src.get(offset+1);
            dst.z = src.get(offset+2);
        }

        public void get(Vector3f dst, int offset, ByteBuffer src) {
            dst.x = src.getFloat(offset);
            dst.y = src.getFloat(offset+4);
            dst.z = src.getFloat(offset+8);
        }

//#endif

        public void copy(Matrix4f src, Matrix4f dest) {
            dest._m00(src.m00());
            dest._m01(src.m01());
            dest._m02(src.m02());
            dest._m03(src.m03());
            dest._m10(src.m10());
            dest._m11(src.m11());
            dest._m12(src.m12());
            dest._m13(src.m13());
            dest._m20(src.m20());
            dest._m21(src.m21());
            dest._m22(src.m22());
            dest._m23(src.m23());
            dest._m30(src.m30());
            dest._m31(src.m31());
            dest._m32(src.m32());
            dest._m33(src.m33());
        }

        public void copy(Matrix3f src, Matrix4f dest) {
            dest._m00(src.m00());
            dest._m01(src.m01());
            dest._m02(src.m02());
            dest._m03(0.0f);
            dest._m10(src.m10());
            dest._m11(src.m11());
            dest._m12(src.m12());
            dest._m13(0.0f);
            dest._m20(src.m20());
            dest._m21(src.m21());
            dest._m22(src.m22());
            dest._m23(0.0f);
            dest._m30(0.0f);
            dest._m31(0.0f);
            dest._m32(0.0f);
            dest._m33(1.0f);
        }

        public void copy(Matrix4f src, Matrix3f dest) {
            dest._m00(src.m00());
            dest._m01(src.m01());
            dest._m02(src.m02());
            dest._m10(src.m10());
            dest._m11(src.m11());
            dest._m12(src.m12());
            dest._m20(src.m20());
            dest._m21(src.m21());
            dest._m22(src.m22());
        }

        public void copy3x3(Matrix4f src, Matrix4f dest) {
            dest._m00(src.m00());
            dest._m01(src.m01());
            dest._m02(src.m02());
            dest._m10(src.m10());
            dest._m11(src.m11());
            dest._m12(src.m12());
            dest._m20(src.m20());
            dest._m21(src.m21());
            dest._m22(src.m22());
        }

        public void copy3x3(Matrix3f src, Matrix4f dest) {
            dest._m00(src.m00());
            dest._m01(src.m01());
            dest._m02(src.m02());
            dest._m10(src.m10());
            dest._m11(src.m11());
            dest._m12(src.m12());
            dest._m20(src.m20());
            dest._m21(src.m21());
            dest._m22(src.m22());
        }

        public void copy(Vector4f src, Vector4f dst) {
            dst.x = src.x;
            dst.y = src.y;
            dst.z = src.z;
            dst.w = src.w;
        }

        public void copy(Quaternionf src, Quaternionf dst) {
            dst.x = src.x;
            dst.y = src.y;
            dst.z = src.z;
            dst.w = src.w;
        }

        public void copy4x3(Matrix4f src, Matrix4f dest) {
            dest._m00(src.m00());
            dest._m01(src.m01());
            dest._m02(src.m02());
            dest._m10(src.m10());
            dest._m11(src.m11());
            dest._m12(src.m12());
            dest._m20(src.m20());
            dest._m21(src.m21());
            dest._m22(src.m22());
            dest._m30(src.m30());
            dest._m31(src.m31());
            dest._m32(src.m32());
        }

        public void copy(Matrix3f src, Matrix3f dest) {
            dest._m00(src.m00());
            dest._m01(src.m01());
            dest._m02(src.m02());
            dest._m10(src.m10());
            dest._m11(src.m11());
            dest._m12(src.m12());
            dest._m20(src.m20());
            dest._m21(src.m21());
            dest._m22(src.m22());
        }

        public void copy(float[] arr, int off, Matrix4f dest) {
            dest._m00(arr[off]);
            dest._m01(arr[off+1]);
            dest._m02(arr[off+2]);
            dest._m03(arr[off+3]);
            dest._m10(arr[off+4]);
            dest._m11(arr[off+5]);
            dest._m12(arr[off+6]);
            dest._m13(arr[off+7]);
            dest._m20(arr[off+8]);
            dest._m21(arr[off+9]);
            dest._m22(arr[off+10]);
            dest._m23(arr[off+11]);
            dest._m30(arr[off+12]);
            dest._m31(arr[off+13]);
            dest._m32(arr[off+14]);
            dest._m33(arr[off+15]);
        }

        public void copy(float[] arr, int off, Matrix3f dest) {
            dest._m00(arr[off]);
            dest._m01(arr[off+1]);
            dest._m02(arr[off+2]);
            dest._m10(arr[off+3]);
            dest._m11(arr[off+4]);
            dest._m12(arr[off+5]);
            dest._m20(arr[off+6]);
            dest._m21(arr[off+7]);
            dest._m22(arr[off+8]);
        }

        public void copy(Matrix4f src, float[] dest, int off) {
            dest[off]  = src.m00();
            dest[off+1]  = src.m01();
            dest[off+2]  = src.m02();
            dest[off+3]  = src.m03();
            dest[off+4]  = src.m10();
            dest[off+5]  = src.m11();
            dest[off+6]  = src.m12();
            dest[off+7]  = src.m13();
            dest[off+8]  = src.m20();
            dest[off+9]  = src.m21();
            dest[off+10] = src.m22();
            dest[off+11] = src.m23();
            dest[off+12] = src.m30();
            dest[off+13] = src.m31();
            dest[off+14] = src.m32();
            dest[off+15] = src.m33();
        }

        public void copy(Matrix3f src, float[] dest, int off) {
            dest[off] = src.m00();
            dest[off+1] = src.m01();
            dest[off+2] = src.m02();
            dest[off+3] = src.m10();
            dest[off+4] = src.m11();
            dest[off+5] = src.m12();
            dest[off+6] = src.m20();
            dest[off+7] = src.m21();
            dest[off+8] = src.m22();
        }

        public void identity(Matrix4f dest) {
            dest._m00(1.0f);
            dest._m01(0.0f);
            dest._m02(0.0f);
            dest._m03(0.0f);
            dest._m10(0.0f);
            dest._m11(1.0f);
            dest._m12(0.0f);
            dest._m13(0.0f);
            dest._m20(0.0f);
            dest._m21(0.0f);
            dest._m22(1.0f);
            dest._m23(0.0f);
            dest._m30(0.0f);
            dest._m31(0.0f);
            dest._m32(0.0f);
            dest._m33(1.0f);
        }

        public void identity(Matrix3f dest) {
            dest._m00(1.0f);
            dest._m01(0.0f);
            dest._m02(0.0f);
            dest._m10(0.0f);
            dest._m11(1.0f);
            dest._m12(0.0f);
            dest._m20(0.0f);
            dest._m21(0.0f);
            dest._m22(1.0f);
        }

        public void identity(Quaternionf dest) {
            dest.x = 0.0f;
            dest.y = 0.0f;
            dest.z = 0.0f;
            dest.w = 1.0f;
        }

        public void swap(Matrix4f m1, Matrix4f m2) {
            float tmp;
            tmp = m1.m00(); m1._m00(m2.m00()); m2._m00(tmp);
            tmp = m1.m01(); m1._m01(m2.m01()); m2._m01(tmp);
            tmp = m1.m02(); m1._m02(m2.m02()); m2._m02(tmp);
            tmp = m1.m03(); m1._m03(m2.m03()); m2._m03(tmp);
            tmp = m1.m10(); m1._m10(m2.m10()); m2._m10(tmp);
            tmp = m1.m11(); m1._m11(m2.m11()); m2._m11(tmp);
            tmp = m1.m12(); m1._m12(m2.m12()); m2._m12(tmp);
            tmp = m1.m13(); m1._m13(m2.m13()); m2._m13(tmp);
            tmp = m1.m20(); m1._m20(m2.m20()); m2._m20(tmp);
            tmp = m1.m21(); m1._m21(m2.m21()); m2._m21(tmp);
            tmp = m1.m22(); m1._m22(m2.m22()); m2._m22(tmp);
            tmp = m1.m23(); m1._m23(m2.m23()); m2._m23(tmp);
            tmp = m1.m30(); m1._m30(m2.m30()); m2._m30(tmp);
            tmp = m1.m31(); m1._m31(m2.m31()); m2._m31(tmp);
            tmp = m1.m32(); m1._m32(m2.m32()); m2._m32(tmp);
            tmp = m1.m33(); m1._m33(m2.m33()); m2._m33(tmp);
        }

        public void swap(Matrix3f m1, Matrix3f m2) {
            float tmp;
            tmp = m1.m00(); m1._m00(m2.m00()); m2._m00(tmp);
            tmp = m1.m01(); m1._m01(m2.m01()); m2._m01(tmp);
            tmp = m1.m02(); m1._m02(m2.m02()); m2._m02(tmp);
            tmp = m1.m10(); m1._m10(m2.m10()); m2._m10(tmp);
            tmp = m1.m11(); m1._m11(m2.m11()); m2._m11(tmp);
            tmp = m1.m12(); m1._m12(m2.m12()); m2._m12(tmp);
            tmp = m1.m20(); m1._m20(m2.m20()); m2._m20(tmp);
            tmp = m1.m21(); m1._m21(m2.m21()); m2._m21(tmp);
            tmp = m1.m22(); m1._m22(m2.m22()); m2._m22(tmp);
        }

        public void zero(Matrix4f dest) {
            dest._m00(0.0f);
            dest._m01(0.0f);
            dest._m02(0.0f);
            dest._m03(0.0f);
            dest._m10(0.0f);
            dest._m11(0.0f);
            dest._m12(0.0f);
            dest._m13(0.0f);
            dest._m20(0.0f);
            dest._m21(0.0f);
            dest._m22(0.0f);
            dest._m23(0.0f);
            dest._m30(0.0f);
            dest._m31(0.0f);
            dest._m32(0.0f);
            dest._m33(0.0f);
        }

        public void zero(Matrix3f dest) {
            dest._m00(0.0f);
            dest._m01(0.0f);
            dest._m02(0.0f);
            dest._m10(0.0f);
            dest._m11(0.0f);
            dest._m12(0.0f);
            dest._m20(0.0f);
            dest._m21(0.0f);
            dest._m22(0.0f);
        }

        public void zero(Vector4f dest) {
            dest.x = 0.0f;
            dest.y = 0.0f;
            dest.z = 0.0f;
            dest.w = 0.0f;
        }

//#ifdef __HAS_NIO__
        public void putMatrix3f(Quaternionf q, int position, ByteBuffer dest) {
            float w2 = q.w * q.w;
            float x2 = q.x * q.x;
            float y2 = q.y * q.y;
            float z2 = q.z * q.z;
            float zw = q.z * q.w;
            float xy = q.x * q.y;
            float xz = q.x * q.z;
            float yw = q.y * q.w;
            float yz = q.y * q.z;
            float xw = q.x * q.w;
            dest.putFloat(position, w2 + x2 - z2 - y2);
            dest.putFloat(position + 4, xy + zw + zw + xy);
            dest.putFloat(position + 8, xz - yw + xz - yw);
            dest.putFloat(position + 12, -zw + xy - zw + xy);
            dest.putFloat(position + 16, y2 - z2 + w2 - x2);
            dest.putFloat(position + 20, yz + yz + xw + xw);
            dest.putFloat(position + 24, yw + xz + xz + yw);
            dest.putFloat(position + 28, yz + yz - xw - xw);
            dest.putFloat(position + 32, z2 - y2 - x2 + w2);
        }

        public void putMatrix3f(Quaternionf q, int position, FloatBuffer dest) {
            float w2 = q.w * q.w;
            float x2 = q.x * q.x;
            float y2 = q.y * q.y;
            float z2 = q.z * q.z;
            float zw = q.z * q.w;
            float xy = q.x * q.y;
            float xz = q.x * q.z;
            float yw = q.y * q.w;
            float yz = q.y * q.z;
            float xw = q.x * q.w;
            dest.put(position, w2 + x2 - z2 - y2);
            dest.put(position + 1, xy + zw + zw + xy);
            dest.put(position + 2, xz - yw + xz - yw);
            dest.put(position + 3, -zw + xy - zw + xy);
            dest.put(position + 4, y2 - z2 + w2 - x2);
            dest.put(position + 5, yz + yz + xw + xw);
            dest.put(position + 6, yw + xz + xz + yw);
            dest.put(position + 7, yz + yz - xw - xw);
            dest.put(position + 8, z2 - y2 - x2 + w2);
        }

        public void putMatrix4f(Quaternionf q, int position, ByteBuffer dest) {
            float w2 = q.w * q.w;
            float x2 = q.x * q.x;
            float y2 = q.y * q.y;
            float z2 = q.z * q.z;
            float zw = q.z * q.w;
            float xy = q.x * q.y;
            float xz = q.x * q.z;
            float yw = q.y * q.w;
            float yz = q.y * q.z;
            float xw = q.x * q.w;
            dest.putFloat(position, w2 + x2 - z2 - y2);
            dest.putFloat(position + 4, xy + zw + zw + xy);
            dest.putFloat(position + 8, xz - yw + xz - yw);
            dest.putFloat(position + 12, 0.0f);
            dest.putFloat(position + 16, -zw + xy - zw + xy);
            dest.putFloat(position + 20, y2 - z2 + w2 - x2);
            dest.putFloat(position + 24, yz + yz + xw + xw);
            dest.putFloat(position + 28, 0.0f);
            dest.putFloat(position + 32, yw + xz + xz + yw);
            dest.putFloat(position + 36, yz + yz - xw - xw);
            dest.putFloat(position + 40, z2 - y2 - x2 + w2);
            dest.putFloat(position + 44, 0.0f);
            dest.putLong(position + 48, 0L);
            dest.putLong(position + 56, 0x3F80000000000000L);
        }

        public void putMatrix4f(Quaternionf q, int position, FloatBuffer dest) {
            float w2 = q.w * q.w;
            float x2 = q.x * q.x;
            float y2 = q.y * q.y;
            float z2 = q.z * q.z;
            float zw = q.z * q.w;
            float xy = q.x * q.y;
            float xz = q.x * q.z;
            float yw = q.y * q.w;
            float yz = q.y * q.z;
            float xw = q.x * q.w;
            dest.put(position, w2 + x2 - z2 - y2);
            dest.put(position + 1, xy + zw + zw + xy);
            dest.put(position + 2, xz - yw + xz - yw);
            dest.put(position + 3, 0.0f);
            dest.put(position + 4, -zw + xy - zw + xy);
            dest.put(position + 5, y2 - z2 + w2 - x2);
            dest.put(position + 6, yz + yz + xw + xw);
            dest.put(position + 7, 0.0f);
            dest.put(position + 8, yw + xz + xz + yw);
            dest.put(position + 9, yz + yz - xw - xw);
            dest.put(position + 10, z2 - y2 - x2 + w2);
            dest.put(position + 11, 0.0f);
            dest.put(position + 12, 0.0f);
            dest.put(position + 13, 0.0f);
            dest.put(position + 14, 0.0f);
            dest.put(position + 15, 1.0f);
        }

        public void putMatrix4x3f(Quaternionf q, int position, ByteBuffer dest) {
            float w2 = q.w * q.w;
            float x2 = q.x * q.x;
            float y2 = q.y * q.y;
            float z2 = q.z * q.z;
            float zw = q.z * q.w;
            float xy = q.x * q.y;
            float xz = q.x * q.z;
            float yw = q.y * q.w;
            float yz = q.y * q.z;
            float xw = q.x * q.w;
            dest.putFloat(position, w2 + x2 - z2 - y2);
            dest.putFloat(position + 4, xy + zw + zw + xy);
            dest.putFloat(position + 8, xz - yw + xz - yw);
            dest.putFloat(position + 12, -zw + xy - zw + xy);
            dest.putFloat(position + 16, y2 - z2 + w2 - x2);
            dest.putFloat(position + 20, yz + yz + xw + xw);
            dest.putFloat(position + 24, yw + xz + xz + yw);
            dest.putFloat(position + 28, yz + yz - xw - xw);
            dest.putFloat(position + 32, z2 - y2 - x2 + w2);
            dest.putLong(position + 36, 0L);
            dest.putFloat(position + 44, 0.0f);
        }

        public void putMatrix4x3f(Quaternionf q, int position, FloatBuffer dest) {
            float w2 = q.w * q.w;
            float x2 = q.x * q.x;
            float y2 = q.y * q.y;
            float z2 = q.z * q.z;
            float zw = q.z * q.w;
            float xy = q.x * q.y;
            float xz = q.x * q.z;
            float yw = q.y * q.w;
            float yz = q.y * q.z;
            float xw = q.x * q.w;
            dest.put(position, w2 + x2 - z2 - y2);
            dest.put(position + 1, xy + zw + zw + xy);
            dest.put(position + 2, xz - yw + xz - yw);
            dest.put(position + 3, -zw + xy - zw + xy);
            dest.put(position + 4, y2 - z2 + w2 - x2);
            dest.put(position + 5, yz + yz + xw + xw);
            dest.put(position + 6, yw + xz + xz + yw);
            dest.put(position + 7, yz + yz - xw - xw);
            dest.put(position + 8, z2 - y2 - x2 + w2);
            dest.put(position + 9, 0.0f);
            dest.put(position + 10, 0.0f);
            dest.put(position + 11, 0.0f);
        }
//#endif

        public void set(Matrix4f m, Vector4f col0, Vector4f col1, Vector4f col2, Vector4f col3) {
            m._m00(col0.x);
            m._m01(col0.y);
            m._m02(col0.z);
            m._m03(col0.w);
            m._m10(col1.x);
            m._m11(col1.y);
            m._m12(col1.z);
            m._m13(col1.w);
            m._m20(col2.x);
            m._m21(col2.y);
            m._m22(col2.z);
            m._m23(col2.w);
            m._m30(col3.x);
            m._m31(col3.y);
            m._m32(col3.z);
            m._m33(col3.w);
        }

        public void set(Matrix3f m, Vector3f col0, Vector3f col1, Vector3f col2) {
            m._m00(col0.x);
            m._m01(col0.y);
            m._m02(col0.z);
            m._m10(col1.x);
            m._m11(col1.y);
            m._m12(col1.z);
            m._m20(col2.x);
            m._m21(col2.y);
            m._m22(col2.z);
        }

        public void putColumn0(Matrix4f src, Vector4f dest) {
            dest.x = src.m00();
            dest.y = src.m01();
            dest.z = src.m02();
            dest.w = src.m03();
        }

        public void putColumn1(Matrix4f src, Vector4f dest) {
            dest.x = src.m10();
            dest.y = src.m11();
            dest.z = src.m12();
            dest.w = src.m13();
        }

        public void putColumn2(Matrix4f src, Vector4f dest) {
            dest.x = src.m20();
            dest.y = src.m21();
            dest.z = src.m22();
            dest.w = src.m23();
        }

        public void putColumn3(Matrix4f src, Vector4f dest) {
            dest.x = src.m30();
            dest.y = src.m31();
            dest.z = src.m32();
            dest.w = src.m33();
        }

        public void getColumn0(Matrix4f dest, Vector4f src) {
            dest._m00(src.x);
            dest._m01(src.y);
            dest._m02(src.z);
            dest._m03(src.w);
        }

        public void getColumn1(Matrix4f dest, Vector4f src) {
            dest._m10(src.x);
            dest._m11(src.y);
            dest._m12(src.z);
            dest._m13(src.w);
        }

        public void getColumn2(Matrix4f dest, Vector4f src) {
            dest._m20(src.x);
            dest._m21(src.y);
            dest._m22(src.z);
            dest._m23(src.w);
        }

        public void getColumn3(Matrix4f dest, Vector4f src) {
            dest._m30(src.x);
            dest._m31(src.y);
            dest._m32(src.z);
            dest._m33(src.w);
        }

        public void broadcast(float c, Vector4f dest) {
            dest.x = c;
            dest.y = c;
            dest.z = c;
            dest.w = c;
        }
    }
}
