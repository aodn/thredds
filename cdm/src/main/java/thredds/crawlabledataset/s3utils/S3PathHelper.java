package thredds.crawlabledataset.s3utils;

import java.io.File;
import java.util.List;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.*;

public class S3PathHelper {
    private static String S3_PREFIX = "s3://";
    private static String S3_DELIMITER = "/";

    public static String concat(String parent, String child)
    {
        if (child.isEmpty())
            return parent;
        else
            return parent + S3_DELIMITER + removeTrailingSlash(child);
    }

    public static String parent(String uri)
    {
        int delim = uri.lastIndexOf(S3_DELIMITER);
        return uri.substring(0, delim);
    }

    public static String delimiter() { return S3_DELIMITER; }

    public static String basename(String uri)
    {
        return new File(uri).getName();
    }

    public static String stripPrefix(String key, String prefix)
    {
        return key.replaceFirst(prefix, "");
    }

    public static String removeTrailingSlash(String str) {
        if (str.endsWith(S3_DELIMITER))
            str = str.substring(0, str.length() - 1);

        return str;
    }

    public static String[] s3UriParts(String uri) throws Exception {
        if (uri.startsWith(S3_PREFIX))
        {
            uri = uri.replaceFirst(S3_PREFIX, "");
            String[] parts = new String[2];
            int delim = uri.indexOf(S3_DELIMITER);
            parts[0] = uri.substring(0, delim);
            parts[1] = uri.substring(Math.min(delim + 1, uri.length()), uri.length());
            return parts;
        }
        else
            throw new IllegalArgumentException(String.format("Not a valid s3 uri: %s", uri));
    }

    public static AmazonS3Client getS3Client() {
        // Use HTTP, it's much faster
        AmazonS3Client s3Client = new AmazonS3Client();
        s3Client.setEndpoint("http://s3.amazonaws.com");
        return  s3Client;
    }
}
