package com.kdg.toast.plugin;

import android.hardware.HardwareBuffer;
import android.os.Parcel;
import android.os.Parcelable;

public class GLResources implements Parcelable {
    private HardwareBuffer colorBuffer;
    private int width;
    private int height;

    public GLResources(HardwareBuffer hwBuffer, int width, int height)
    {
        colorBuffer = hwBuffer;
        this.width = width;
        this.height = height;
    }

    public HardwareBuffer GetColorBuffer() {return colorBuffer;}

    public int GetWidth() {return width;}
    public int GetHeight() {return height;}

    protected GLResources(Parcel in) {
        colorBuffer = in.readParcelable(HardwareBuffer.class.getClassLoader());
        // colorBuffer = (HardwareBuffer) in.readValue(HardwareBuffer.class.getClassLoader());
        width = in.readInt();
        height = in.readInt();
    }

    public static final Creator<GLResources> CREATOR = new Creator<GLResources>() {
        @Override
        public GLResources createFromParcel(Parcel in) {
            return new GLResources(in);
        }

        @Override
        public GLResources[] newArray(int size) {
            return new GLResources[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(colorBuffer, 0);
        //dest.writeValue(colorBuffer);
        dest.writeInt(width);
        dest.writeInt(height);
        dest.marshall();
    }
}
