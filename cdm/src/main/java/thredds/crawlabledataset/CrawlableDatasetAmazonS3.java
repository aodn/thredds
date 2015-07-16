package thredds.crawlabledataset;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class CrawlableDatasetAmazonS3 implements CrawlableDataset {

    static {
        System.out.println("=======================================");
        System.out.println(" > CrawlableDatasetAmazonS3          < ");
        System.out.println("=======================================");
    }

    static final String S3_PROTOCOL = "s3://";
    static final String DIRECTORY_DELIMITER = "/";

    Object configObject;
    String path;

    AmazonS3 s3client = new AmazonS3Client(new ProfileCredentialsProvider());
    String bucketName;

    public CrawlableDatasetAmazonS3(String path, Object configObject) {

        this.path = path;
        this.configObject = configObject;

        System.out.println("path = " + path);
        System.out.println("configObject = " + configObject);
        System.out.println("s3client = " + s3client);

        int startIdx = S3_PROTOCOL.length();
        int endIdx = path.indexOf(DIRECTORY_DELIMITER, startIdx);
        bucketName = path.substring(startIdx, endIdx);

        System.out.println("bucketName = " + bucketName);

        for (S3ObjectSummary summary : getObjectSummaries()) {
            System.out.println("summary = " + summary.getKey());
        }
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
        return path.substring(path.lastIndexOf(DIRECTORY_DELIMITER));
    }

    @Override
    public CrawlableDataset getParentDataset() {

        int amountToTrim = path.endsWith(DIRECTORY_DELIMITER) ? DIRECTORY_DELIMITER.length() : 0;
        String tempPath = path.substring(0, path.length() - amountToTrim);
        String parentDatasetPath = tempPath.substring(0, tempPath.lastIndexOf(DIRECTORY_DELIMITER) + 1);

        return new CrawlableDatasetAmazonS3(parentDatasetPath, configObject);
    }

    @Override
    public boolean exists() {
        return s3client.doesBucketExist(bucketName);
    }

    @Override
    public boolean isCollection() {
        return path.endsWith(DIRECTORY_DELIMITER);
    }

    @Override
    public CrawlableDataset getDescendant(String relativePath) {
        return new CrawlableDatasetAmazonS3(path + relativePath, configObject);
    }

    @Override
    public List<CrawlableDataset> listDatasets() throws IOException {

        List<S3ObjectSummary> summaries = getObjectSummaries();

        for (S3ObjectSummary summary : summaries) {
            System.out.println("summary = " + summary);
            System.out.println("summary = " + summary.getKey());
            System.out.println("summary = " + summary.getSize());
            System.out.println("summary = " + summary.getLastModified());
            System.out.println();
        }

        throw new RuntimeException("listDatasets() not implemented");
    }

    @Override
    public List<CrawlableDataset> listDatasets(CrawlableDatasetFilter filter) throws IOException {
        List<CrawlableDataset> list = this.listDatasets();

        if (filter == null) {
            return list;
        }

        List<CrawlableDataset> retList = new ArrayList<CrawlableDataset>();
        for (CrawlableDataset dataset: list) {
            if (filter.accept(dataset)) {
                retList.add(dataset);
            }
        }

        return retList;
    }

    @Override
    public long length() {
        return -1; // Todo - DN: Implement. See: http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/s3/model/S3ObjectSummary.html#getSize%28%29
    }

    @Override
    public Date lastModified() {
        return null; // Todo - DN: Implement. See: http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/s3/model/S3ObjectSummary.html#getLastModified%28%29
    }

    private String getPathWithoutBucket() {
        return path.substring(S3_PROTOCOL.length() + bucketName.length() + DIRECTORY_DELIMITER.length());
    }

    private List<S3ObjectSummary> getObjectSummaries() {

        System.out.println("getPathWithoutBucket() = " + getPathWithoutBucket());

        ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
            .withBucketName(bucketName)
            .withPrefix(getPathWithoutBucket());

        List<S3ObjectSummary> summaries = new ArrayList<S3ObjectSummary>();

        ObjectListing objectListing;
        do {
            objectListing = s3client.listObjects(listObjectsRequest);
            summaries.addAll(objectListing.getObjectSummaries());

            listObjectsRequest.setMarker(objectListing.getNextMarker());
        } while (objectListing.isTruncated());

        return summaries;
    }
}
