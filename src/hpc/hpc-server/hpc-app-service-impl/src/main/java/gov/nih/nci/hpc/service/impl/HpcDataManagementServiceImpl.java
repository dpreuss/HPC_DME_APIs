/**
 * HpcDataManagementServiceImpl.java
 *
 * Copyright SVG, Inc.
 * Copyright Leidos Biomedical Research, Inc
 * 
 * Distributed under the OSI-approved BSD 3-Clause License.
 * See http://ncip.github.com/HPC/LICENSE.txt for details.
 */

package gov.nih.nci.hpc.service.impl;

import static gov.nih.nci.hpc.service.impl.HpcDomainValidator.isValidMetadataEntries;
import static gov.nih.nci.hpc.service.impl.HpcDomainValidator.isValidFileLocation;
import gov.nih.nci.hpc.domain.dataset.HpcDataManagementEntity;
import gov.nih.nci.hpc.domain.dataset.HpcFileLocation;
import gov.nih.nci.hpc.domain.error.HpcErrorType;
import gov.nih.nci.hpc.domain.error.HpcRequestRejectReason;
import gov.nih.nci.hpc.domain.metadata.HpcMetadataEntry;
import gov.nih.nci.hpc.domain.user.HpcIntegratedSystemAccount;
import gov.nih.nci.hpc.exception.HpcException;
import gov.nih.nci.hpc.integration.HpcDataManagementProxy;
import gov.nih.nci.hpc.service.HpcDataManagementService;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

/**
 * <p>
 * HPC Data Management Application Service Implementation.
 * </p>
 *
 * @author <a href="mailto:eran.rosenberg@nih.gov">Eran Rosenberg</a>
 * @version $Id$
 */

public class HpcDataManagementServiceImpl implements HpcDataManagementService
{    
    //---------------------------------------------------------------------//
    // Constants
    //---------------------------------------------------------------------//    
    
    // File Location metadata attributes.
	public final static String FILE_LOCATION_ENDPOINT_ATTRIBUTE = 
			                   "Data Location Globus Endpoint"; 
	public final static String FILE_LOCATION_PATH_ATTRIBUTE = 
			                   "Data Location Globus Path"; 
	public final static String FILE_SOURCE_ENDPOINT_ATTRIBUTE = 
			                   "Data Source Globus Endpoint"; 
	public final static String FILE_SOURCE_PATH_ATTRIBUTE = 
			                   "Data Source Globus Path"; 
	
    //---------------------------------------------------------------------//
    // Instance members
    //---------------------------------------------------------------------//

    // The Data Management Proxy instance.
	@Autowired
    private HpcDataManagementProxy dataManagementProxy = null;
	
	// Metadata Validator.
	@Autowired
	private HpcMetadataValidator metadataValidator = null;
	
    //---------------------------------------------------------------------//
    // Constructors
    //---------------------------------------------------------------------//
	
    /**
     * Constructor for Spring Dependency Injection.
     * 
     */
    private HpcDataManagementServiceImpl()
    {
    }   
    
    //---------------------------------------------------------------------//
    // Methods
    //---------------------------------------------------------------------//
    
    //---------------------------------------------------------------------//
    // HpcDataManagementService Interface Implementation
    //---------------------------------------------------------------------//  

    @Override
    public void createDirectory(HpcIntegratedSystemAccount dataManagementAccount,
    		                    String path) 
    		                   throws HpcException
    {
    	dataManagementProxy.createCollectionDirectory(dataManagementAccount, path);
    }
    
    @Override
    public void createFile(HpcIntegratedSystemAccount dataManagementAccount,
    		               String path) 
    		              throws HpcException
    {
    	// Check the path is available.
    	if(dataManagementProxy.exists(dataManagementAccount, path)) {
    		throw new HpcException("Path already exists: " + path, 
    				               HpcRequestRejectReason.DATA_OBJECT_PATH_ALREADY_EXISTS);
    	}
    	
    	// Create the parent directory if it doesn't already exist.
    	dataManagementProxy.createParentPathDirectory(dataManagementAccount, path);
    	
    	// Create the data object file.
    	dataManagementProxy.createDataObjectFile(dataManagementAccount, path);
    }

