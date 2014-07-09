package ucar.nc2.ft.point.writer;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import ucar.nc2.NetcdfFileWriter;
import ucar.nc2.VariableSimpleIF;
import ucar.nc2.constants.FeatureType;
import ucar.nc2.ft.*;
import ucar.nc2.ft.point.TestCFPointDatasets;
import ucar.nc2.util.CompareNetcdf2;
import ucar.unidata.test.util.TestDir;
import ucar.unidata.util.StringUtil2;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;

/**
 * Test CFPointWriter, write into nc, nc4 and nc4c (classic) files        C:/dev/github/thredds/cdm/target/test/tmp/stationRaggedContig.ncml.nc4
 *
 * @author caron
 * @since 4/11/12
 */
@RunWith(Parameterized.class)
public class TestCFPointWriter {
  static public String CFpointObs_topdir = TestDir.cdmLocalTestDataDir + "point/";

  @Parameterized.Parameters
  public static List<Object[]> getTestParameters() {
    List<Object[]> result = new ArrayList<>();
    //result.add(new Object[]{CFpointObs_topdir + "profileMultidimZJoin.ncml", FeatureType.PROFILE, 50});
    // result.add(new Object[]{CFpointObs_topdir + "profileMultidimTimeZJoin.ncml", FeatureType.PROFILE, 50})

    result.addAll(TestCFPointDatasets.getPointDatasets());
    result.addAll(TestCFPointDatasets.getStationDatasets());
    result.addAll(TestCFPointDatasets.getProfileDatasets());

    /* result.add(new Object[] {TestDir.cdmUnitTestDir + "ft/point/ldm/04061912_buoy.nc", FeatureType.POINT, 218});
    result.add(new Object[] {TestDir.cdmUnitTestDir + "ft/point/netcdf/Surface_Buoy_20090921_0000.nc", FeatureType.POINT, 32452});
    result.add(new Object[] {TestDir.cdmUnitTestDir + "ft/station/multiStationMultiVar.ncml", FeatureType.STATION, 15});
    result.add(new Object[] {TestDir.cdmUnitTestDir + "cfPoint/station/sampleDataset.nc", FeatureType.STATION, 1728});
    result.add(new Object[] {TestDir.cdmUnitTestDir + "ft/station/200501q3h-gr.nc", FeatureType.STATION, 5023});  // */
    return result;
  }

  String location;
  FeatureType ftype;
  int countExpected;
  boolean show = false;

  public TestCFPointWriter(String location, FeatureType ftype, int countExpected) {
    this.location = location;
    this.ftype = ftype;
    this.countExpected = countExpected;
  }

  // @Test
  public void testWrite3col() throws IOException {
    CFPointWriterConfig config = new CFPointWriterConfig(NetcdfFileWriter.Version.netcdf3);
    config.recDimensionLength = countExpected;

    int count = writeDataset(location, ".nc-col", ftype, config, true);   // column oriented
    System.out.printf("%s netcdf3 count=%d%n", location, count);
    assert count == countExpected : "count ="+count+" expected "+countExpected;
  }

  @Test
  public void testWrite3() throws IOException {
    int count = writeDataset(location, ".nc", ftype, new CFPointWriterConfig(NetcdfFileWriter.Version.netcdf3), true);
    System.out.printf("%s netcdf3 count=%d%n", location, count);
    assert count == countExpected : "count ="+count+" expected "+countExpected;
  }

  // @Test
  public void testWrite4classic() throws IOException {
    int count = writeDataset(location, ".nc4c", ftype, new CFPointWriterConfig(NetcdfFileWriter.Version.netcdf4_classic), true);
    System.out.printf("%s netcdf4_classic count=%d%n", location, count);
    assert count == countExpected : "count ="+count+" expected "+countExpected;
  }

  //@Test
  public void testWrite4() throws IOException {
    int count = writeDataset(location, ".nc4", ftype, new CFPointWriterConfig(NetcdfFileWriter.Version.netcdf4), true);
    System.out.printf("%s netcdf4 count=%d%n", location, count);
    assert count == countExpected : "count ="+count+" expected "+countExpected;
  }

  // @Test
  public void testProblem() throws IOException {
    writeDataset(TestDir.cdmUnitTestDir + "ft/point/netcdf/Surface_Buoy_20090921_0000.nc", "nc4", FeatureType.POINT,
            new CFPointWriterConfig(NetcdfFileWriter.Version.netcdf4), true);
  }

  // synthetic variants
  /* @Test
  public void testWriteProfileVariants() throws IOException {
    assert 13 ==  writeDataset(CFpointObs_topdir + "profileSingle.ncml", FeatureType.PROFILE);
    assert 12 ==  writeDataset(CFpointObs_topdir + "profileSingleTimeJoin.ncml", FeatureType.PROFILE);
    assert 50 ==  writeDataset(CFpointObs_topdir + "profileMultidim.ncml", FeatureType.PROFILE);
    assert 50 ==  writeDataset(CFpointObs_topdir + "profileMultidimTimeJoin.ncml", FeatureType.PROFILE);
    assert 50 ==  writeDataset(CFpointObs_topdir + "profileMultidimZJoin.ncml", FeatureType.PROFILE);
    assert 50 ==  writeDataset(CFpointObs_topdir + "profileMultidimTimeZJoin.ncml", FeatureType.PROFILE);
    assert 40 ==  writeDataset(CFpointObs_topdir + "profileMultidimMissingId.ncml", FeatureType.PROFILE);
    assert 14 == writeDataset(CFpointObs_topdir + "profileMultidimMissingAlt.ncml", FeatureType.PROFILE);
    assert 6 ==  writeDataset(CFpointObs_topdir + "profileRaggedContig.ncml", FeatureType.PROFILE);
    assert 6 ==  writeDataset(CFpointObs_topdir + "profileRaggedContigTimeJoin.ncml", FeatureType.PROFILE);
    assert 22 ==  writeDataset(CFpointObs_topdir + "profileRaggedIndex.ncml", FeatureType.PROFILE);
    assert 22 ==  writeDataset(CFpointObs_topdir + "profileRaggedIndexTimeJoin.ncml", FeatureType.PROFILE);
  } */

