/*
 * Copyright (c) 1998-2018 University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */
package ucar.nc2.ncml;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Sets;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.output.Format;
import org.jdom2.output.LineSeparator;
import org.jdom2.output.XMLOutputter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import thredds.client.catalog.Catalog;
import ucar.ma2.Array;
import ucar.ma2.DataType;
import ucar.ma2.Index;
import ucar.ma2.IndexIterator;
import ucar.nc2.*;
import ucar.nc2.util.URLnaming;
import ucar.nc2.util.xml.Parse;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Helper class to write NcML.
 *
 * @author caron
 * @author cwardgar
 * @see ucar.nc2.NetcdfFile
 * @see <a href="http://www.unidata.ucar.edu/software/netcdf/ncml/">http://www.unidata.ucar.edu/software/netcdf/ncml/</a>
 */
public class NcMLWriter {
  /**
   * A default namespace constructed from the NcML URI: {@code http://www.unidata.ucar.edu/namespaces/netcdf/ncml-2.2}.
   */
  // A default namespace means that we can use it without having to prepend the "ncml:" prefix to every element name.
  // thredds.client.catalog.Catalog.ncmlNS is *not* default and therefore *does* require the prefix.
  public static final Namespace ncmlDefaultNamespace = Namespace.getNamespace(Catalog.NJ22_NAMESPACE);

  private static final Logger log = LoggerFactory.getLogger(NcMLWriter.class);

  //////////////////////////////////////// Variable-writing predicates ////////////////////////////////////////

  /** Predicate that always returns {@code false}. */
  public static final Predicate<Variable> writeNoVariablesPredicate = Predicates.alwaysFalse();

  /**
   * Predicate that returns {@code true} for variables that are {@link Variable#isMetadata() metadata variables}.
   * For such variables, the data is not actually present in the file, so we must include it in the NcML.
   * It could be synthesized (i.e. generated by an IOSP) or specified in a <values> element of the input NcML.
   * */
  public static final Predicate<Variable> writeMetadataVariablesPredicate = Variable::isMetadata;

  /**
   * Predicate that returns {@code true} for variables that are
   * {@link Variable#isCoordinateVariable() coordinate variables}.
   **/
  public static final Predicate<Variable> writeCoordinateVariablesPredicate = Variable::isCoordinateVariable;

  /** Predicate that always returns {@code true}. */
  public static final Predicate<Variable> writeAllVariablesPredicate = Predicates.alwaysTrue();

  /** Predicate that returns {@code true} for variables whose names are specified to the constructor. */
  public static class WriteVariablesWithNamesPredicate implements Predicate<Variable> {
    private final Set<String> variableNames;

    public WriteVariablesWithNamesPredicate(Iterable<String> variableNames) {
      this.variableNames = Sets.newHashSet(variableNames);
    }

    @Override
    public boolean apply(Variable var) {
      return variableNames.contains(var.getFullName());
    }
  }

  //////////////////////////////////////// Instance variables ////////////////////////////////////////

  private Namespace namespace;
  private Format xmlFormat;
  private Predicate<Variable> writeVariablesPredicate;

  private final XMLOutputter xmlOutputter = new XMLOutputter();

  //////////////////////////////////////// Constructors ////////////////////////////////////////

  public NcMLWriter() {
    this.namespace = ncmlDefaultNamespace;
    this.xmlFormat = Format.getPrettyFormat().setLineSeparator(LineSeparator.UNIX);
    this.writeVariablesPredicate = writeMetadataVariablesPredicate;
  }

  //////////////////////////////////////// Getters and setters ////////////////////////////////////////

  /**
   * Gets the XML namespace for the elements in the NcML. By default, it is {@link #ncmlDefaultNamespace}.
   *
   * @return  the XML namespace.
   */
  public Namespace getNamespace() {
    return namespace;
  }

  /**
   * Sets the XML namespace.
   *
   * @param namespace  the new namespace. {@code null} implies {@link Namespace#NO_NAMESPACE}.
   */
  public void setNamespace(Namespace namespace) {
    this.namespace = namespace == null ? Namespace.NO_NAMESPACE : namespace;
  }

