/**
 * HpcSearchProjectController.java
 *
 * Copyright SVG, Inc.
 * Copyright Leidos Biomedical Research, Inc
 * 
 * Distributed under the OSI-approved BSD 3-Clause License.
 * See https://ncisvn.nci.nih.gov/svn/HPC_Data_Management/branches/hpc-prototype-dev/LICENSE.txt for details.
 */
package gov.nih.nci.hpc.web.controller;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import javax.ws.rs.core.Response;

import org.apache.cxf.jaxrs.client.WebClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.MappingJsonFactory;

import gov.nih.nci.hpc.domain.datamanagement.HpcDataHierarchy;
import gov.nih.nci.hpc.domain.metadata.HpcCompoundMetadataQuery;
import gov.nih.nci.hpc.domain.metadata.HpcCompoundMetadataQueryOperator;
import gov.nih.nci.hpc.domain.metadata.HpcCompoundMetadataQueryType;
import gov.nih.nci.hpc.domain.metadata.HpcMetadataEntries;
import gov.nih.nci.hpc.domain.metadata.HpcMetadataEntry;
import gov.nih.nci.hpc.domain.metadata.HpcMetadataLevelAttributes;
import gov.nih.nci.hpc.domain.metadata.HpcMetadataQuery;
import gov.nih.nci.hpc.domain.metadata.HpcMetadataQueryLevelFilter;
import gov.nih.nci.hpc.domain.metadata.HpcMetadataQueryOperator;
import gov.nih.nci.hpc.dto.datamanagement.HpcCollectionDTO;
import gov.nih.nci.hpc.dto.datamanagement.HpcCollectionListDTO;
import gov.nih.nci.hpc.dto.datamanagement.HpcDataManagementModelDTO;
import gov.nih.nci.hpc.dto.datamanagement.HpcDataObjectDTO;
import gov.nih.nci.hpc.dto.datamanagement.HpcDataObjectListDTO;
import gov.nih.nci.hpc.dto.datamanagement.HpcMetadataAttributesListDTO;
import gov.nih.nci.hpc.dto.datasearch.HpcCompoundMetadataQueryDTO;
import gov.nih.nci.hpc.dto.security.HpcUserDTO;
import gov.nih.nci.hpc.web.model.HpcCollectionSearchResultDetailed;
import gov.nih.nci.hpc.web.model.HpcCompoundQuery;
import gov.nih.nci.hpc.web.model.HpcDatafileSearchResultDetailed;
import gov.nih.nci.hpc.web.model.HpcLogin;
import gov.nih.nci.hpc.web.model.HpcMetadataHierarchy;
import gov.nih.nci.hpc.web.model.HpcSaveSearch;
import gov.nih.nci.hpc.web.model.HpcSearch;
import gov.nih.nci.hpc.web.model.HpcSearchResult;
import gov.nih.nci.hpc.web.util.HpcClientUtil;
import gov.nih.nci.hpc.web.util.HpcCompoundSearchBuilder;

/**
 * <p>
 * HPC DM Project Search controller
 * </p>
 *
 * @author <a href="mailto:Prasad.Konka@nih.gov">Prasad Konka</a>
 * @version $Id: HpcDataRegistrationController.java
 */

@Controller
@EnableAutoConfiguration
@RequestMapping("/criteria")
public class HpcSearchCriteriaController extends AbstractHpcController {
	@Value("${gov.nih.nci.hpc.server.collection}")
	private String collectionServiceURL;
	@Value("${gov.nih.nci.hpc.server.search.collection.compound}")
	private String compoundCollectionSearchServiceURL;
	@Value("${gov.nih.nci.hpc.server.search.dataobject.compound}")
	private String compoundDataObjectSearchServiceURL;
	@Value("${gov.nih.nci.hpc.server.dataObject}")
	private String datafileServiceURL;
	@Value("${gov.nih.nci.hpc.server.model}")
	private String modelServiceURL;
	@Value("${gov.nih.nci.hpc.server.metadataattributes}")
	private String hpcMetadataAttrsURL;

