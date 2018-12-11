package thredds.crawlabledataset.s3;

import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Listing of the "virtual directory" at a specified Amazon S3 URI.
 *
 * @author jonescc
 * @since 2016/09/12
 */
public class ThreddsS3Listing {
    private static final Logger logger = LoggerFactory.getLogger(ThreddsS3Listing.class);

    private final S3URI s3uri;

    private List<ThreddsS3Metadata> entries = new ArrayList<>();

    public ThreddsS3Listing(S3URI s3uri) {
        this.s3uri = s3uri;
    }

    public S3URI getS3uri() {
        return s3uri;
    }

    public void add(ObjectListing objectListing) {
        if (objectListing == null) {
            return;
        }

        List<String> legitFiles = new ArrayList<>();
        for (S3ObjectSummary objectSummary : objectListing.getObjectSummaries()) {
            String [] knownFileExtensions = {".hdf", ".xml", ".nc", ".bz2", ".cdp", ".jpg"};

            for(int i=0;i<knownFileExtensions.length;i++) {
                if (objectSummary.getKey().toLowerCase().endsWith(knownFileExtensions[i])) {
                    legitFiles.add(objectSummary.getKey());
                    entries.add(new ThreddsS3Object(objectSummary));
                }
            }
        }

        List<String> commonPrefixes = objectListing.getCommonPrefixes();
        Set<String> legitCommonPrefixes = new HashSet<>();
        for(String file : legitFiles) {
            for(String prefix : commonPrefixes)  {
                if(file.startsWith(prefix)) {
                    legitCommonPrefixes.add(prefix);
                }
            }
        }
        for (String commonPrefix : commonPrefixes) {
            logger.info("COMMON PREFIX UNFILTERED: " + commonPrefix);
        }
        for (String commonPrefix : legitCommonPrefixes) {
            logger.info("COMMON PREFIX: " + commonPrefix);
            entries.add(new ThreddsS3Directory(new S3URI(s3uri.getBucket(), commonPrefix)));
        }
    }

    public void add(ThreddsS3Metadata metadata) {
        entries.add(metadata);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public List<ThreddsS3Metadata> getContents() {
        return new ArrayList<>(entries);
    }

    //////////////////////////////////////// Object ////////////////////////////////////////

    @Override
    public String toString() {
        return String.format("ThreddsS3Listing{'%s'}", s3uri);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else if (other == null || getClass() != other.getClass()) {
            return false;
        }

        ThreddsS3Listing that = (ThreddsS3Listing) other;
        return Objects.equals(this.getS3uri(), that.getS3uri()) &&
                Objects.equals(this.getContents(), that.getContents());
    }

    @Override
    public int hashCode() {
        return s3uri.hashCode();
    }
}