  /**
   * Gets the object that encapsulates XML formatting options. By default, the format is
   * {@link Format#getPrettyFormat() pretty} with {@link LineSeparator#UNIX UNIX line separators}.
   *
   * @return  the XML formatting options.
   */
  public Format getXmlFormat() {
    return xmlFormat;
  }

  public void setXmlFormat(Format xmlFormat) {
    this.xmlFormat = Preconditions.checkNotNull(xmlFormat);
  }

  /**
   * Gets the predicate that will be applied to variables to determine wither their values should be written in
   * addition to their metadata. The values will be contained within a {@code <values>} element.
   *
   * @return the predicate.
   */
  public Predicate<Variable> getWriteVariablesPredicate() {
    return writeVariablesPredicate;
  }

  /**
   * Sets the predicate that will be applied to variables to determine whether their values should be written in
   * addition to their metadata. The values will be contained within a {@code <values>} element.
   * <p/>
   * By default, the predicate will be {@link #writeMetadataVariablesPredicate}. Their could be data loss if the values
   * of metadata variables aren't included in the NcML, so we recommend that you always use it, possibly as part of a
   * compound predicate. For example, suppose you wanted to print the values of metadata <b>and</b> coordinate
   * variables. Just do:
   * <pre>
   * Predicate<Variable> compoundPred = Predicates.or(
   *         writeMetadataVariablesPredicate,
   *         writeCoordinateVariablesPredicate);
   * ncmlWriter.setWriteVariablesPredicate(compoundPred);
   * </pre>
   *
   * @param predicate  the predicate to apply.
   */
  public void setWriteVariablesPredicate(Predicate<Variable> predicate) {
    this.writeVariablesPredicate = Preconditions.checkNotNull(predicate);
  }

  //////////////////////////////////////// Writing ////////////////////////////////////////

  /**
   * Writes an NcML element to a string.
   *
   * @param elem an NcML element.
   * @return the string that represents the NcML document.
   */
  public String writeToString(Element elem) {
    try (StringWriter writer = new StringWriter()) {
      writeToWriter(elem, writer);
      return writer.toString();
    } catch (IOException e) {
      throw new AssertionError("CAN'T HAPPEN: StringWriter.close() is a no-op.", e);
    }
  }

  /**
   * Writes an NcML element to an output file.
   *
   * @param elem  an NcML element.
   * @param outFile  the file to write the NcML document to.
   * @throws IOException  if there's any problem writing.
   */
  public void writeToFile(Element elem, File outFile) throws IOException {
    try (OutputStream outStream = new BufferedOutputStream(new FileOutputStream(outFile, false))) {
      writeToStream(elem, outStream);
    }
  }

  /**
   * Writes an NcML element to an output stream.
   *
   * @param elem  an NcML element.
   * @param outStream  the stream to write the NcML document to. Will be closed at end of the method.
   * @throws IOException  if there's any problem writing.
   */
  public void writeToStream(Element elem, OutputStream outStream) throws IOException {
    try (Writer writer = new BufferedWriter(new OutputStreamWriter(
            new BufferedOutputStream(outStream), xmlFormat.getEncoding()))) {
      writeToWriter(elem, writer);
    }
  }

  /**
   * Writes an NcML element to a Writer.
   *
   * @param elem  an NcML element.
   * @param writer  the Writer to write the NcML document to. Will be closed at end of the method.
   * @throws IOException  if there's any problem writing.
   */
  public void writeToWriter(Element elem, Writer writer) throws IOException {
    xmlOutputter.setFormat(xmlFormat);
    elem.detach();  // In case this element had previously been added to a Document.
    xmlOutputter.output(new Document(elem), writer);
  }

  //////////////////////////////////////// Element creation ////////////////////////////////////////

  public Element makeExplicitNetcdfElement(NetcdfFile ncFile, String location) {
    Element netcdfElem = makeNetcdfElement(ncFile, location);
    netcdfElem.addContent(0, new Element("explicit", namespace));
    return netcdfElem;
  }