	/*
	 * Action for Datset registration page
	 */
	@RequestMapping(method = RequestMethod.GET)
	public String home(@RequestBody(required = false) String q, Model model, BindingResult bindingResult,
			HttpSession session, HttpServletRequest request) {
		HpcSearch hpcSearch = new HpcSearch();
		HpcSaveSearch hpcSaveSearch = new HpcSaveSearch();
		hpcSearch.setSearchType("collection");
		model.addAttribute("hpcSearch", hpcSearch);
		model.addAttribute("hpcSaveSearch", hpcSaveSearch);
		session.removeAttribute("compoundQuery");
		String authToken = (String) session.getAttribute("hpcUserToken");
		HpcUserDTO user = (HpcUserDTO) session.getAttribute("hpcUser");
		if (user == null) {
			ObjectError error = new ObjectError("hpcLogin", "Invalid user session!");
			bindingResult.addError(error);
			HpcLogin hpcLogin = new HpcLogin();
			model.addAttribute("hpcLogin", hpcLogin);
			return "index";
		}
		populateHierarchy(session, model, authToken, user);
		populateMetadata(model, authToken, user, "collection", session);
		populateOperators(model);
		populateLevelOperators(model);
		return "criteria";
	}

	/*
	 * Action for Project registration
	 */
	@RequestMapping(method = RequestMethod.POST)
	public String search(@Valid @ModelAttribute("hpcSearch") HpcSearch search, Model model, BindingResult bindingResult,
			HttpSession session, HttpServletRequest request, RedirectAttributes redirectAttrs) {
		if (search.getActionType() != null && search.getActionType().equals("refresh")) {
			HpcUserDTO user = (HpcUserDTO) session.getAttribute("hpcUser");
			String authToken = (String) session.getAttribute("hpcUserToken");
			populateHierarchy(session, model, authToken, user);
			populateMetadata(model, authToken, user, search.getSearchType(), session);
			populateOperators(model);
			populateLevelOperators(model);
			return "criteria";
		}

		boolean success = false;
		try {

			// String criteria = getCriteria();
			Map<String, String> hierarchy = (Map<String, String>) session.getAttribute("hierarchies");

			HpcCompoundMetadataQueryDTO compoundQuery = constructCriteria(hierarchy, search);
			if (search.isDetailed())
				compoundQuery.setDetailedResponse(true);

			// criteria = criteria + "&detailedResponse=true";
			String authToken = (String) session.getAttribute("hpcUserToken");
			String serviceURL = compoundDataObjectSearchServiceURL;
			if (search.getSearchType() != null && search.getSearchType().equals("collection"))
				serviceURL = compoundCollectionSearchServiceURL;

			WebClient client = HpcClientUtil.getWebClient(serviceURL, sslCertPath, sslCertPassword);
			client.header("Authorization", "Bearer " + authToken);

			Response restResponse = client.invoke("POST", compoundQuery);
			if (restResponse.getStatus() == 200) {
				processResponseResults(search, restResponse, model, redirectAttrs);
				success = true;
				session.setAttribute("compoundQuery", compoundQuery);
				// model.addAttribute("searchresultscolumns",getColumnDefs(search.isDetailed()));
			} else {
				String message = "No matching results!";
				ObjectError error = new ObjectError("hpcSearch", message);
				bindingResult.addError(error);
				model.addAttribute("error", message);
				return "criteria";
			}
		} catch (com.fasterxml.jackson.databind.JsonMappingException e) {
			e.printStackTrace();
			ObjectError error = new ObjectError("hpcLogin", "Failed to project: " + e.getMessage());
			bindingResult.addError(error);
			model.addAttribute("error", "Failed to search due to: " + e.getMessage());
			return "criteria";
		} catch (HttpStatusCodeException e) {
			e.printStackTrace();
			ObjectError error = new ObjectError("hpcLogin", "Failed to search: " + e.getMessage());
			bindingResult.addError(error);
			model.addAttribute("error", "Failed to search due to: " + e.getMessage());
			return "criteria";
		} catch (RestClientException e) {
			e.printStackTrace();
			ObjectError error = new ObjectError("hpcLogin", "Failed to search: " + e.getMessage());
			bindingResult.addError(error);
			model.addAttribute("error", "Failed to search due to: " + e.getMessage());
			return "criteria";
		} catch (Exception e) {
			e.printStackTrace();
			ObjectError error = new ObjectError("hpcLogin", "Failed to search: " + e.getMessage());
			bindingResult.addError(error);
			model.addAttribute("error", "Failed to search due to: " + e.getMessage());
			return "criteria";
		} finally {
			if (!success) {
				HpcUserDTO user = (HpcUserDTO) session.getAttribute("hpcUser");
				String authToken = (String) session.getAttribute("hpcUserToken");
				populateHierarchy(session, model, authToken, user);
				populateMetadata(model, authToken, user, search.getSearchType(), session);
				populateOperators(model);
				populateLevelOperators(model);
			}
		}

		if (search.getSearchType().equals("collection"))
			return search.isDetailed() ? "collectionsearchresultdetail" : "collectionsearchresult";
		else
			return search.isDetailed() ? "dataobjectsearchresultdetail" : "dataobjectsearchresult";
	}

