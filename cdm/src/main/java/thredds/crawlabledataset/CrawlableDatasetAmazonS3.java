package thredds.crawlabledataset;

import thredds.crawlabledataset.s3utils.*;

import java.io.*;
import java.util.*;

public class CrawlableDatasetAmazonS3 extends CrawlableDatasetFile
{
    static private org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CrawlableDatasetAmazonS3.class);

    private final Object configObject;
    private final String path;
    private ThreddsS3Object s3Object = null;
    private static S3Helper helper = new S3Helper();

    public CrawlableDatasetAmazonS3(String path, Object configObject) {
        super(path, configObject);
        this.path = path;
        this.configObject = configObject;
    }

    private CrawlableDatasetAmazonS3(CrawlableDatasetAmazonS3 parent, ThreddsS3Object s3Object) {
        this(S3PathHelper.concat(parent.getPath(), s3Object.key), null);
        this.s3Object = s3Object;
    }

    @Override
    public Object getConfigObject()
    {
        return configObject;
    }

    @Override
    public String getPath()
    {
        return path;
    }

    @Override
    public String getName()
    {
        return S3PathHelper.basename(path);
    }

    @Override
    public File getFile() {
        return helper.getS3File(path);
    }

    @Override
    public CrawlableDataset getParentDataset() {
        return new CrawlableDatasetAmazonS3(S3PathHelper.parent(path), configObject);
    }

    @Override
    public boolean exists()
    {
        return true;
    }

    @Override
    public boolean isCollection() {
        if (null == s3Object)
            return true;
        else
            return s3Object.type == ThreddsS3Object.Type.DIR;
    }

    @Override
    public CrawlableDataset getDescendant(String relativePath) {
        if (relativePath.startsWith("/"))
            throw new IllegalArgumentException("Path must be relative <" + relativePath + ">.");

        ThreddsS3Object obj = new ThreddsS3Object(relativePath, ThreddsS3Object.Type.DIR);
        return new CrawlableDatasetAmazonS3(this, obj);
    }

    @Override
    public List<CrawlableDataset> listDatasets() throws IOException {
        if (!this.isCollection())
        {
            String tmpMsg = "This dataset <" + this.getPath() + "> is not a collection dataset.";
            log.error( "listDatasets(): " + tmpMsg);
            throw new IllegalStateException(tmpMsg);
        }

        List<ThreddsS3Object> listing = helper.listS3Dir(this.path);

        if (listing.isEmpty())
        {
            log.error("listDatasets(): the underlying file [" + this.path + "] exists, but is empty");
            return Collections.emptyList();
        }

        List<CrawlableDataset> list = new ArrayList<CrawlableDataset>();
        for (ThreddsS3Object s3Object : listing)
        {
            CrawlableDatasetAmazonS3 crDs = new CrawlableDatasetAmazonS3(this, s3Object);
            list.add(crDs);
        }

        return list;
    }

    @Override
    public long length() {
        if (null != s3Object)
            return s3Object.size;

        throw new RuntimeException(String.format("Uknown size for object '%s'", path));
    }

    @Override
    public Date lastModified() {
        if (null != s3Object)
            return s3Object.lastModified;

        throw new RuntimeException(String.format("Uknown lastModified for object '%s'", path));
    }
}
