package ucar.nc2.ui;

import thredds.inventory.CollectionManager;
import thredds.inventory.MFileCollectionManager;
import thredds.inventory.MFile;
import ucar.nc2.Attribute;
import ucar.nc2.NetcdfFile;
import ucar.nc2.dataset.CoordinateAxis1D;
import ucar.nc2.dataset.NetcdfDataset;
import ucar.nc2.dt.GridCoordSystem;
import ucar.nc2.dt.GridDatatype;
import ucar.nc2.dt.grid.GridDataset;
import ucar.nc2.grib.GribCollection;
import ucar.nc2.grib.GribStatType;
import ucar.nc2.grib.grib2.Grib2CollectionBuilder;
import ucar.nc2.grib.grib2.*;
import ucar.nc2.grib.grib2.Grib2Pds;
import ucar.nc2.grib.grib2.table.Grib2Customizer;
import ucar.nc2.grib.grib2.table.WmoCodeTable;
import ucar.nc2.ui.widget.TextHistoryPane;
import ucar.nc2.util.Misc;
import ucar.unidata.io.RandomAccessFile;
import ucar.util.prefs.PreferencesExt;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;

/**
 * Run through collections of Grib 2 files and make reports
 *
 * @author caron
 * @since Dec 13, 2010
 */
public class Grib2ReportPanel extends JPanel {
  public static enum Report {
    checkTables, localUseSection, uniqueGds, duplicatePds, drsSummary, gdsTemplate, pdsSummary, idProblems, timeCoord, names,
  }

  private PreferencesExt prefs;
  private TextHistoryPane reportPane;

  public Grib2ReportPanel(PreferencesExt prefs, JPanel buttPanel) {
    this.prefs = prefs;
    reportPane = new TextHistoryPane();
    setLayout(new BorderLayout());
    add(reportPane, BorderLayout.CENTER);
  }

  public void save() {
  }

  public void showInfo(Formatter f) {
  }

  public boolean setCollection(String spec) {
    Formatter f = new Formatter();
    f.format("collection = %s%n", spec);
    boolean hasFiles = false;

    CollectionManager dcm = getCollection(spec, f);
    if (dcm == null) {
      return false;
    }

    for (MFile mfile : dcm.getFiles()) {
      f.format(" %s%n", mfile.getPath());
      hasFiles = true;
    }

    reportPane.setText(f.toString());
    reportPane.gotoTop();
    return hasFiles;
  }

  private CollectionManager getCollection(String spec, Formatter f) {
    CollectionManager dc = null;
    try {
      dc = MFileCollectionManager.open(spec, null, f);
      dc.scan(false);

    } catch (Exception e) {
      ByteArrayOutputStream bos = new ByteArrayOutputStream(10000);
      e.printStackTrace(new PrintStream(bos));
      reportPane.setText(bos.toString());
      return null;
    }

    return dc;
  }

  public void doReport(String spec, boolean useIndex, Report which) throws IOException {
    Formatter f = new Formatter();
    f.format("%s %s %s%n", spec, useIndex, which);

    CollectionManager dcm = getCollection(spec, f);
    if (dcm == null) {
      return;
    }

    // CollectionSpecParser parser = dcm.getCollectionSpecParser();

    f.format("top dir = %s%n", dcm.getRoot());
    //f.format("filter = %s%n", parser.getFilter());
    reportPane.setText(f.toString());

    File top = new File(dcm.getRoot());
    if (!top.exists()) {
      f.format("top dir = %s does not exist%n", dcm.getRoot());
    } else {

      switch (which) {
        case checkTables:
          doCheckTables(f, dcm, useIndex);
          break;
        case localUseSection:
          doLocalUseSection(f, dcm, useIndex);
          break;
        case uniqueGds:
          doUniqueGds(f, dcm, useIndex);
          break;
        case duplicatePds:
          doDuplicatePds(f, dcm, useIndex);
          break;
        case drsSummary:
          doDrsSummary(f, dcm, useIndex);
          break;
        case gdsTemplate:
          doGdsTemplate(f, dcm, useIndex);
          break;
        case pdsSummary:
          doPdsSummary(f, dcm, useIndex);
          break;
        case idProblems:
          doIdProblems(f, dcm, useIndex);
          break;
        case timeCoord:
          doTimeCoord(f, dcm, useIndex);
          break;
        case names:
          doNames(f, dcm, useIndex);
          break;
      }
    }

    reportPane.setText(f.toString());
    reportPane.gotoTop();
  }

  ///////////////////////////////////////////////

  private void doCheckTables(Formatter f, CollectionManager dcm, boolean useIndex) throws IOException {
    f.format("Check Grib-2 Parameter Tables%n");
    int[] accum = new int[4];

    for (MFile mfile : dcm.getFiles()) {
      f.format("%n %s%n", mfile.getPath());
      doCheckTables(mfile, f, accum);
    }

    f.format("%nGrand total=%d not operational = %d local = %d missing = %d%n", accum[0], accum[1], accum[2], accum[3]);
  }

