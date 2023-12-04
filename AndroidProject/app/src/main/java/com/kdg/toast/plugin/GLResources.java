package com.kdg.toast.plugin;

import android.opengl.EGLContext;
import android.os.Parcel;
import android.os.Parcelable;

public class GLResources implements Parcelable {
    private int colorTexture;
    private long mainContext;
    private int width;
    private int height;

    public GLResources(int colorTex, long ctxHandle, int width, int height)
    {
        colorTexture = colorTex;
        mainContext = ctxHandle;
        this.width = width;
        this.height = height;
    }

    public int GetColorTex() {return colorTexture;}
    public long GetMainCtx() {
        return mainContext;
    }

    public int GetWidth() {return width;}
    public int GetHeight() {return height;}

    protected GLResources(Parcel in) {
        colorTexture = in.readInt();
        mainContext = in.readLong();
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
        dest.writeInt(colorTexture);
        dest.writeLong(mainContext);
        dest.writeInt(width);
        dest.writeInt(height);
    }
}