    @Override
    public void addMetadataToCollection(HpcIntegratedSystemAccount dataManagementAccount,
    		                            String path, 
    		                            List<HpcMetadataEntry> metadataEntries) 
    		                           throws HpcException
    {
       	// Input validation.
       	if(path == null || !isValidMetadataEntries(metadataEntries)) {
       	   throw new HpcException("Null path or Invalid metadata entry", 
       			                  HpcErrorType.INVALID_REQUEST_INPUT);
       	}	
       	
       	// Validate Metadata.
       	metadataValidator.validateCollectionMetadata(metadataEntries);
       	
       	// Add Metadata to the DM system.
       	dataManagementProxy.addMetadataToCollection(dataManagementAccount,
       			                                    path, metadataEntries);
    }
    
    @Override
    public void addMetadataToDataObject(HpcIntegratedSystemAccount dataManagementAccount,
    		                            String path, 
    		                            List<HpcMetadataEntry> metadataEntries) 
    		                           throws HpcException
    {
       	// Input validation.
       	if(path == null || !isValidMetadataEntries(metadataEntries)) {
       	   throw new HpcException("Null path or Invalid metadata entry", 
       			                  HpcErrorType.INVALID_REQUEST_INPUT);
       	}	
       	
       	// Validate Metadata.
       	metadataValidator.validateDataObjectMetadata(metadataEntries);
       	
       	// Add Metadata to the DM system.
       	dataManagementProxy.addMetadataToDataObject(dataManagementAccount, 
       			                                    path, metadataEntries);
    }
    
    @Override
    public void addFileLocationsMetadataToDataObject(
    		           HpcIntegratedSystemAccount dataManagementAccount,
                       String path, 
                       HpcFileLocation fileLocation,
    		           HpcFileLocation fileSource) 
                       throws HpcException
    {
       	// Input validation.
       	if(path == null || !isValidFileLocation(fileLocation)) {
       	   throw new HpcException("Null path or Invalid file location", 
       			                  HpcErrorType.INVALID_REQUEST_INPUT);
       	}	
       	
       	List<HpcMetadataEntry> metadataEntries = new ArrayList<HpcMetadataEntry>();
       	
       	// Create the file location endpoint metadata.
       	HpcMetadataEntry locationEndpointMetadata = new HpcMetadataEntry();
       	locationEndpointMetadata.setAttribute(FILE_LOCATION_ENDPOINT_ATTRIBUTE);
       	locationEndpointMetadata.setValue(fileLocation.getEndpoint());
       	locationEndpointMetadata.setUnit("");
       	metadataEntries.add(locationEndpointMetadata);
       	
       	// Create the file location path metadata.
       	HpcMetadataEntry locationPathMetadata = new HpcMetadataEntry();
       	locationPathMetadata.setAttribute(FILE_LOCATION_PATH_ATTRIBUTE);
       	locationPathMetadata.setValue(fileLocation.getPath());
       	locationPathMetadata.setUnit("");
       	metadataEntries.add(locationPathMetadata);
       	
       	// Create the file source endpoint metadata.
       	HpcMetadataEntry sourceEndpointMetadata = new HpcMetadataEntry();
       	sourceEndpointMetadata.setAttribute(FILE_SOURCE_ENDPOINT_ATTRIBUTE);
       	sourceEndpointMetadata.setValue(fileSource.getEndpoint());
       	sourceEndpointMetadata.setUnit("");
       	metadataEntries.add(sourceEndpointMetadata);
       	
       	// Create the file source path metadata.
       	HpcMetadataEntry sourcePathMetadata = new HpcMetadataEntry();
       	sourcePathMetadata.setAttribute(FILE_SOURCE_PATH_ATTRIBUTE);
       	sourcePathMetadata.setValue(fileSource.getPath());
       	sourcePathMetadata.setUnit("");
       	metadataEntries.add(sourcePathMetadata);
       	
       	// Add Metadata to the DM system.
       	dataManagementProxy.addMetadataToDataObject(dataManagementAccount, 
       			                                    path, metadataEntries);    	
    }
    
    @Override
    public List<HpcDataManagementEntity> getCollections(
    		    HpcIntegratedSystemAccount dataManagementAccount,
		        List<HpcMetadataEntry> metadataEntryQueries) throws HpcException
    {
    	return dataManagementProxy.getCollections(dataManagementAccount,
    			                                  metadataEntryQueries);
    }
}