  private void doCheckTables(MFile ff, Formatter fm, int[] accum) throws IOException {
    int local = 0;
    int miss = 0;
    int nonop = 0;
    int total = 0;

    GridDataset ncfile = null;
    try {
      ncfile = GridDataset.open(ff.getPath());
      for (GridDatatype dt : ncfile.getGrids()) {
        String currName = dt.getName();
        total++;

        Attribute att = dt.findAttributeIgnoreCase("Grib_Parameter");
        if (att != null && att.getLength() == 3) {
          int discipline = (Integer) att.getValue(0);
          int category = (Integer) att.getValue(1);
          int number = (Integer) att.getValue(2);
          if ((category > 191) || (number > 191)) {
            fm.format("  local parameter (%d %d %d) = %s units=%s %n", discipline, category, number, currName, dt.getUnitsString());
            local++;
            continue;
          }

          WmoCodeTable.TableEntry entry = WmoCodeTable.getParameterEntry(discipline, category, number);
          if (entry == null) {
            fm.format("  missing from WMO table (%d %d %d) = %s units=%s %n", discipline, category, number, currName, dt.getUnitsString());
            miss++;
            continue;
          }

          if (!entry.status.equalsIgnoreCase("Operational")) {
            fm.format("  %s parameter = %s (%d %d %d) %n", entry.status, currName, discipline, category, number);
            nonop++;
          }
        }

      }
    } finally {
      if (ncfile != null) ncfile.close();
    }
    fm.format("total=%d not operational = %d local = %d missing = %d%n", total, nonop, local, miss);
    accum[0] += total;
    accum[1] += nonop;
    accum[2] += local;
    accum[3] += miss;
  }

  ///////////////////////////////////////////////

  private void doLocalUseSection(Formatter f, CollectionManager dcm, boolean useIndex) throws IOException {
    f.format("Show Local Use Section%n");

    for (MFile mfile : dcm.getFiles()) {
      f.format(" %s%n", mfile.getPath());
      doLocalUseSection(mfile, f, useIndex);
    }
  }

  private void doLocalUseSection(MFile mf, Formatter f, boolean useIndex) throws IOException {
    f.format("File = %s%n", mf);

    Grib2Index index = createIndex(mf, f);
    if (index == null) return;

    for (Grib2Record gr : index.getRecords()) {
      Grib2SectionLocalUse lus = gr.getLocalUseSection();
      if (lus == null || lus.getRawBytes() == null)
        f.format(" %10d == none%n", gr.getDataSection().getStartingPosition());
      else
        f.format(" %10d == %s%n", gr.getDataSection().getStartingPosition(), Misc.showBytes(lus.getRawBytes()));
    }
  }

  private Grib2Index createIndex(MFile mf, Formatter f) throws IOException {
    String path = mf.getPath();
    Grib2Index index = new Grib2Index();
    if (!index.readIndex(path, mf.getLastModified())) {
      // make sure its a grib2 file
      RandomAccessFile raf = new RandomAccessFile(path, "r");
      if (!Grib2RecordScanner.isValidFile(raf)) return null;
      index.makeIndex(path, f);
    }
    return index;
  }

  ///////////////////////////////////////////////

  private void doUniqueGds(Formatter f, CollectionManager dcm, boolean useIndex) throws IOException {
    f.format("Show Unique GDS%n");

    Map<Integer, GdsList> gdsSet = new HashMap<Integer, GdsList>();
    for (MFile mfile : dcm.getFiles()) {
      f.format(" %s%n", mfile.getPath());
      doUniqueGds(mfile, gdsSet, f);
    }

    for (GdsList gdsl : gdsSet.values()) {
      f.format("%nGDS = %d x %d (%d) %n", gdsl.gds.ny, gdsl.gds.nx, gdsl.gds.template);
      for (FileCount fc : gdsl.fileList)
        f.format("  %5d %s (%d)%n", fc.count, fc.f.getPath(), fc.countGds);
    }
  }

  private void doUniqueGds(MFile mf, Map<Integer, GdsList> gdsSet, Formatter f) throws IOException {
    Grib2Index index = createIndex(mf, f);
    if (index == null) return;

    int countGds = index.getGds().size();
    for (Grib2Record gr : index.getRecords()) {
      int hash = gr.getGDSsection().getGDS().hashCode();
      GdsList gdsList = gdsSet.get(hash);
      if (gdsList == null) {
        gdsList = new GdsList(gr.getGDSsection().getGDS());
        gdsSet.put(hash, gdsList);
      }
      FileCount fc = gdsList.contains(mf);
      if (fc == null) {
        fc = new FileCount(mf, countGds);
        gdsList.fileList.add(fc);
      }
      fc.count++;
    }
  }

  private class GdsList {
    Grib2Gds gds;
    java.util.List<FileCount> fileList = new ArrayList<FileCount>();

    private GdsList(Grib2Gds gds) {
      this.gds = gds;
    }

