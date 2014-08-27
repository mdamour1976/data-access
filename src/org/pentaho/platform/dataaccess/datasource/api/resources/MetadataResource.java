/*!
 * This program is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License, version 2.1 as published by the Free Software
 * Foundation.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this
 * program; if not, you can obtain a copy at http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 * or from the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * Copyright (c) 2002-2013 Pentaho Corporation..  All rights reserved.
 */

package org.pentaho.platform.dataaccess.datasource.api.resources;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static javax.ws.rs.core.MediaType.WILDCARD;
import static javax.ws.rs.core.Response.Status.UNAUTHORIZED;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.enunciate.jaxrs.ResponseCode;
import org.codehaus.enunciate.jaxrs.StatusCodes;
import org.pentaho.metadata.repository.IMetadataDomainRepository;
import org.pentaho.platform.api.engine.PentahoAccessControlException;
import org.pentaho.platform.dataaccess.datasource.api.DatasourceService;
import org.pentaho.platform.dataaccess.datasource.api.MetadataService;
import org.pentaho.platform.dataaccess.datasource.wizard.service.messages.Messages;
import org.pentaho.platform.engine.core.system.PentahoSessionHolder;
import org.pentaho.platform.engine.core.system.PentahoSystem;
import org.pentaho.platform.plugin.services.importer.PlatformImportException;
import org.pentaho.platform.plugin.services.metadata.IPentahoMetadataDomainRepositoryExporter;
import org.pentaho.platform.web.http.api.resources.FileResource;
import org.pentaho.platform.web.http.api.resources.JaxbList;

import com.sun.jersey.core.header.FormDataContentDisposition;
import com.sun.jersey.multipart.FormDataBodyPart;
import com.sun.jersey.multipart.FormDataParam;

public class MetadataResource {

  private static final Log logger = LogFactory.getLog( MetadataResource.class );
  private static final String OVERWRITE_IN_REPOS = "overwrite";
  private static final String SUCCESS = "3";

  private MetadataService service;
  private IMetadataDomainRepository metadataDomainRepository;
  
  public MetadataResource() {
    service = new MetadataService();
    metadataDomainRepository = PentahoSystem.get( IMetadataDomainRepository.class, PentahoSessionHolder.getSession() );
  }
  
  /**
   * Export a metadata datasource.
   *
   * <p><b>Example Request:</b><br/>
   *   GET /pentaho/plugin/data-access/api/datasource/metadata/FoodMart/download
   * </p>
   *
   * @param metadataId The id of the Metadata datasource to export
   *               <pre function="syntax.xml">
   *               {@code
   *               FoodMart
   *               }
   *               </pre>
   * @return A Response object containing the metadata xmi file.
   */
  @GET
  @Path( "/{metadataId : .+}/download" )
  @Produces( WILDCARD )
  @StatusCodes({
      @ResponseCode( code = 200, condition = "Metadata datasource export succeeded." ),
      @ResponseCode( code = 401, condition = "User is not authorized to export Metadata datasource." ),
      @ResponseCode( code = 500, condition = "Failure to export Metadata datasource." )
  })  
  public Response doGetMetadataFilesAsDownload( @PathParam( "metadataId" ) String metadataId ) {
    if( !DatasourceService.canAdminister() ) {
      return Response.status( UNAUTHORIZED ).build();
    }
    if ( !( metadataDomainRepository instanceof IPentahoMetadataDomainRepositoryExporter ) ) {
      return Response.serverError().build();
    }
    Map<String, InputStream> fileData = ( (IPentahoMetadataDomainRepositoryExporter) metadataDomainRepository ).getDomainFilesData( metadataId );
    return ResourceUtil.createAttachment( fileData, metadataId );
  }  

  /**
   * Remove the metadata for a given metadata ID
   *
   * <p><b>Example Request:</b><br/>
   *   POST /pentaho/plugin/data-access/api/datasource/metadata/FoodMart/delete
   * </p>
   *
   * @param metadataId The id of the Metadata datasource to remove
   *               <pre function="syntax.xml">
   *               {@code
   *               FoodMart
   *               }
   *               </pre>
   */
  @POST
  @Path( "/{metadataId : .+}/remove" )
  @Produces( WILDCARD )
  @StatusCodes({
    @ResponseCode( code = 200, condition = "Metadata datasource removed." ),
    @ResponseCode( code = 401, condition = "User is not authorized to delete the Metadata datasource." ),
  })      
  public Response doRemoveMetadata( @PathParam( "metadataId" ) String metadataId ) {
    try {
      service.removeMetadata( metadataId );
      return Response.ok().build();
    } catch ( PentahoAccessControlException e ) {
      return Response.status( UNAUTHORIZED ).build();
    }
  }