	private void processResponseResults(HpcSearch search, Response restResponse, Model model,
			RedirectAttributes redirectAttrs) throws JsonParseException, IOException {
		if (search.getSearchType().equals("collection"))
			processCollectionResults(search, restResponse, model, redirectAttrs);
		else
			processDataObjectResults(search, restResponse, model, redirectAttrs);
	}

	private void processCollectionResults(HpcSearch search, Response restResponse, Model model,
			RedirectAttributes redirectAttrs) throws JsonParseException, IOException {
		MappingJsonFactory factory = new MappingJsonFactory();
		JsonParser parser = factory.createParser((InputStream) restResponse.getEntity());
		HpcCollectionListDTO collections = parser.readValueAs(HpcCollectionListDTO.class);
		if (!search.isDetailed()) {
			List<String> searchResults = collections.getCollectionPaths();
			List<HpcSearchResult> returnResults = new ArrayList<HpcSearchResult>();
			for (String result : searchResults) {
				HpcSearchResult returnResult = new HpcSearchResult();
				returnResult.setPath(result);
				returnResults.add(returnResult);

			}
			model.addAttribute("searchresults", returnResults);
			model.addAttribute("searchType", "collection");
		} else {
			SimpleDateFormat format = new SimpleDateFormat("MM/dd/yyyy hh:mm");
			List<HpcCollectionDTO> searchResults = collections.getCollections();
			List<HpcCollectionSearchResultDetailed> returnResults = new ArrayList<HpcCollectionSearchResultDetailed>();
			for (HpcCollectionDTO result : searchResults) {
				HpcCollectionSearchResultDetailed returnResult = new HpcCollectionSearchResultDetailed();
				returnResult.setPath(result.getCollection().getCollectionName());
				returnResult.setUuid(getAttributeValue("uuid", result.getMetadataEntries()));
				returnResult.setRegisteredBy(getAttributeValue("registered_by", result.getMetadataEntries()));
				returnResult.setCreatedOn(format.format(result.getCollection().getCreatedAt().getTime()));
				returnResult.setCollectionType(getAttributeValue("collection_type", result.getMetadataEntries()));
				returnResults.add(returnResult);

			}
			model.addAttribute("searchresults", returnResults);
			model.addAttribute("detailed", "yes");
			model.addAttribute("searchType", "collection");
		}
	}

	private void processDataObjectResults(HpcSearch search, Response restResponse, Model model,
			RedirectAttributes redirectAttrs) throws JsonParseException, IOException {
		MappingJsonFactory factory = new MappingJsonFactory();
		JsonParser parser = factory.createParser((InputStream) restResponse.getEntity());
		HpcDataObjectListDTO dataObjects = parser.readValueAs(HpcDataObjectListDTO.class);
		if (!search.isDetailed()) {
			List<String> searchResults = dataObjects.getDataObjectPaths();
			List<HpcSearchResult> returnResults = new ArrayList<HpcSearchResult>();
			for (String result : searchResults) {
				HpcSearchResult returnResult = new HpcSearchResult();
				returnResult.setPath(result);
				returnResults.add(returnResult);

			}
			model.addAttribute("searchresults", returnResults);
			model.addAttribute("searchType", "datafile");
		} else {
			SimpleDateFormat format = new SimpleDateFormat("MM/dd/yyyy hh:mm");
			List<HpcDataObjectDTO> searchResults = dataObjects.getDataObjects();
			List<HpcDatafileSearchResultDetailed> returnResults = new ArrayList<HpcDatafileSearchResultDetailed>();
			for (HpcDataObjectDTO result : searchResults) {
				HpcDatafileSearchResultDetailed returnResult = new HpcDatafileSearchResultDetailed();
				returnResult.setPath(result.getDataObject().getAbsolutePath());
				returnResult.setUuid(getAttributeValue("uuid", result.getMetadataEntries()));
				returnResult.setRegisteredBy(getAttributeValue("registered_by", result.getMetadataEntries()));
				returnResult.setCreatedOn(format.format(result.getDataObject().getCreatedAt().getTime()));
				returnResult.setChecksum(getAttributeValue("checksum", result.getMetadataEntries()));
				returnResults.add(returnResult);

			}
			model.addAttribute("searchresults", returnResults);
			model.addAttribute("detailed", "yes");
			model.addAttribute("searchType", "datafile");
		}
	}