    FileCount contains(MFile f) {
      for (FileCount fc : fileList)
        if (fc.f.getPath().equals(f.getPath())) return fc;
      return null;
    }

  }

  private class FileCount {
    private FileCount(MFile f, int countGds) {
      this.f = f;
      this.countGds = countGds;
    }

    MFile f;
    int count = 0;
    int countGds = 0;
  }

  ///////////////////////////////////////////////

  private int countPDS, countPDSdup;

  private void doDuplicatePds(Formatter f, CollectionManager dcm, boolean useIndex) throws IOException {
    countPDS = 0;
    countPDSdup = 0;
    f.format("Show Duplicate PDS%n");
    for (MFile mfile : dcm.getFiles()) {
      doDuplicatePds(f, mfile);
    }
    f.format("Total PDS duplicates = %d / %d%n%n", countPDSdup, countPDS);
  }

  private void doDuplicatePds(Formatter f, MFile mfile) throws IOException {
    Set<Long> pdsMap = new HashSet<Long>();
    int dups = 0;
    int count = 0;

    RandomAccessFile raf = new RandomAccessFile(mfile.getPath(), "r");
    Grib2RecordScanner scan = new Grib2RecordScanner(raf);
    while (scan.hasNext()) {
      ucar.nc2.grib.grib2.Grib2Record gr = scan.next();
      Grib2SectionProductDefinition pds = gr.getPDSsection();
      long crc = pds.calcCRC();
      if (pdsMap.contains(crc))
        dups++;
      else
        pdsMap.add(crc);
      count++;
    }
    raf.close();

    f.format("PDS duplicates = %d / %d for %s%n%n", dups, count, mfile.getPath());
    countPDS += count;
    countPDSdup += dups;
  }

  ///////////////////////////////////////////////

  private class Counter {
    Map<Integer, Integer> set = new HashMap<Integer, Integer>();
    String name;

    private Counter(String name) {
      this.name = name;
    }

    void count(int value) {
      Integer count = set.get(value);
      if (count == null)
        set.put(value, 1);
      else
        set.put(value, count + 1);
    }

    void show(Formatter f) {
      f.format("%n%s%n", name);
      List<Integer> list = new ArrayList<Integer>(set.keySet());
      Collections.sort(list);
      for (int template : list) {
        int count = set.get(template);
        f.format("   %3d: count = %d%n", template, count);
      }
    }

  }

  int total = 0;
  int prob = 0;

  private void doPdsSummary(Formatter f, CollectionManager dcm, boolean useIndex) throws IOException {
    if (useIndex) {
      f.format("Check Grib-2 PDS probability and statistical variables%n");
      total = 0;
      prob = 0;
      for (MFile mfile : dcm.getFiles()) {
        f.format("%n %s%n", mfile.getPath());
        doPdsSummaryIndexed(f, mfile);
      }
      f.format("problems = %d/%d%n", prob, total);

    } else {
      Counter templateSet = new Counter("template");
      Counter timeUnitSet = new Counter("timeUnit");
      Counter levelTypeSet = new Counter("levelType");
      Counter processType = new Counter("genProcessType");
      Counter processId = new Counter("genProcessId");
      Counter levelScale = new Counter("levelScale");
      Counter ncoords = new Counter("nExtraCoords");

      for (MFile mfile : dcm.getFiles()) {
        f.format(" %s%n", mfile.getPath());
        doPdsSummary(f, mfile, templateSet, timeUnitSet, processType, processId, levelScale, levelTypeSet, ncoords);
      }

      templateSet.show(f);
      timeUnitSet.show(f);
      levelTypeSet.show(f);
      processType.show(f);
      processId.show(f);
      levelScale.show(f);
      ncoords.show(f);
    }
  }

  private void doPdsSummaryIndexed(Formatter fm, MFile ff) throws IOException {
    String path = ff.getPath();

    Grib2Index index = createIndex(ff, fm);
    if (index == null) return;

    GribCollection gc = Grib2CollectionBuilder.readOrCreateIndexFromSingleFile(ff, CollectionManager.Force.nocheck, null, fm);
    gc.close();

    GridDataset ncfile = null;
    try {
      ncfile = GridDataset.open(path + GribCollection.IDX_EXT);
      for (GridDatatype dt : ncfile.getGrids()) {
        String currName = dt.getName();
        total++;

        Attribute att = dt.findAttributeIgnoreCase("Grib_Probability_Type");
        if (att != null) {
          fm.format("  %s (PROB) desc=%s %n", currName, dt.getDescription());
          prob++;
        }

        att = dt.findAttributeIgnoreCase("Grib_Statistical_Interval_Type");
        if (att != null) {
          int statType = att.getNumericValue().intValue();
          if ((statType == 7) || (statType == 9)) {
            fm.format("  %s (STAT type %s) desc=%s %n", currName, statType, dt.getDescription());
            prob++;
          }
        }

        att = dt.findAttributeIgnoreCase("Grib_Ensemble_Derived_Type");
        if (att != null) {
          int type = att.getNumericValue().intValue();
          if ((type > 9)) {
            fm.format("  %s (DERIVED type %s) desc=%s %n", currName, type, dt.getDescription());
            prob++;
          }
        }

      }
    } catch (Throwable ioe) {
      fm.format("Failed on %s == %s%n", path, ioe.getMessage());
      System.out.printf("Failed on %s%n", path);
      ioe.printStackTrace();

    } finally {
      if (ncfile != null) ncfile.close();
    }
  }