  public Element makeNetcdfElement(NetcdfFile ncFile, String location) {
    Element rootElem = makeGroupElement(ncFile.getRootGroup());

    // rootElem isn't just like any other group element; we must undo some of the changes made to it in writeGroup().
    rootElem.setName("netcdf");         // Was "group".
    rootElem.removeAttribute("name");   // This attribute is not defined on the root "netcdf" element.

    rootElem.addNamespaceDeclaration(namespace);

    if (null == location)
      location = ncFile.getLocation();

    if (null != location) {
      rootElem.setAttribute("location", URLnaming.canonicalizeWrite(location));
    }

    if (null != ncFile.getId())
      rootElem.setAttribute("id", ncFile.getId());

    if (null != ncFile.getTitle())
      rootElem.setAttribute("title", ncFile.getTitle());

    return rootElem;
  }

  public Element makeGroupElement(Group group) {
    Element elem = new Element("group", namespace);
    elem.setAttribute("name", group.getShortName());

    // enumTypeDef
    for (EnumTypedef etd : group.getEnumTypedefs()) {
      elem.addContent(makeEnumTypedefElement(etd));
    }

    // dimensions
    for (Dimension dim : group.getDimensions()) {
      elem.addContent(makeDimensionElement(dim));
    }

    // regular variables
    for (Variable var : group.getVariables()) {
      boolean showValues = writeVariablesPredicate.apply(var);
      elem.addContent(makeVariableElement(var, showValues));
    }

    // nested groups
    for (Group g : group.getGroups()) {
      Element groupElem = new Element("group", namespace);
      groupElem.setAttribute("name", g.getShortName());
      elem.addContent(makeGroupElement(g));
    }

    // attributes
    for (Attribute att : group.getAttributes()) {
      elem.addContent(makeAttributeElement(att));
    }

    return elem;
  }

  // enum Typedef
  public Element makeEnumTypedefElement(EnumTypedef etd) {
    Element typeElem = new Element("enumTypedef", namespace);
    typeElem.setAttribute("name", etd.getShortName());
    typeElem.setAttribute("type", etd.getBaseType().toString());

    // Use a TreeMap so that the key-value pairs are emitted in a consistent order.
    TreeMap<Integer, String> map = new TreeMap<>(etd.getMap());

    for (Map.Entry<Integer, String> entry : map.entrySet()) {
      typeElem.addContent(new Element("enum", namespace)
              .setAttribute("key", Integer.toString(entry.getKey()))
              .addContent(entry.getValue()));
    }

    return typeElem;
  }

  // Only for shared dimensions.
  public Element makeDimensionElement(Dimension dim) throws IllegalArgumentException {
    if (!dim.isShared()) {
      throw new IllegalArgumentException("Cannot create private dimension: " +
              "in NcML, <dimension> elements are always shared.");
    }

    Element dimElem = new Element("dimension", namespace);
    dimElem.setAttribute("name", dim.getShortName());
    dimElem.setAttribute("length", Integer.toString(dim.getLength()));

    if (dim.isUnlimited())
      dimElem.setAttribute("isUnlimited", "true");

    return dimElem;
  }

  public Element makeVariableElement(Variable var, boolean showValues) {
    boolean isStructure = var instanceof Structure;

    Element varElem = new Element("variable", namespace);
    varElem.setAttribute("name", var.getShortName());

    StringBuilder buff = new StringBuilder();
    List dims = var.getDimensions();
    for (int i = 0; i < dims.size(); i++) {
      Dimension dim = (Dimension) dims.get(i);
      if (i > 0) buff.append(" ");
      if (dim.isShared())
        buff.append(dim.getShortName());
      else if (dim.isVariableLength())
        buff.append("*");
      else
        buff.append(dim.getLength());
    }
    //if (buff.length() > 0)
    varElem.setAttribute("shape", buff.toString());

    DataType dt = var.getDataType();
    if (dt != null) {
      varElem.setAttribute("type", dt.toString());
      if (dt.isEnum())
        varElem.setAttribute("typedef", var.getEnumTypedef().getShortName());
    }


    // attributes
    for (Attribute att : var.getAttributes()) {
      varElem.addContent(makeAttributeElement(att));
    }

    if (isStructure) {
      Structure s = (Structure) var;
      for (Variable variable : s.getVariables()) {
        varElem.addContent(makeVariableElement(variable, showValues));
      }
    } else if (showValues) {
      try {
        varElem.addContent(makeValuesElement(var, true));
      } catch (IOException e) {
        String message = String.format("Couldn't read values for %s. Omitting <values> element.%n\t%s",
                                       var.getFullName(), e.getMessage());
        log.warn(message);
      }
    }

    return varElem;
  }

