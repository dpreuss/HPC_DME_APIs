package gov.nih.nci.hpc.integration.globus.impl;

import gov.nih.nci.hpc.domain.datamanagement.HpcPathAttributes;
import gov.nih.nci.hpc.domain.datatransfer.HpcDataTransferReport;
import gov.nih.nci.hpc.domain.datatransfer.HpcDataTransferStatus;
import gov.nih.nci.hpc.domain.datatransfer.HpcFileLocation;
import gov.nih.nci.hpc.domain.error.HpcErrorType;
import gov.nih.nci.hpc.domain.user.HpcIntegratedSystemAccount;
import gov.nih.nci.hpc.exception.HpcException;
import gov.nih.nci.hpc.integration.HpcDataTransferProxy;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.DatatypeConverter;

import org.globusonline.transfer.APIError;
import org.globusonline.transfer.BaseTransferAPIClient;
import org.globusonline.transfer.JSONTransferAPIClient;
import org.globusonline.transfer.JSONTransferAPIClient.Result;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class HpcDataTransferProxyImpl implements HpcDataTransferProxy 
{
    //---------------------------------------------------------------------//
    // Constants
    //---------------------------------------------------------------------//    
    
    // Globus transfer status strings.
	private final static String FAILED_STATUS = "FAILED"; 
	private final static String ARCHIVED_STATUS = "SUCCEEDED";
	
	// Globus error codes.
	private final static String NOT_DIRECTORY_GLOBUS_CODE = 
			                    "ExternalError.DirListingFailed.NotDirectory";
	
    //---------------------------------------------------------------------//
    // Instance members
    //---------------------------------------------------------------------//
	
	// The Globus connection instance.
	@Autowired
    private HpcGlobusConnection globusConnection = null;
    
	// The Logger instance.
	private final Logger logger = 
			             LoggerFactory.getLogger(this.getClass().getName());

    //---------------------------------------------------------------------//
    // Constructors
    //---------------------------------------------------------------------//
	
    /**
     * Constructor for Spring Dependency Injection.
     * 
     */
	private HpcDataTransferProxyImpl()
    {
    }
    
    //---------------------------------------------------------------------//
    // Methods
    //---------------------------------------------------------------------//
    
    //---------------------------------------------------------------------//
    // HpcDataTransferProxy Interface Implementation
    //---------------------------------------------------------------------//  
    
    @Override
    public Object authenticate(HpcIntegratedSystemAccount dataTransferAccount) 
		                      throws HpcException
    {
    	return globusConnection.authenticate(dataTransferAccount);
    }
    
    @Override
    public String transferData(Object authenticatedToken,
    		                   HpcFileLocation source, HpcFileLocation destination)
    		                  throws HpcException
    {
    	JSONTransferAPIClient client = 
    			       globusConnection.getTransferClient(authenticatedToken);
    	
        if (!autoActivate(source.getEndpoint(), client) || 
        	!autoActivate(destination.getEndpoint(), client)) {
            logger.error("Unable to auto activate go tutorial endpoints, exiting");
            // throw ENDPOINT NOT ACTIVATED HPCException
            //return false;
        }
        
        try {
	        JSONTransferAPIClient.Result r;
	        r = client.getResult("/transfer/submission_id");
	        String submissionId = r.document.getString("value");
	        JSONObject transfer = new JSONObject();
	        transfer.put("DATA_TYPE", "transfer");
	        transfer.put("submission_id", submissionId);
	        transfer.put("verify_checksum", true);
	        transfer.put("delete_destination_extra", false);
	        transfer.put("preserve_timestamp", false);
	        transfer.put("encrypt_data", false);

	        JSONObject item = setJSONItem(source, destination, client);
	        transfer.append("DATA", item);
	        
	        r = client.postResult("/transfer", transfer, null);
	        String taskId = r.document.getString("task_id");
	        logger.debug("Transfer task id :"+ taskId );
	      
	
	        return taskId;
	        
        } catch(Exception e) {
	        	throw new HpcException(
       		                 "Failed to transfer: " + source + ", " + destination, 
       		                 HpcErrorType.DATA_TRANSFER_ERROR, e);
        }
    }
    
    @Override
    public HpcDataTransferStatus getDataTransferStatus(Object authenticatedToken,
                                                       String dataTransferRequestId) 
                                                      throws HpcException
    {
		 HpcDataTransferReport report = getDataTransferReport(authenticatedToken, 
				                                              dataTransferRequestId);
		 if(report.getStatus().equals(ARCHIVED_STATUS)) {
			return HpcDataTransferStatus.ARCHIVED;
		 }

		 if(report.getStatus().equals(FAILED_STATUS)) {
 			return HpcDataTransferStatus.FAILED;
 		 }
		 
		 return HpcDataTransferStatus.IN_PROGRESS;
    }
    
    @Override
    public long getDataTransferSize(Object authenticatedToken,
                                    String dataTransferRequestId) 
                                   throws HpcException
    {
    	return getDataTransferReport(authenticatedToken, dataTransferRequestId).getBytesTransferred();
    }
    
    @Override
    public HpcPathAttributes getPathAttributes(Object authenticatedToken, 
                                               HpcFileLocation fileLocation,
                                               boolean getSize) 
                                              throws HpcException
    {
    	return getPathAttributes(fileLocation, 
    			                 globusConnection.getTransferClient(authenticatedToken),
    			                 getSize);
    }
    
    //---------------------------------------------------------------------//
    // Helper Methods
    //---------------------------------------------------------------------//  
    
    private Calendar convertToLexicalTime(String timeStr) 
    {
    	if (timeStr == null || "null".equalsIgnoreCase(timeStr))    	
    		return null;     	
    	else
    		return DatatypeConverter.parseDateTime(timeStr.trim().replace(' ', 'T'));
	}
    
	private JSONObject setJSONItem(HpcFileLocation source, HpcFileLocation destination, 
			                       JSONTransferAPIClient client)  
			                      throws HpcException 
	{
    	JSONObject item = new JSONObject();
    	try {
	        item.put("DATA_TYPE", "transfer_item");
	        item.put("source_endpoint", source.getEndpoint());
	        item.put("source_path", source.getPath());
	        item.put("destination_endpoint", destination.getEndpoint());
	        item.put("destination_path", destination.getPath());
	        item.put("recursive", getPathAttributes(source, client, false).getIsDirectory());
	        return item;
	        
		} catch(Exception e) {
	        throw new HpcException(
	        		     "Failed to create JSON: " + source + ", " + destination, 
	        		     HpcErrorType.DATA_TRANSFER_ERROR, e);
		}    
    }

	private boolean autoActivate(String endpointName, JSONTransferAPIClient client)
                                throws HpcException 
	{
		try {
             String resource = BaseTransferAPIClient.endpointPath(endpointName)
                               + "/autoactivate?if_expires_in=100";
             JSONTransferAPIClient.Result r = client.postResult(resource, null,
                                                                null);
             String code = r.document.getString("code");
             if(code.startsWith("AutoActivationFailed")) {
            	return false;
             }
             return true;
             
		} catch(Exception e) {
		        throw new HpcException(
		        		     "Failed to activate endpoint: " + endpointName, 
		        		     HpcErrorType.DATA_TRANSFER_ERROR, e);
		}
    }
	
    /**
     * Get a data transfer report.
     *
     * @param authenticatedToken An authenticated token.
     * @param dataTransferRequestId The data transfer request ID.
     * 
     * @return HpcDataTransferReport the data transfer report for the request.
     * 
     * @throws HpcException
     */
    private HpcDataTransferReport getDataTransferReport(Object authenticatedToken,
                              String dataTransferRequestId) 
           throws HpcException
    {
		JSONTransferAPIClient client = globusConnection.getTransferClient(authenticatedToken);
		HpcDataTransferReport hpcDataTransferReport = new HpcDataTransferReport();
		
		JSONTransferAPIClient.Result r;
		String resource = "/task/" +  dataTransferRequestId;
		// Map<String, String> params = new HashMap<String, String>();
		//    params.put("fields", "status");
		try {
		r = client.getResult(resource);
		r.document.getString("status");
		hpcDataTransferReport.setTaskID(dataTransferRequestId);
		hpcDataTransferReport.setTaskType(r.document.getString("type"));
		hpcDataTransferReport.setStatus(r.document.getString("status"));
		if (r.document.has("request_time") && !r.document.isNull("request_time"))
		hpcDataTransferReport.setRequestTime(convertToLexicalTime(r.document.getString("request_time")));
		else
		hpcDataTransferReport.setRequestTime(null);  
		if (r.document.has("deadline") && !r.document.isNull("deadline"))
		hpcDataTransferReport.setDeadline(convertToLexicalTime(r.document.getString("deadline")));
		else
		hpcDataTransferReport.setDeadline(null);     
		if (r.document.has("completion_time") && !r.document.isNull("completion_time"))
		hpcDataTransferReport.setCompletionTime(convertToLexicalTime(r.document.getString("completion_time")));
		else
		hpcDataTransferReport.setCompletionTime(null);
		hpcDataTransferReport.setTotalTasks(r.document.getInt("subtasks_total"));
		hpcDataTransferReport.setTasksSuccessful(r.document.getInt("subtasks_succeeded"));
		hpcDataTransferReport.setTasksExpired(r.document.getInt("subtasks_expired"));
		hpcDataTransferReport.setTasksCanceled(r.document.getInt("subtasks_canceled"));
		hpcDataTransferReport.setTasksPending(r.document.getInt("subtasks_pending"));
		hpcDataTransferReport.setTasksRetrying(r.document.getInt("subtasks_retrying"));
		hpcDataTransferReport.setCommand(r.document.getString("command"));
		hpcDataTransferReport.setSourceEndpoint(r.document.getString("source_endpoint"));
		hpcDataTransferReport.setDestinationEndpoint(r.document.getString("destination_endpoint"));
		hpcDataTransferReport.setDataEncryption(r.document.getBoolean("encrypt_data"));
		hpcDataTransferReport.setChecksumVerification(r.document.getBoolean("verify_checksum"));
		hpcDataTransferReport.setDelete(r.document.getBoolean("delete_destination_extra"));
		hpcDataTransferReport.setFiles(r.document.getInt("files"));
		hpcDataTransferReport.setFilesSkipped(r.document.getInt("files_skipped"));
		hpcDataTransferReport.setDirectories(r.document.getInt("directories"));
		hpcDataTransferReport.setBytesTransferred(r.document.getLong("bytes_transferred"));
		hpcDataTransferReport.setBytesChecksummed(r.document.getLong("bytes_checksummed"));
		hpcDataTransferReport.setEffectiveMbitsPerSec(r.document.getDouble("effective_bytes_per_second"));
		hpcDataTransferReport.setFaults(r.document.getInt("faults"));
		
		return hpcDataTransferReport;
		
		} catch(Exception e) {
		throw new HpcException(
		"Failed to get task report for task: " + dataTransferRequestId, 
		HpcErrorType.DATA_TRANSFER_ERROR, e);
		} 
    }
    
    /**
     * Get attributes of a file/directory.
     *
     * @param fileLocation The endpoint/path to check.
     * @param client Globus client API instance.
     * @param getSize If set to true, the file/directory size will be returned. 
     * @return HpcDataTransferPathAttributes The path attributes.
     * 
     * @throws HpcException
     */
    private HpcPathAttributes getPathAttributes(HpcFileLocation fileLocation,
                                                JSONTransferAPIClient client,
                                                boolean getSize) 
                                               throws HpcException
    {
    	HpcPathAttributes pathAttributes = new HpcPathAttributes();
    	pathAttributes.setExists(false);
    	pathAttributes.setIsDirectory(false);
    	pathAttributes.setIsFile(false);
    	pathAttributes.setSize(0);
    	
    	// Invoke the Globus directory listing service.
        try {
        	 Result dirContent = listDirectoryContent(fileLocation, client);
        	 pathAttributes.setExists(true);
        	 pathAttributes.setIsDirectory(true);
        	 pathAttributes.setSize(getSize ? getDirectorySize(dirContent, client) : -1);
        	
        } catch(APIError error) {
        	    if(error.code.equals(NOT_DIRECTORY_GLOBUS_CODE)) {
        	       // Path exists as a single file
        	       pathAttributes.setExists(true);
        	       pathAttributes.setIsFile(true);
        	       pathAttributes.setSize(getSize ? getFileSize(fileLocation, client) : -1);
        	    } // else path was not found. 
        	    
        } catch(Exception e) {
	            throw new HpcException("Failed to get path attributes: " + fileLocation, 
       		                           HpcErrorType.DATA_TRANSFER_ERROR, e);
        }
        
    	return pathAttributes;
    }
    
    /**
     * Get file size.
     *
     * @param fileLocation The file endpoint/path.
     * @param client Globus client API instance.
     * @return The file size in bytes.
     */
    private long getFileSize(HpcFileLocation fileLocation, JSONTransferAPIClient client)
    {
    	// Get the directory location of the file.
    	HpcFileLocation dirLocation = new HpcFileLocation();
    	dirLocation.setEndpoint(fileLocation.getEndpoint());
    	int fileNameIndex = fileLocation.getPath().lastIndexOf('/');
    	dirLocation.setPath(fileLocation.getPath().substring(0, fileNameIndex));
    	
    	// Extract the file name from the path.
    	String fileName = fileLocation.getPath().substring(fileNameIndex + 1);
    	
    	// List the directory content.
    	try {
             Result dirContent = listDirectoryContent(dirLocation, client);  
             JSONArray jsonFiles = dirContent.document.getJSONArray("DATA");
             if(jsonFiles != null) {
                // Iterate through the directory files, and locate the file we look for.
            	int filesNum = jsonFiles.length();
                for(int i = 0; i < filesNum; i++) {
                	JSONObject jsonFile = jsonFiles.getJSONObject(i);
                	String jsonFileName = jsonFile.getString("name");
                	if(jsonFileName != null && jsonFileName.equals(fileName)) {
                	   // The file was found. Return its size
                	   return jsonFile.getLong("size");
                	}
                }
             }
             
    	} catch(Exception e) {
    		    // Unexpected error. Eat this.
    		    logger.error("Failed to calculate file size", e); 
    	}
    	
    	// File not found, or exception was caught.
    	return 0;
    }	
    
    /**
     * Get directory size. Sums up the size of all the files in this directory recursively.
     *
     * @param dirContent The directory content.
     * @param client Globus client API instance.
     * @return The directory size in bytes.
     */
    private long getDirectorySize(Result dirContent, JSONTransferAPIClient client)
    {
    	try {
             JSONArray jsonFiles = dirContent.document.getJSONArray("DATA");
             if(jsonFiles != null) {
                // Iterate through the directory files, and sum up the files size.
            	int filesNum = jsonFiles.length();
            	long size = 0;
                for(int i = 0; i < filesNum; i++) {
                	JSONObject jsonFile = jsonFiles.getJSONObject(i);
                	String jsonFileType = jsonFile.getString("type");
                	if(jsonFileType != null) {
                	   if(jsonFileType.equals("file")) {
                		  // This is a file. Add its size to the total;
                	      size += jsonFile.getLong("size");
                	      continue;
                	   } else if(jsonFileType.equals("dir")) {
                		         // It's a sub directory. Make a recursive call, to add its size.
                		         HpcFileLocation subDirLocation = new HpcFileLocation();
                		         subDirLocation.setEndpoint(dirContent.document.getString("endpoint"));
                		         subDirLocation.setPath(dirContent.document.getString("path") +
                		        		                '/' + jsonFile.getString("name"));
                		         
                		         size += getDirectorySize(listDirectoryContent(subDirLocation, 
                		        		                                       client), 
                		        		                  client);
                	   }
                	}
                }
                return size;
             }
             
    	} catch(Exception e) {
    		    // Unexpected error. Eat this.
    		    logger.error("Failed to calculate directory size", e);
    	}
    	
    	// Directory not found, or exception was caught. 
    	return 0;
    }	
    
    /**
     * Call the Globus list directory content service.
     * See: https://docs.globus.org/api/transfer/file_operations/#list_directory_contents
     *
     * @param dirLocation The directory endpoint/path.
     * @param client Globus client API instance.
     * @return The file size in bytes.
     */
    private Result listDirectoryContent(HpcFileLocation dirLocation, 
    		                            JSONTransferAPIClient client)
    		                           throws APIError, HpcException
    {
		Map<String, String> params = new HashMap<String, String>();
		params.put("path", dirLocation.getPath());
	    try {
		     String resource = BaseTransferAPIClient.endpointPath(dirLocation.getEndpoint()) + "/ls";
		     return client.getResult(resource, params);
		
	    } catch(APIError apiError) {
	    	    throw apiError;
	    } catch(Exception e) {
		        throw new HpcException("Failed to invoke list directory content service: " + 
	                                   dirLocation, 
		                               HpcErrorType.DATA_TRANSFER_ERROR, e);
		}
    }
}