  private void doPdsSummary(Formatter f, MFile mf, Counter templateSet, Counter timeUnitSet, Counter processType,
                            Counter processId, Counter levelScale, Counter levelTypeSet, Counter ncoords) throws IOException {
    boolean showLevel = true;
    boolean showCoords = true;
    int firstPtype = -1;
    boolean shutup = false;

    Grib2Index index = createIndex(mf, f);
    if (index == null) return;

    for (ucar.nc2.grib.grib2.Grib2Record gr : index.getRecords()) {
      Grib2Pds pds = gr.getPDS();
      templateSet.count(pds.getTemplateNumber());
      timeUnitSet.count(pds.getTimeUnit());

      levelTypeSet.count(pds.getLevelType1());
      if (showLevel && (pds.getLevelType1() == 105)) {
        showLevel = false;
        f.format(" level = 105 : %s%n", mf.getPath());
      }

      int n = pds.getExtraCoordinatesCount();
      ncoords.count(n);
      if (showCoords && (n > 0)) {
        showCoords = false;
        f.format(" ncoords > 0 : %s%n", mf.getPath());
      }

      int ptype = pds.getGenProcessType();
      processType.count(ptype);
      if (firstPtype < 0) firstPtype = ptype;
      else if (firstPtype != ptype && !shutup) {
        f.format(" getGenProcessType differs in %s %s == %d%n", mf.getPath(), gr.getPDS().getParameterNumber(), ptype);
        shutup = true;
      }
      processId.count(pds.getGenProcessId());

      if (pds.getLevelScale() > 127) {
        if (Grib2Utils.isLevelUsed(pds.getLevelType1())) {
          f.format(" LevelScale > 127: %s %s == %d%n", mf.getPath(), gr.getPDS().getParameterNumber(), pds.getLevelScale());
          levelScale.count(pds.getLevelScale());
        }
      }
    }
  }

  ///////////////////////////////////////////////

  private void doIdProblems(Formatter f, CollectionManager dcm, boolean useIndex) throws IOException {
    f.format("Look for ID Problems%n");

    Counter disciplineSet = new Counter("discipline");
    Counter masterTable = new Counter("masterTable");
    Counter localTable = new Counter("localTable");
    Counter centerId = new Counter("centerId");
    Counter subcenterId = new Counter("subcenterId");
    Counter genProcess = new Counter("genProcess");
    Counter backProcess = new Counter("backProcess");

    for (MFile mfile : dcm.getFiles()) {
      f.format(" %s%n", mfile.getPath());
      doIdProblems(f, mfile, useIndex,
              disciplineSet, masterTable, localTable, centerId, subcenterId, genProcess, backProcess);
    }

    disciplineSet.show(f);
    masterTable.show(f);
    localTable.show(f);
    centerId.show(f);
    subcenterId.show(f);
    genProcess.show(f);
    backProcess.show(f);
  }

  private void doIdProblems(Formatter f, MFile mf, boolean showProblems,
                            Counter disciplineSet, Counter masterTable, Counter localTable, Counter centerId,
                            Counter subcenterId, Counter genProcessC, Counter backProcessC) throws IOException {
    Grib2Index index = createIndex(mf, f);
    if (index == null) return;

    // these should be the same for the entire file
    int center = -1;
    int subcenter = -1;
    int master = -1;
    int local = -1;
    int genProcess = -1;
    int backProcess = -1;

    for (ucar.nc2.grib.grib2.Grib2Record gr : index.getRecords()) {
      disciplineSet.count(gr.getDiscipline());
      masterTable.count(gr.getId().getMaster_table_version());
      localTable.count(gr.getId().getLocal_table_version());
      centerId.count(gr.getId().getCenter_id());
      subcenterId.count(gr.getId().getSubcenter_id());
      genProcessC.count(gr.getPDS().getGenProcessId());
      backProcessC.count(gr.getPDS().getBackProcessId());

      if (!showProblems) continue;

      if (gr.getDiscipline() == 255) {
        f.format("  bad discipline= ");
        gr.show(f);
        f.format("%n");
      }

      int val = gr.getId().getCenter_id();
      if (center < 0) center = val;
      else if (center != val) {
        f.format("  center %d != %d ", center, val);
        gr.show(f);
        f.format(" %s%n", gr.getId());
      }

      val = gr.getId().getSubcenter_id();
      if (subcenter < 0) subcenter = val;
      else if (subcenter != val) {
        f.format("  subcenter %d != %d ", subcenter, val);
        gr.show(f);
        f.format(" %s%n", gr.getId());
      }

      val = gr.getId().getMaster_table_version();
      if (master < 0) master = val;
      else if (master != val) {
        f.format("  master %d != %d ", master, val);
        gr.show(f);
        f.format(" %s%n", gr.getId());
      }

      val = gr.getId().getLocal_table_version();
      if (local < 0) local = val;
      else if (local != val) {
        f.format("  local %d != %d ", local, val);
        gr.show(f);
        f.format(" %s%n", gr.getId());
      }

      val = gr.getPDS().getGenProcessId();
      if (genProcess < 0) genProcess = val;
      else if (genProcess != val) {
        f.format("  genProcess %d != %d ", genProcess, val);
        gr.show(f);
        f.format(" %s%n", gr.getId());
      }

      val = gr.getPDS().getBackProcessId();
      if (backProcess < 0) backProcess = val;
      else if (backProcess != val) {
        f.format("  backProcess %d != %d ", backProcess, val);
        gr.show(f);
        f.format(" %s%n", gr.getId());
      }

    }
  }