  /**
   * Get the Metadata datasource IDs
   *
   * <p><b>Example Request:</b><br/>
   *   GET /pentaho/plugin/data-access/api/datasource/metadata/ids
   * </p>
   *
   * @return JaxbList<String> of Metadata datasource IDs
   *               <pre function="syntax.xml">
   *               {@code
   *               {"Item":{"@type":"xs:string","$":"steel-wheels"}}
   *               }
   *               </pre>
   */
  @GET
  @Path( "/ids" )
  @Produces( { APPLICATION_XML, APPLICATION_JSON } )
  public JaxbList<String> getMetadataDatasourceIds() {
    return new JaxbList<String>( service.getMetadataDatasourceIds() );
  }
  
  /**
   * Import a Metadata datasource
   * 
   * @param domainId
   *          Unique identifier for the metadata datasource
   *          <pre function="syntax.xml">
   *          {@code
   *          {"Item":{"@type":"xs:string","$":"steel-wheels"}}
   *          }
   *          </pre>
   * @param metadataFile
   *          input stream for the metadata.xmi
   * @param metadataFileInfo
   *          User selected name for the file
   * @param localeFiles
   *          List of local files
   * @param localeFilesInfo
   *          List of information for each local file
   * @param overwrite
   *          Flag for overwriting existing version of the file
   *          <pre function="syntax.xml">
   *          {@code
   *          true
   *          }
   *          </pre>
   *
   * @return Response containing the success of the method, a response of:
   *  2: unspecified general error has occurred
   *  3: indicates successful import
   *  9: content already exists (use overwrite flag to force)
   * 10: import failed because publish is prohibited
   * 
   */
  @PUT
  @Path( "/import" )
  @Consumes( MediaType.MULTIPART_FORM_DATA )
  @Produces( "text/plain" )
  @StatusCodes({
    @ResponseCode( code = 200, condition = "Metadata datasource import succeeded." ),
    @ResponseCode( code = 500, condition = "Metadata datasource import failed.  Error code or message included in response entity" ),
  })   
  public Response importMetadataDatasource( @FormDataParam( "domainId" ) String domainId,
      @FormDataParam( "metadataFile" ) InputStream metadataFile,
      @FormDataParam( "metadataFile" ) FormDataContentDisposition metadataFileInfo,
      @FormDataParam( OVERWRITE_IN_REPOS ) String overwrite,
      @FormDataParam( "localeFiles" ) List<FormDataBodyPart> localeFiles,
      @FormDataParam( "localeFiles" ) List<FormDataContentDisposition> localeFilesInfo ) {
    try {
      service.importMetadataDatasource( domainId, metadataFile, metadataFileInfo, overwrite, localeFiles,
          localeFilesInfo );
      return Response.ok().status( new Integer( SUCCESS ) ).type( MediaType.TEXT_PLAIN ).build();
    } catch ( PentahoAccessControlException e ) {
      return Response.serverError().entity( e.toString() ).build();
    } catch ( PlatformImportException e ) {
      if ( e.getErrorStatus() == PlatformImportException.PUBLISH_PROHIBITED_SYMBOLS_ERROR ) {
        FileResource fr = new FileResource();
        return Response.status( PlatformImportException.PUBLISH_PROHIBITED_SYMBOLS_ERROR ).entity(
            Messages.getString( "MetadataDatasourceService.ERROR_003_PROHIBITED_SYMBOLS_ERROR", domainId, (String) fr
                .doGetReservedCharactersDisplay().getEntity() ) ).build();
      } else {
        String msg = e.getMessage();
        logger.error( "Error import metadata: " + msg + " status = " + e.getErrorStatus() );
        Throwable throwable = e.getCause();
        if ( throwable != null ) {
          msg = throwable.getMessage();
          logger.error( "Root cause: " + msg );
        }
        int statusCode = e.getErrorStatus();
        Response response = Response.ok().status( statusCode ).type( MediaType.TEXT_PLAIN ).build();
        return response;
      }
    } catch ( Exception e ) {
      logger.error( e );
      return Response.serverError().entity(
          Messages.getString( "MetadataDatasourceService.ERROR_001_METADATA_DATASOURCE_ERROR" ) ).build();
    }
  }
}
