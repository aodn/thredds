/*
 * Copyright 1998-2015 University Corporation for Atmospheric Research/Unidata
 *
 *   Portions of this software were developed by the Unidata Program at the
 *   University Corporation for Atmospheric Research.
 *
 *   Access and use of this software shall impose the following obligations
 *   and understandings on the user. The user is granted the right, without
 *   any fee or cost, to use, copy, modify, alter, enhance and distribute
 *   this software, and any derivative works thereof, and its supporting
 *   documentation for any purpose whatsoever, provided that this entire
 *   notice appears in all copies of the software, derivative works and
 *   supporting documentation.  Further, UCAR requests that the user credit
 *   UCAR/Unidata in any publications that result from the use of this
 *   software or in any product that includes this software. The names UCAR
 *   and/or Unidata, however, may not be used in any advertising or publicity
 *   to endorse or promote any products or commercial entity unless specific
 *   written permission is obtained from UCAR/Unidata. The user also
 *   understands that UCAR/Unidata is not obligated to provide the user with
 *   any support, consulting, training or assistance of any kind with regard
 *   to the use, operation and performance of this software nor to provide
 *   the user with any updates, revisions, new versions or "bug fixes."
 *
 *   THIS SOFTWARE IS PROVIDED BY UCAR/UNIDATA "AS IS" AND ANY EXPRESS OR
 *   IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *   WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *   DISCLAIMED. IN NO EVENT SHALL UCAR/UNIDATA BE LIABLE FOR ANY SPECIAL,
 *   INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING
 *   FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT,
 *   NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION
 *   WITH THE ACCESS, USE OR PERFORMANCE OF THIS SOFTWARE.
 */
package thredds.client.catalog;

import net.jcip.annotations.Immutable;
import thredds.catalog.*;
import ucar.nc2.constants.DataFormatType;
import ucar.nc2.stream.CdmRemote;

import java.net.URI;

/**
 * <xsd:element name="access">
   <xsd:complexType>
     <xsd:sequence>
       <xsd:element ref="dataSize" minOccurs="0"/>
     </xsd:sequence>
     <xsd:attribute name="urlPath" type="xsd:token" use="required"/>
     <xsd:attribute name="serviceName" type="xsd:string"/>
     <xsd:attribute name="dataFormat" type="dataFormatTypes"/>
   </xsd:complexType>
 </xsd:element >
 *
 * @author caron
 * @since 1/7/2015
 */
@Immutable
public class Access {                 // (5)
  private final Dataset dataset;
  private final String urlPath;
  private final Service service;
  private final DataFormatType dataFormat;
  private final long dataSize;

  public Access(Dataset dataset, String urlPath, Service service, DataFormatType dataFormat, long dataSize) {
    this.dataset = dataset;
    this.urlPath = urlPath;
    this.service = service;
    this.dataFormat = dataFormat;
    this.dataSize = dataSize;
  }

  public Dataset getDataset() {
    return dataset;
  }

  public String getUrlPath() {
    return urlPath;
  }

  public Service getService() {
    return service;
  }

  public DataFormatType getDataFormat() {
    return dataFormat;
  }

  public long getDataSize() {
    return dataSize;
  }

  /**
   * Get the standard URL, with resolution if the URL is reletive.
   * catalog.resolveURI( getUnresolvedUrlName())
   *
   * @return URL string, or null if error.
   */
  public String getStandardUrlName() {
    URI uri = getStandardUri();
    if (uri == null) return null;
    return uri.toString();
  }

  /**
   * Construct the standard THREDDS access URI for this dataset access method,
   * resolve if the URI is relative.
   *
   * @return the standard fully resolved THREDDS access URI for this dataset access method, or null if error.
   */
  public URI getStandardUri() {
    try {
      Catalog cat = dataset.getParentCatalog();
      if (cat == null)
        return new URI(getUnresolvedUrlName());

      return cat.resolveUri(getUnresolvedUrlName());

    } catch (java.net.URISyntaxException e) {
      throw new RuntimeException("Error parsing URL= " + getUnresolvedUrlName());
    }
  }

  /**
   * Construct "unresolved" URL: service.getBase() + getUrlPath() + service.getSuffix().
   * It is not resolved, so it may be a reletive URL.
   * @return unresolved Url as a String
   */
  public String getUnresolvedUrlName() {
    return service.getBase() + getUrlPath() + service.getSuffix();
  }

  public String getWrappedUrlName() {
    URI uri = getStandardUri();
    if (uri == null) return null;

    if (service.getType() == ServiceType.THREDDS)
      return ucar.nc2.thredds.ThreddsDataFactory.SCHEME + uri;
    if (service.getType() == ServiceType.CdmRemote)
      return CdmRemote.SCHEME + uri;
    return uri.toString();
  }

}