  ///////////////////////////////////////////////

  private void doDrsSummary(Formatter f, CollectionManager dcm, boolean useIndex) throws IOException {
    f.format("Show Unique DRS Templates%n");
    Counter template = new Counter("DRS template");
    Counter prob = new Counter("DRS template 40 signed problem");

    for (MFile mfile : dcm.getFiles()) {
      f.format(" %s%n", mfile.getPath());
      doDrsSummary(f, mfile, useIndex, template, prob);
    }

    template.show(f);
    prob.show(f);
  }

  private void doDrsSummary(Formatter f, MFile mf, boolean useIndex, Counter templateC, Counter probC) throws IOException {
    Grib2Index index = createIndex(mf, f);
    if (index == null) return;

    String path = mf.getPath();
    RandomAccessFile raf = new RandomAccessFile(path, "r");

    for (ucar.nc2.grib.grib2.Grib2Record gr : index.getRecords()) {
      Grib2SectionDataRepresentation drss = gr.getDataRepresentationSection();
      int template = drss.getDataTemplate();
      templateC.count(template);

      if (useIndex && template == 40) {  // expensive
        Grib2Drs.Type40 drs40 = gr.readDataTest(raf);
        if (drs40 != null) {
          if (drs40.hasSignedProblem())
            probC.count(1);
          else
            probC.count(0);
        }
      }
    }
    raf.close();
  }

  ///////////////////////////////////////////////

  private void doGdsTemplate(Formatter f, CollectionManager dcm, boolean useIndex) throws IOException {
    f.format("Show Unique GDS Templates%n");

    Map<Integer, Integer> drsSet = new HashMap<Integer, Integer>();
    for (MFile mfile : dcm.getFiles()) {
      f.format(" %s%n", mfile.getPath());
      doGdsTemplate(f, mfile, drsSet);
    }

    for (int template : drsSet.keySet()) {
      int count = drsSet.get(template);
      f.format("%nGDS template = %d count = %d%n", template, count);
    }
  }

  private void doGdsTemplate(Formatter f, MFile mf, Map<Integer, Integer> gdsSet) throws IOException {
    Grib2Index index = createIndex(mf, f);
    if (index == null) return;

    for (Grib2SectionGridDefinition gds : index.getGds()) {
      int template = gds.getGDSTemplateNumber();
      Integer count = gdsSet.get(template);
      if (count == null)
        gdsSet.put(template, 1);
      else
        gdsSet.put(template, count + 1);
    }
  }

  ///////////////////////////////////////////////////////////////////

  private void doTimeCoord(Formatter f, CollectionManager dcm, boolean useIndex) throws IOException {
    Counter templateSet = new Counter("template");
    Counter timeUnitSet = new Counter("timeUnit");
    Counter statTypeSet = new Counter("statType");
    Counter NTimeIntervals = new Counter("NumberTimeIntervals");
    Counter TinvDiffer = new Counter("TimeIntervalsDiffer");
    Counter TinvLength = new Counter("TimeIntervalsLength");

    int count = 0;
    for (MFile mfile : dcm.getFiles()) {
      f.format(" %s%n", mfile.getPath());
      count += doTimeCoord(f, mfile, templateSet, timeUnitSet, statTypeSet, NTimeIntervals, TinvDiffer, TinvLength);
    }

    f.format("total records = %d%n", count);

    templateSet.show(f);
    timeUnitSet.show(f);
    statTypeSet.show(f);
    NTimeIntervals.show(f);
    TinvDiffer.show(f);
    TinvLength.show(f);
  }