	private HpcCompoundMetadataQueryDTO constructCriteria(Map<String, String> hierarchy, HpcSearch search) {
		HpcCompoundMetadataQueryDTO dto = new HpcCompoundMetadataQueryDTO();
		HpcCompoundMetadataQuery query = null;
		if (search.getAdvancedCriteria() != null && !search.getAdvancedCriteria().isEmpty())
			query = buildAdvancedSearch(hierarchy, search);
		else
			query = buildSimpleSearch(hierarchy, search);

		dto.setCompoundQuery(query);
		dto.setDetailedResponse(search.isDetailed());
		if (search.getSearchType().equals("collection"))
			dto.setCompoundQueryType(HpcCompoundMetadataQueryType.COLLECTION);
		else
			dto.setCompoundQueryType(HpcCompoundMetadataQueryType.DATA_OBJECT);
		dto.setDetailedResponse(search.isDetailed());
		return dto;
	}

	private HpcCompoundMetadataQuery buildSimpleSearch(Map<String, String> hierarchy, HpcSearch search) {
		HpcCompoundMetadataQuery query = new HpcCompoundMetadataQuery();
		query.setOperator(HpcCompoundMetadataQueryOperator.AND);
		Map<String, HpcMetadataQuery> queriesMap = getQueries(hierarchy, search);
		List<HpcMetadataQuery> queries = new ArrayList<HpcMetadataQuery>();
		Iterator<String> iter = queriesMap.keySet().iterator();
		while(iter.hasNext())
			queries.add(queriesMap.get(iter.next()));

		query.getQueries().addAll(queries);
		return query;
	}

	private Map<String, HpcMetadataQuery> getQueries(Map<String, String> hierarchy, HpcSearch search)
	{
		Map<String, HpcMetadataQuery> queries = new HashMap<String, HpcMetadataQuery>();
		for (int i = 0; i < search.getAttrName().length; i++) {
			String rowId = search.getRowId()[i];
			String attrName = search.getAttrName()[i];
			String attrValue = search.getAttrValue()[i];
			String operator = search.getOperator()[i];
			String level = search.getLevel()[i];
			if (!attrName.isEmpty() && !attrValue.isEmpty() && !operator.isEmpty()) {
				HpcMetadataQuery criteria = new HpcMetadataQuery();
				criteria.setAttribute(attrName);
				criteria.setValue(attrValue);
				criteria.setOperator(HpcMetadataQueryOperator.fromValue(operator));
				if (level != null && !level.equals("-1")) {
					HpcMetadataQueryLevelFilter levelFilter = new HpcMetadataQueryLevelFilter();
					if (level.equals("Data file") || level.equals("DataObject"))
						levelFilter.setLevel(1);
					else
						levelFilter.setLabel(level);
					levelFilter.setOperator(HpcMetadataQueryOperator.EQUAL);
					criteria.setLevelFilter(levelFilter);
				}
				queries.put(rowId, criteria);
			}
		}	
		return queries;
	}
	
	private HpcCompoundMetadataQuery buildAdvancedSearch(Map<String, String> hierarchy, HpcSearch search) {
		Map<String, HpcMetadataQuery> queries = getQueries(hierarchy, search);
		HpcCompoundQuery compoundQuery = new HpcCompoundQuery();
		compoundQuery.setQueries(queries);
		compoundQuery.setCriteria(search.getAdvancedCriteria());
		return new HpcCompoundSearchBuilder(compoundQuery).build();
	}

	private String getAttributeValue(String attrName, HpcMetadataEntries entries) {
		if (entries == null)
			return null;

		List<HpcMetadataEntry> selfEntries = entries.getSelfMetadataEntries();
		for (HpcMetadataEntry entry : selfEntries) {
			if (entry.getAttribute().equals(attrName))
				return entry.getValue();
		}
		List<HpcMetadataEntry> parentEntries = entries.getParentMetadataEntries();
		for (HpcMetadataEntry entry : parentEntries) {
			if (entry.getAttribute().equals(attrName))
				return entry.getValue();
		}
		return null;
	}