  int writeDataset(String location, String prefix, FeatureType ftype, CFPointWriterConfig config, boolean readBack) throws IOException {
    File fileIn = new File(location);
    long start = System.currentTimeMillis();

    int pos = location.lastIndexOf("/");
    String name = location.substring(pos + 1);
    //String prefix = (config.version == NetcdfFileWriter.Version.netcdf3) ? ".nc" : (config.version == NetcdfFileWriter.Version.netcdf4) ? ".nc4" : ".nc4c";
    if (!name.endsWith(prefix)) name = name + prefix;
    File fileOut = new File(TestDir.temporaryLocalDataDir, name);

    String absIn = fileIn.getAbsolutePath();
    absIn = StringUtil2.replace(absIn, "\\", "/");
    String absOut = fileOut.getAbsolutePath();
    absOut = StringUtil2.replace(absOut, "\\", "/");
    System.out.printf("================ TestCFPointWriter%n read %s size=%d%n write to=%s%n", absIn, fileIn.length(), absOut);

    // open point dataset
    Formatter out = new Formatter();
    FeatureDataset fdataset = FeatureDatasetFactoryManager.open(ftype, location, null, out);
    if (fdataset == null) {
      System.out.printf("**failed on %s %n --> %s %n", location, out);
      assert false;
    }

    assert fdataset instanceof FeatureDatasetPoint;
    FeatureDatasetPoint fdpoint = (FeatureDatasetPoint) fdataset;
    int count = CFPointWriter.writeFeatureCollection(fdpoint, fileOut.getPath(), config);
    long took = System.currentTimeMillis() - start;
    System.out.printf(" nrecords written = %d took=%d msecs%n%n", count, took);

    ////////////////////////////////
    // open result
    if (readBack) {

      System.out.printf(" open result dataset=%s size = %d (%f ratio out/in) %n", fileOut.getPath(), fileOut.length(), ((double) fileOut.length() / fileIn.length()));
      out = new Formatter();

      FeatureDataset result = FeatureDatasetFactoryManager.open(ftype, fileOut.getPath(), null, out);
      if (result == null) {
        System.out.printf(" **failed --> %n%s <--END FAIL messages%n", out);
        assert false;
      }
      if (show) {
        System.out.printf("----------- testPointDataset getDetailInfo -----------------%n");
        result.getDetailInfo(out);
        System.out.printf("%s %n", out);
      }

      compare( fdpoint, (FeatureDatasetPoint) result);
      result.close();
    }

    fdataset.close();

    return count;
  }

  void compare(FeatureDatasetPoint org, FeatureDatasetPoint copy) {

    FeatureType fcOrg = org.getFeatureType();
    FeatureType fcCopy = copy.getFeatureType();
    assert fcOrg == fcCopy;

    List<VariableSimpleIF> orgVars = org.getDataVariables();
    List<VariableSimpleIF> copyVars = copy.getDataVariables();
    Formatter f = new Formatter();
    boolean ok = CompareNetcdf2.compareLists(getNames(orgVars), getNames(copyVars), f);
    if (ok) System.out.printf("Data Vars OK%n");
    else {
      System.out.printf("Data Vars NOT OK%n %s%n", f);
      assert false;
    }


/*    FeatureCollection orgFc = org.getPointFeatureCollectionList().get(0);
    FeatureCollection copyFc = copy.getPointFeatureCollectionList().get(0);

          if (fc instanceof PointFeatureCollection) {
            PointFeatureCollection pfc = (PointFeatureCollection) fc;
            count = checkPointFeatureCollection(pfc, show);
            System.out.println("PointFeatureCollection getData count= " + count + " size= " + pfc.size());
            assert count == pfc.size();

          } else if (fc instanceof StationTimeSeriesFeatureCollection) {
            count = checkStationFeatureCollection((StationTimeSeriesFeatureCollection) fc);
            //testNestedPointFeatureCollection((StationTimeSeriesFeatureCollection) fc, show);

          } else if (fc instanceof StationProfileFeatureCollection) {
            count = checkStationProfileFeatureCollection((StationProfileFeatureCollection) fc, show);
            if (showStructureData) showStructureData((StationProfileFeatureCollection) fc);

          } else if (fc instanceof SectionFeatureCollection) {
            count = checkSectionFeatureCollection((SectionFeatureCollection) fc, show);

          } else if (fc instanceof ProfileFeatureCollection) {
            count = checkProfileFeatureCollection((ProfileFeatureCollection) fc, show);

          } else {
            count = checkNestedPointFeatureCollection((NestedPointFeatureCollection) fc, show);
          }
        }

      }   */
  }

  List<String> getNames(List<VariableSimpleIF> vars) {
    List<String> result = new ArrayList<>();
    for (VariableSimpleIF v : vars) {
      result.add(v.getShortName());
      System.out.printf("  %s%n", v.getShortName());
    }
    System.out.printf("%n");
    return result;
  }

}