  private int doTimeCoord(Formatter f, MFile mf, Counter templateSet, Counter timeUnitSet, Counter statTypeSet, Counter NTimeIntervals,
                          Counter TinvDiffer, Counter TinvLength) throws IOException {
    boolean showTinvDiffers = true;
    boolean showNint = true;
    boolean shutup = false;

    Grib2Index index = createIndex(mf, f);
    if (index == null) return 0;
    Grib2Customizer cust = null;

    int count = 0;
    for (ucar.nc2.grib.grib2.Grib2Record gr : index.getRecords()) {
      Grib2Pds pds = gr.getPDS();
      templateSet.count(pds.getTemplateNumber());
      int timeUnit = pds.getTimeUnit();
      timeUnitSet.count(timeUnit);

      if (pds instanceof Grib2Pds.PdsInterval) {
        Grib2Pds.PdsInterval pdsi = (Grib2Pds.PdsInterval) pds;
        for (Grib2Pds.TimeInterval ti : pdsi.getTimeIntervals()) {
          statTypeSet.count(ti.statProcessType);

          if ((ti.timeRangeUnit != timeUnit) || (ti.timeIncrementUnit != timeUnit && ti.timeIncrementUnit != 255 && ti.timeIncrement != 0)) {
            TinvDiffer.count(ti.timeRangeUnit);
            if (showTinvDiffers) {
              f.format("  TimeInterval has different units timeUnit= %s file=%s%n  ", timeUnit, mf.getName());
              pds.show(f);
              f.format("%n");
            }
          }
        }

        NTimeIntervals.count(pdsi.getTimeIntervals().length);
        if (showNint && !shutup && pdsi.getTimeIntervals().length > 1) {
          f.format("  TimeIntervals > 1 = %s file=%s%n  ", getId(gr), mf.getName());
          shutup = true;
        }

        if (cust == null) cust = Grib2Customizer.factory(gr);
        double len = cust.getForecastTimeIntervalSizeInHours(gr);
        TinvLength.count((int) len);
        int[] intv = cust.getForecastTimeIntervalOffset(gr);
        if ((intv[0] == 0) && (intv[1] == 0)) {
          f.format("  TimeInterval [0,0] = %s file=%s%n  ", getId(gr), mf.getName());
        }
      }

      count++;
    }

    return count;
  }

  String getId(Grib2Record gr) {
    Grib2SectionIndicator is = gr.getIs();
    Grib2Pds pds = gr.getPDS();
    return is.getDiscipline()+"-"+pds.getParameterCategory()+ "-" + pds.getParameterNumber();
  }

  ///////////////////////////////////////////////////////////////////////////////////

  private void doNames(Formatter f, CollectionManager dcm, boolean useIndex) throws IOException {
    f.format("CHECK Grib-2 Names: Old vs New for collection %s%n", dcm.getCollectionName());

    Map<String,List<String>> gridsAll = new HashMap<String,List<String>>(1000); // old -> list<new>
    int countExactMatch = 0;
    int countExactMatchIg = 0;
    int countOldVars = 0;

    for (MFile mfile : dcm.getFiles()) {
      f.format("%n%s%n", mfile.getPath());
      Map<Integer,GridMatch> gridsNew = getGridsNew(mfile, f);
      Map<Integer,GridMatch> gridsOld = getGridsOld(mfile, f);

     // look for exact match on name
      Set<String> namesNew = new HashSet<String>(  gridsNew.size());
      for (GridMatch gm : gridsNew.values()) 
        namesNew.add(gm.grid.getFullName());
      for (GridMatch gm : gridsOld.values()) {
        if (namesNew.contains(gm.grid.getFullName())) countExactMatch++;
        countOldVars++;
      }

      // look for exact match on hashcode
      for (GridMatch gm : gridsNew.values()) {
        GridMatch match = gridsOld.get(gm.hashCode());
        if (match != null) {
          gm.match = match;
          match.match = gm;
        }
      }

      // look for alternative match
      for (GridMatch gm : gridsNew.values()) {
        if (gm.match == null) {
          GridMatch match = altMatch(gm, gridsOld.values());
          if (match != null) {
            gm.match = match;
            match.match = gm;
          }
        }
      }

      f.format("%n");
      List<GridMatch> listNew = new ArrayList<GridMatch>(gridsNew.values());
      Collections.sort(listNew);
      for (GridMatch gm : listNew) {
        f.format(" %s%n", gm.grid.findAttributeIgnoreCase(Grib2Iosp.VARIABLE_ID_ATTNAME));
        f.format(" %s%n", gm.grid.getFullName());
        if (gm.match != null) {
          boolean exact =  gm.match.grid.getFullName().equals(gm.grid.getFullName());
          boolean exactIg =  !exact && gm.match.grid.getFullName().equalsIgnoreCase(gm.grid.getFullName());
          if (exactIg) countExactMatchIg++;
          String status = exact ? " " : exactIg ? "**" : " *";
          f.format("%s%s%n", status, gm.match.grid.getFullName());
        }
        f.format("%n");
      }

      f.format("%nMISSING MATCHES IN NEW%n");
      List<GridMatch> list = new ArrayList<GridMatch>(gridsNew.values());
      Collections.sort(list);
      for (GridMatch gm : list) {
        if (gm.match == null)
          f.format(" %s (%s) == %s%n", gm.grid.getFullName(), gm.show(), gm.grid.getDescription());
      }


      f.format("%nMISSING MATCHES IN OLD%n");
      List<GridMatch> listOld = new ArrayList<GridMatch>(gridsOld.values());
      Collections.sort(listOld);
      for (GridMatch gm : listOld) {
        if (gm.match == null)
          f.format(" %s (%s)%n", gm.grid.getFullName(), gm.show());
      }

      // add to gridsAll
       for (GridMatch gmOld : listOld) {
         String key = gmOld.grid.getFullName();
         List<String> newGrids = gridsAll.get(key);
         if (newGrids == null) {
           newGrids = new ArrayList<String>();
           gridsAll.put(key, newGrids);
         }
         if (gmOld.match != null) {
           String keyNew = gmOld.match.grid.getFullName()+" == "+gmOld.match.grid.getDescription();
           if (!newGrids.contains(keyNew)) newGrids.add(keyNew);
         }
      }
    }

    f.format("%nOLD -> NEW MAPPINGS%n");
    List<String> keys = new ArrayList<String>(gridsAll.keySet());
    int total = keys.size();
    int dups = 0;
    Collections.sort(keys);
    for (String key : keys) {
      f.format(" OLD %s%n", key);
      List<String> newGrids = gridsAll.get(key);
      Collections.sort(newGrids);
      if (newGrids.size() > 1) dups++;
      for (String newKey : newGrids)
        f.format(" NEW %s%n", newKey);
      f.format("%n");
    }

    f.format("Exact matches=%d  Exact ignore case=%d  totalOldVars=%d%n", countExactMatch,  countExactMatchIg, countOldVars);
    f.format("Number with more than one map=%d total=%d%n", dups, total);
  }

