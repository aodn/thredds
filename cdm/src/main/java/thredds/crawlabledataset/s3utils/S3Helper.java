package thredds.crawlabledataset.s3utils;

import net.sf.ehcache.*;
import net.sf.ehcache.event.CacheEventListener;
import net.sf.ehcache.constructs.blocking.SelfPopulatingCache;
import net.sf.ehcache.constructs.blocking.CacheEntryFactory;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.*;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class S3Helper {
    private ConcurrentHashMap<String, File> fileStore;
    private Ehcache s3ObjectCache;
    private Ehcache s3ListingCache;

    private static final String EHCACHE_S3_OBJECT_KEY = "S3Objects";
    private static final String EHCACHE_S3_LISTING_KEY = "S3Listing";
    private static final int EHCACHE_MAX_OBJECTS = 1000;
    private static final int EHCACHE_TTL = 60;
    private static final int EHCACHE_TTI = 60;

    private static org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(S3Helper.class);

    public S3Helper() {
        this.fileStore = new ConcurrentHashMap<>();
        this.s3ObjectCache = createCache(EHCACHE_S3_OBJECT_KEY, new S3ObjectCacheEntryFactory(), new S3CacheEventListener());
        this.s3ListingCache = createCache(EHCACHE_S3_LISTING_KEY, new S3ListingCacheEntryFactory(), null);
    }

    private Ehcache createCache(String cacheName, CacheEntryFactory factory, S3CacheEventListener eventListener) {
        CacheManager cacheManager = CacheManager.create();
        Cache newCache = new Cache(cacheName, EHCACHE_MAX_OBJECTS, false, false, EHCACHE_TTL, EHCACHE_TTI);
        cacheManager.addCache(newCache);
        SelfPopulatingCache selfPopulatingCache = new SelfPopulatingCache(newCache, factory);

        if (null != eventListener) {
            selfPopulatingCache.getCacheEventNotificationService().registerListener(eventListener);
        }

        cacheManager.replaceCacheWithDecoratedCache(newCache, selfPopulatingCache);

        return cacheManager.getEhcache(cacheName);
    }

    public void deleteFileElement(Element element) {
        File file = (File) fileStore.get(element.getObjectKey());
        if (null == file) {
            return;
        }

        // Should cleanup what createTempFile has created, meaning we have
        // to get rid of both the file and its containing directory
        file.delete();
        file.getParentFile().delete();

        fileStore.remove(element.getObjectKey());
    }

    public File createTempFile(String uri) throws IOException {
        // We have to save the key twice, as Ehcache will not provide
        // us with tmpFile when eviction happens, so we use fileStore
        Path tmpDir = Files.createTempDirectory("S3Download_");
        String fileBasename = new File(uri).getName();
        File file = new File(tmpDir.toFile(), fileBasename);
        file.deleteOnExit();
        fileStore.put(uri, file);
        return file;
    }

    public File getS3File(String uri) {
        log.debug(String.format("S3 Downloading '%s'", uri));
        try {
            return (File) s3ObjectCache.get(uri).getObjectValue();
        }
        catch (Exception e)
        {
            log.error(String.format(String.format("S3 Error downloading '%s'", uri)));
            e.printStackTrace();
            return null;
        }
    }

    public List<ThreddsS3Object> listS3Dir(String uri) {
        return (List<ThreddsS3Object>) s3ListingCache.get(uri).getObjectValue();
    }

    public class S3CacheEventListener implements CacheEventListener {

        public void notifyElementRemoved(final Ehcache cache, final Element element) throws CacheException
        {
            deleteFileElement(element);
        }

        public void notifyElementExpired(final Ehcache cache, final Element element)
        {
            deleteFileElement(element);
        }

        public void notifyElementEvicted(Ehcache cache, Element element)
        {
            deleteFileElement(element);
        }

        public void notifyElementPut(final Ehcache cache, final Element element) throws CacheException {}
        public void notifyElementUpdated(final Ehcache cache, final Element element) throws CacheException {}
        public void notifyRemoveAll(Ehcache cache) {}
        public void dispose() {}
        public Object clone() throws CloneNotSupportedException
        {
            return super.clone();
        }
    }

    public class S3ListingCacheEntryFactory implements CacheEntryFactory {

        @Override
        public Object createEntry(Object uri) throws Exception {

            List<ThreddsS3Object> listing = new ArrayList<ThreddsS3Object>();

            log.debug(String.format("S3 Listing '%s'", uri));

            try {
                String[] uriParts = S3PathHelper.s3UriParts(uri.toString());
                String s3Bucket = uriParts[0];
                String s3Key = uriParts[1];

                if (!s3Key.endsWith(S3PathHelper.delimiter())) {
                    s3Key += S3PathHelper.delimiter();
                }

                final ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
                        .withBucketName(s3Bucket)
                        .withPrefix(s3Key)
                        .withDelimiter(S3PathHelper.delimiter());

                final ObjectListing objectListing = S3PathHelper.getS3Client().listObjects(listObjectsRequest);

                for (final S3ObjectSummary objectSummary : objectListing.getObjectSummaries()) {
                    listing.add(new ThreddsS3Object(
                            S3PathHelper.stripPrefix(objectSummary.getKey(), s3Key),
                            objectSummary.getSize(),
                            objectSummary.getLastModified(),
                            ThreddsS3Object.Type.FILE
                    ));
                }

                for (final String commonPrefix : objectListing.getCommonPrefixes()) {
                    String key = S3PathHelper.stripPrefix(commonPrefix, s3Key);
                    key = S3PathHelper.removeTrailingSlash(key);

                    listing.add(new ThreddsS3Object(key, ThreddsS3Object.Type.DIR));
                }

                return listing;
            }
            catch (Exception e) {
                log.error(String.format(String.format("S3 Error listing '%s'", uri)));
                e.printStackTrace();
                throw e;
            }
        }
    }

    public class S3ObjectCacheEntryFactory implements CacheEntryFactory {

        @Override
        public Object createEntry(Object uri) throws Exception {
            File tmpFile = createTempFile(uri.toString());

            try {
                String[] uriParts = S3PathHelper.s3UriParts(uri.toString());
                String s3Bucket = uriParts[0];
                String s3Key = uriParts[1];

                S3Object object = S3PathHelper.getS3Client().getObject(new GetObjectRequest(s3Bucket, s3Key));

                log.info(String.format("S3 Downloading 's3://%s/%s' to '%s'", s3Bucket, s3Key, tmpFile.toString()));

                try (OutputStream os = new FileOutputStream(tmpFile);
                     InputStream is = object.getObjectContent()) {
                     IOUtils.copy(is, os);
                }

            } catch (Exception e) {
                log.error("Failed to download file from S3: '%s'", e);
                tmpFile.delete();
            }
            return tmpFile;
        }
    }

}
