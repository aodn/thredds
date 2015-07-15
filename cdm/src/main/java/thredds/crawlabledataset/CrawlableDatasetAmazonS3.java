package thredds.crawlabledataset;

import java.io.IOException;
import java.util.Date;
import java.util.List;

public class CrawlableDatasetAmazonS3 implements CrawlableDataset {

    static {
        System.out.println("=======================================");
        System.out.println(" > CrawlableDatasetAmazonS3          < ");
        System.out.println("=======================================");
    }

    Object configObject;
    String path;

    public CrawlableDatasetAmazonS3(String path, Object configObject) {

        this.path = path;
        this.configObject = configObject;

        System.out.println("path = " + path);
        System.out.println("configObject = " + configObject);
    }

    @Override
    public Object getConfigObject() {
        return configObject;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public String getName() {
        throw new RuntimeException("getName() not implemented");
    }

    @Override
    public CrawlableDataset getParentDataset() {
        throw new RuntimeException("getParentDataset() not implemented");
    }

    @Override
    public boolean exists() {
        throw new RuntimeException("exists() not implemented");
    }

    @Override
    public boolean isCollection() {
        throw new RuntimeException("isCollection() not implemented");
    }

    @Override
    public CrawlableDataset getDescendant(String relativePath) {
        throw new RuntimeException("getDescendant(" + relativePath + ") not implemented");
    }

    @Override
    public List<CrawlableDataset> listDatasets() throws IOException {
        throw new RuntimeException("listDatasets() not implemented");
    }

    @Override
    public List<CrawlableDataset> listDatasets(CrawlableDatasetFilter filter) throws IOException {
        throw new RuntimeException("listDatasets(" + filter + ") not implemented");
    }

    @Override
    public long length() {
        throw new RuntimeException("length() not implemented");
    }

    @Override
    public Date lastModified() {
        throw new RuntimeException("lastModified() not implemented");
    }
}