  private GridMatch altMatch(GridMatch want, Collection<GridMatch> test) {
    // look for scale factor errors in prob
    for (GridMatch gm : test) {
      if (gm.match != null) continue; // already matched
      if (gm.altMatch(want)) return gm;
    }

    // give up matching the prob
    for (GridMatch gm : test) {
      if (gm.match != null) continue; // already matched
      if (gm.altMatchNoProb(want)) return gm;
    }

    return null;
  }

  private class GridMatch implements Comparable<GridMatch> {
    GridDatatype grid;
    GridMatch match;
    boolean isNew;
    int[] param = new int[3];
    int level;
    boolean isLayer, isError;
    int interval = -1;
    int prob = -1;
    int ens = -1;
    int probLimit;

    private GridMatch(GridDatatype grid, boolean aNew) {
      this.grid = grid;
      isNew = aNew;

      GridCoordSystem gcs = grid.getCoordinateSystem();
      CoordinateAxis1D zaxis = gcs.getVerticalAxis();
      if (zaxis != null) isLayer = zaxis.isInterval();

      if (isNew) {
        Attribute att = grid.findAttributeIgnoreCase("Grib2_Parameter");
        for (int i=0; i<3; i++)
          param[i] = att.getNumericValue(i).intValue();

        att = grid.findAttributeIgnoreCase("Grib2_Level_Type");
        level = att.getNumericValue().intValue();
        isError = grid.getName().contains("error");

        att = grid.findAttributeIgnoreCase("Grib2_Statistical_Interval_Type");
        if (att != null) {
          int intv = att.getNumericValue().intValue();
          if (intv != 255) interval = intv;
        }

        att = grid.findAttributeIgnoreCase("Grib2_Probability_Type");
        if (att != null) prob = att.getNumericValue().intValue();

        att = grid.findAttributeIgnoreCase("Grib2_Probability_Name"); // :Grib2_Probability_Name = "above_17.5";
        if (att != null) {
          String pname = att.getStringValue();
          int pos = pname.indexOf('_');
          pname = pname.substring(pos+1);
          probLimit = (int) (1000.0 * Double.parseDouble(pname));
        }

        att = grid.findAttributeIgnoreCase("Grib2_Ensemble_Derived_Type");
        if (att != null) ens = att.getNumericValue().intValue();

      } else {
        Attribute att = grid.findAttributeIgnoreCase("GRIB_param_id");
        for (int i=0; i<3; i++)
          param[i] = att.getNumericValue(i+1).intValue();

        att = grid.findAttributeIgnoreCase("GRIB_level_type");
        level = att.getNumericValue().intValue();
        isError = grid.getName().contains("error");

        att = grid.findAttributeIgnoreCase("GRIB_interval_stat_type");
        if (att != null) {
          String intName = att.getStringValue();
          interval = GribStatType.getStatTypeNumber(intName);
        }

        att = grid.findAttributeIgnoreCase("GRIB_probability_type");
        if (att != null) prob = att.getNumericValue().intValue();
        if (prob == 0) {
          att = grid.findAttributeIgnoreCase("GRIB_probability_lower_limit");
          if (att != null) probLimit = (int) (1000 * att.getNumericValue().doubleValue());
          //if (Math.abs(probLimit) > 100000) probLimit /= 1000; // wierd bug in 4.2
        } else if (prob == 1) {
          att = grid.findAttributeIgnoreCase("GRIB_probability_upper_limit"); // GRIB_probability_upper_limit = 12.89; // double
          if (att != null) probLimit = (int) (1000 * att.getNumericValue().doubleValue());
          //if (Math.abs(probLimit) > 100000) probLimit /= 1000; // wierd bug in 4.2
        }

        att = grid.findAttributeIgnoreCase("GRIB_ensemble_derived_type");
        if (att != null) ens = att.getNumericValue().intValue();
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      GridMatch gridMatch = (GridMatch) o;

      if (ens != gridMatch.ens) return false;
      if (interval != gridMatch.interval) return false;
      if (isError != gridMatch.isError) return false;
      if (isLayer != gridMatch.isLayer) return false;
      if (level != gridMatch.level) return false;
      if (prob != gridMatch.prob) return false;
      if (probLimit != gridMatch.probLimit) return false;
      if (!Arrays.equals(param, gridMatch.param)) return false;

      return true;
    }

    public boolean altMatch(GridMatch gridMatch) {
      if (!altMatchNoProb(gridMatch)) return false;

      if (probLimit/1000 == gridMatch.probLimit) return true;
      if (probLimit == gridMatch.probLimit/1000) return true;

      return false;
    }

    public boolean altMatchNoProb(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      GridMatch gridMatch = (GridMatch) o;

      if (ens != gridMatch.ens) return false;
      if (interval != gridMatch.interval) return false;
      if (isError != gridMatch.isError) return false;
      if (isLayer != gridMatch.isLayer) return false;
      if (level != gridMatch.level) return false;
      if (prob != gridMatch.prob) return false;

      return true;
    }


    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + level;
      result = 31 * result + param[0];
      result = 31 * result + (isLayer ? 1 : 0);
      result = 31 * result + param[1];
      result = 31 * result + (isError ? 1 : 0);
      result = 31 * result + interval;
      result = 31 * result + prob;
      result = 31 * result + param[2];
      result = 31 * result + ens;
      result = 31 * result + probLimit;
      return result;
    }