  public Element makeAttributeElement(Attribute attribute) {
    Element attElem = new Element("attribute", namespace);
    attElem.setAttribute("name", attribute.getShortName());

    DataType dt = attribute.getDataType();
    if ((dt != null) && (dt != DataType.STRING))
      attElem.setAttribute("type", dt.toString());

    if (attribute.getLength() == 0) {
      //if (attribute.isUnsigned())
      //  attElem.setAttribute("isUnsigned", "true");
      return attElem;
    }

    if (attribute.isString()) {
      StringBuilder buff = new StringBuilder();
      for (int i = 0; i < attribute.getLength(); i++) {
        String sval = attribute.getStringValue(i);
        if (i > 0) buff.append("|");
        buff.append(sval);
      }

      attElem.setAttribute("value", Parse.cleanCharacterData(buff.toString()));
      if (attribute.getLength() > 1)
        attElem.setAttribute("separator", "|");

    } else {
      StringBuilder buff = new StringBuilder();
      for (int i = 0; i < attribute.getLength(); i++) {
        Number val = attribute.getNumericValue(i);
        if (i > 0) buff.append(" ");
        buff.append(val.toString());
      }
      attElem.setAttribute("value", buff.toString());

      //if (attribute.isUnsigned())
      //  attElem.setAttribute("isUnsigned", "true");
    }
    return attElem;
  }

  /**
   * Creates a {@code <values>} element from the variable's data.
   *
   * @param variable  the variable to read values from
   * @param allowRegular  {@code true} if regular values should be represented with {@code start}, {@code increment},
   *     and {@code npts} attributes instead of space-separated Element text. Has no effect if the data isn't regular.
   * @return  the {@code <values>} element.
   * @throws IOException  if there was an I/O error when reading the variable's data.
   */
  public Element makeValuesElement(Variable variable, boolean allowRegular) throws IOException {
    Element elem = new Element("values", namespace);

    StringBuilder buff = new StringBuilder();
    Array a = variable.read();

    if (variable.getDataType() == DataType.CHAR) {
      char[] data = (char[]) a.getStorage();
      elem.setText(new String(data));
    } else if (variable.getDataType() == DataType.STRING) {
      elem.setAttribute("separator", "|");
      int count = 0;

      for (IndexIterator iter = a.getIndexIterator(); iter.hasNext(); ) {
        if (count++ > 0) {
          buff.append("|");
        }
        buff.append(iter.getObjectNext());
      }

      elem.setText(buff.toString());
    } else {
      //check to see if regular
      if (allowRegular && (a.getRank() == 1) && (a.getSize() > 2)) {
        Index ima = a.getIndex();
        double start = a.getDouble(ima.set(0));
        double incr = a.getDouble(ima.set(1)) - start;
        boolean isRegular = true;
        for (int i = 2; i < a.getSize(); i++) {
          double v1 = a.getDouble(ima.set(i));
          double v0 = a.getDouble(ima.set(i - 1));
          if (!ucar.nc2.util.Misc.nearlyEquals(v1 - v0, incr))
            isRegular = false;
        }

        if (isRegular) {
          elem.setAttribute("start", Double.toString(start));
          elem.setAttribute("increment", Double.toString(incr));
          elem.setAttribute("npts", Long.toString(variable.getSize()));
          return elem;
        }
      }

      // not regular
      boolean isRealType = (variable.getDataType() == DataType.DOUBLE) || (variable.getDataType() == DataType.FLOAT);
      IndexIterator iter = a.getIndexIterator();
      buff.append(isRealType ? iter.getDoubleNext() : iter.getIntNext());
      while (iter.hasNext()) {
        buff.append(" ");
        buff.append(isRealType ? iter.getDoubleNext() : iter.getIntNext());
      }
      elem.setText(buff.toString());

    } // not string

    return elem;
  }
}