	private void populateOperators(Model model) {
		Map<String, String> entries = new HashMap<String, String>();
		entries.put("EQUAL", "=");
		entries.put("NOT_EQUAL", "!=");
		entries.put("NUM_LESS_THAN", "<");
		entries.put("NUM_LESS_OR_EQUAL", "<=");
		entries.put("NUM_GREATER_OR_EQUAL", ">=");
		entries.put("LIKE", "LIKE");
		model.addAttribute("operators", entries);
	}

	private void populateLevelOperators(Model model) {
		Map<String, String> entries = new HashMap<String, String>();
		entries.put("EQUAL", "=");
		entries.put("NOT_EQUAL", "!=");
		entries.put("NUM_LESS_THAN", "<");
		entries.put("NUM_LESS_OR_EQUAL", "<=");
		entries.put("NUM_GREATER_OR_EQUAL", ">=");
		model.addAttribute("leveloperators", entries);
	}

	private void populateMetadata(Model model, String authToken, HpcUserDTO user, String type, HttpSession session) {
		model.addAttribute("doc", user.getNciAccount().getDoc());
		if (session.getAttribute("hierarchy") != null) {
			model.addAttribute("hierarchy", session.getAttribute("hierarchy"));
			return;
		}

		HpcMetadataHierarchy dataHierarchy = new HpcMetadataHierarchy();
		Map<String, String> hierarchy = new HashMap<String, String>();

		List<String> attrs = new ArrayList<String>();

		HpcMetadataAttributesListDTO dto = HpcClientUtil.getMetadataAttrNames(authToken, hpcMetadataAttrsURL,
				sslCertPath, sslCertPassword);
		if (dto != null && dto.getCollectionMetadataAttributes() != null) {
			for (HpcMetadataLevelAttributes levelAttrs : dto.getCollectionMetadataAttributes()) {
				String label = levelAttrs.getLevelLabel();
				if (label == null)
					continue;
//					label = "Data file";
				hierarchy.put(label, label);
				dataHierarchy.getCollectionAttrsSet().addAll(levelAttrs.getMetadataAttributes());
				for (String name : levelAttrs.getMetadataAttributes())
					attrs.add(label + ":collection:" + name);
			}
		}

		if (dto != null && dto.getDataObjectMetadataAttributes() != null) {
			for (HpcMetadataLevelAttributes levelAttrs : dto.getDataObjectMetadataAttributes()) {
				String label = levelAttrs.getLevelLabel();
				if (label == null)
					continue;
				
				hierarchy.put(label, label);
				dataHierarchy.getDataobjectAttrsSet().addAll(levelAttrs.getMetadataAttributes());
				for (String name : levelAttrs.getMetadataAttributes())
					attrs.add(label + ":datafile:" + name);
			}
		}

		// hierarchy.put(("datafile"), "Data file");
		dataHierarchy.setLevels(hierarchy);
		dataHierarchy.setAllAttributes(attrs);
		model.addAttribute("hierarchy", dataHierarchy);
		session.setAttribute("hierarchy", dataHierarchy);
	}

	private void populateHierarchy(HttpSession session, Model model, String authToken, HpcUserDTO user) {
		try {
			if (session.getAttribute("hierarchies") == null)
				session.setAttribute("hierarchies", getHierarchy(authToken, user));
			model.addAttribute("doc", user.getNciAccount().getDoc());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private Map<String, String> getHierarchy(String authToken, HpcUserDTO user) {
		Map<String, String> hierarchiesMap = new HashMap<String, String>();
		try {
			HpcDataManagementModelDTO docDTO = HpcClientUtil.getDOCModel(authToken, modelServiceURL,
					user.getNciAccount().getDoc(), sslCertPath, sslCertPassword);
			HpcDataHierarchy hierarchy = docDTO.getDataHierarchy();

			List<String> hierarchies = new ArrayList<String>();
			getHierarchies(hierarchy, hierarchies);

			int count = 1;
			for (String name : hierarchies)
				hierarchiesMap.put(name, ("" + count++));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return hierarchiesMap;
	}

	private List<String> getHierarchies(HpcDataHierarchy hierarchy, List<String> hierarchies) {
		try {
			if (hierarchy == null)
				return hierarchies;
			if (hierarchy.getCollectionType() != null)
				hierarchies.add(hierarchy.getCollectionType());
			List<HpcDataHierarchy> subHierarchies = hierarchy.getSubCollectionsHierarchies();
			if (subHierarchies == null || subHierarchies.isEmpty())
				return hierarchies;
			else {
				for (HpcDataHierarchy sub : subHierarchies)
					getHierarchies(sub, hierarchies);
			}
			return hierarchies;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

}