    @Override
    public int compareTo(GridMatch o) {
      return grid.compareTo(o.grid);
    }

    String show() {
      Formatter f = new Formatter();
      for (int i=0; i<3; i++)
        f.format("%d-", param[i]);
      f.format("%d", level);
      if (isLayer) f.format("_layer");
      if (interval >= 0) f.format("_intv%d",interval);
      if (prob >= 0) f.format("_prob%d_%d",prob,probLimit);
      if (ens >= 0) f.format("_ens%d",ens);
      if (isError) f.format("_error");
      return f.toString();
    }
  }

  private Map<Integer,GridMatch> getGridsNew(MFile ff, Formatter f) throws IOException {
    Map<Integer,GridMatch> grids = new HashMap<Integer,GridMatch>(100);
    GridDataset ncfile = null;
    try {
      ncfile = GridDataset.open(ff.getPath());
      for (GridDatatype dt : ncfile.getGrids()) {
        GridMatch gm = new GridMatch(dt, true);
        GridMatch dup = grids.get(gm.hashCode());
        if (dup != null)
          f.format(" DUP NEW (%d == %d) = %s (%s) and DUP %s (%s)%n", gm.hashCode(), dup.hashCode(), gm.grid.getFullName(), gm.show(), dup.grid.getFullName(), dup.show());
        else
          grids.put(gm.hashCode(), gm);
      }
    } finally {
      if (ncfile != null) ncfile.close();
    }
    return grids;
  }

  private Map<Integer,GridMatch> getGridsOld(MFile ff, Formatter f) throws IOException {
    Map<Integer,GridMatch> grids = new HashMap<Integer,GridMatch>(100);
    NetcdfFile ncfile = null;
    try {
      ncfile = NetcdfFile.open(ff.getPath(), "ucar.nc2.iosp.grib.GribServiceProvider", -1, null, null);
      NetcdfDataset ncd = new NetcdfDataset(ncfile);
      GridDataset grid = new GridDataset(ncd);
      for (GridDatatype dt : grid.getGrids()) {
        GridMatch gm = new GridMatch(dt, false);
        GridMatch dup = grids.get(gm.hashCode());
        if (dup != null)
          f.format(" DUP OLD (%d == %d) = %s (%s) and DUP %s (%s)%n", gm.hashCode(), dup.hashCode(), gm.grid.getFullName(), gm.show(), dup.grid.getFullName(), dup.show());
        else
          grids.put(gm.hashCode(), gm);
      }
    } catch (Throwable t) {
      t.printStackTrace();
    } finally {
      if (ncfile != null) ncfile.close();
    }
    return grids;
  }

}
