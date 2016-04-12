package thredds.crawlabledataset.s3utils;

import java.util.Date;

public class ThreddsS3Object {
    public final String key;
    public final long size;
    public final Date lastModified;
    public enum Type {
        DIR,
        FILE
    }
    public final Type type;

    public ThreddsS3Object(String key, long size, Date lastModified, Type type)
    {
        this.key = key;
        this.size = size;
        this.lastModified = lastModified;
        this.type = type;
    }

    public ThreddsS3Object(String key, Type type)
    {
        this(key, -1, null, type);
    }
}
