package gov.nih.nci.hpc.web.util;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import gov.nih.nci.hpc.domain.metadata.HpcCompoundMetadataQuery;
import gov.nih.nci.hpc.domain.metadata.HpcCompoundMetadataQueryOperator;
import gov.nih.nci.hpc.domain.metadata.HpcMetadataQuery;
import gov.nih.nci.hpc.domain.metadata.HpcMetadataQueryOperator;
import gov.nih.nci.hpc.web.HpcWebException;
import gov.nih.nci.hpc.web.model.HpcCompoundQuery;
import gov.nih.nci.hpc.web.model.HpcCompoundQueryCondition;

public class HpcCompoundSearchBuilder {

	private HpcCompoundQuery query;
	
	private SecureRandom random = new SecureRandom();

	public HpcCompoundSearchBuilder(HpcCompoundQuery query) throws HpcWebException
	{
		if(query == null || query.getQueries() == null || query.getQueries().size() == 0 || query.getCriteria() == null)
			throw new HpcWebException("Invalid Search criteria");
		this.query = query;
	}
	
	public HpcCompoundMetadataQuery build() throws HpcWebException {
		//Validate criteria
		HpcCompoundSearchValidator validator = new HpcCompoundSearchValidator(query.getQueries(), query.getCriteria());
		validator.validate();
		
		//For each part of the criteria, build HpcCompoundMetadataQuery and redefine criteria with 
		//newly built HpcCompoundMetadataQuery name
		HpcCompoundMetadataQuery compoundQuery = null;
		while(true)
		{
			HpcCompoundQueryCondition condition = parseQueryCondition(query.getCriteria());
			if(condition == null)
				break;
			HpcCompoundMetadataQuery compoundMetadataQuery = buildQuery(condition.getQueryPart(), condition.getName());
			query.getCompoundQueries().put(condition.getName(), compoundMetadataQuery);
			compoundQuery = compoundMetadataQuery;
		}
		return compoundQuery;
	}
	
	public HpcCompoundMetadataQuery buildQuery(String queryPart, String queryName) throws HpcWebException {
		if (queryPart == null || queryName == null)
			throw new HpcWebException("Invalid Search criteria");
		HpcCompoundMetadataQuery compoundQuery = new HpcCompoundMetadataQuery();
		
		StringTokenizer tokens = new StringTokenizer(queryPart,  " ");
		boolean part1 = true;
		boolean part2 = false;
		boolean operator = false;
		String part1Token = null;
		String part2Token = null;
		String operatorToken = null;
		while(tokens.hasMoreTokens())
		{
			if(tokens.countTokens() > 3)
				throw new HpcWebException("Invalid Search criteria. Please group two parts if you are using different operators. Example: (A1 AND A2) OR A3");
			String token = tokens.nextToken();
			if((part1 || part2) && (token.equals(HpcCompoundMetadataQueryOperator.AND.value()) || token.equals(HpcCompoundMetadataQueryOperator.OR.value())))
				throw new HpcWebException("Invalid search criteria");
			else if(operator && (!token.equals(HpcCompoundMetadataQueryOperator.AND.value()) && !token.equals(HpcCompoundMetadataQueryOperator.OR.value())))
				throw new HpcWebException("Invalid search criteria");
			if(part1)
				part1Token = token;
			else if(part2)
				part2Token = token;
			else if(operator)
				operatorToken = token;
			
			if(part1)
			{
				operator = true;
				part2 = false;
				part1 = false;
			}
			else if(part2)
			{
				part1 = true;
				part2 = false;
				operator = false;
				compoundQuery = buildCompoundMetadataQuery(part1Token, part2Token, operatorToken);
			}
			else if(operator)
			{
				part1 = false;
				part2 = true;
				operator = false;
			}
		}
		String criteria = query.getCriteria();
		criteria = criteria.replace(queryPart, queryName);
		if(criteria.indexOf("(") != -1)
		{
			int start = criteria.indexOf(queryName)-1;
			int end = start + queryName.length()+1;
			criteria = criteria.substring(0, start) + queryName + criteria.substring(end+1, criteria.length());
		}
		query.setCriteria(criteria);
		return compoundQuery;
	}

	private HpcCompoundMetadataQuery buildCompoundMetadataQuery(String part1, String part2, String operator)
	{
		HpcCompoundMetadataQuery compoundQuery = new HpcCompoundMetadataQuery();
		if(operator.equals(HpcCompoundMetadataQueryOperator.AND.value()))
			compoundQuery.setOperator(HpcCompoundMetadataQueryOperator.AND);
		else if(operator.equals(HpcCompoundMetadataQueryOperator.OR.value()))
			compoundQuery.setOperator(HpcCompoundMetadataQueryOperator.OR);
		else
			throw new HpcWebException("Invalid Search criteria operator. Valid values are AND, OR");
		
		HpcMetadataQuery simpleQuery1 = query.getQueries().get(part1);
		if(simpleQuery1 == null)
		{
			HpcCompoundMetadataQuery compoundPart = query.getCompoundQueries().get(part1);
			if(compoundPart != null)
				compoundQuery.getCompoundQueries().add(compoundPart);
			else
				throw new HpcWebException("Invalid Search criteria IDs");
		}
		else
		{
			compoundQuery.getQueries().add(simpleQuery1);
		}
		
		if(part2 != null)
		{
			HpcMetadataQuery simpleQuery2 = query.getQueries().get(part2);
			if(simpleQuery2 == null)
			{
				HpcCompoundMetadataQuery compoundPart = query.getCompoundQueries().get(part2);
				if(compoundPart != null)
					compoundQuery.getCompoundQueries().add(compoundPart);
				else
					throw new HpcWebException("Invalid Search criteria IDs");
			}
			else
			{
				compoundQuery.getQueries().add(simpleQuery2);
			}
			
		}

		return compoundQuery;
	}

	public HpcCompoundQueryCondition parseQueryCondition(String criteria)
	{
		HpcCompoundQueryCondition condition = null;
		if(criteria.indexOf('(') != -1)
		{
			String tempCriteria = criteria.substring(criteria.lastIndexOf('(')+1, criteria.length());
			String subCriteria = tempCriteria.substring(0, tempCriteria.indexOf(')'));
			condition = buildQueryCondition(subCriteria);
		}
		else 
		{
			if(criteria.indexOf("AND") != -1 || criteria.indexOf("OR") != -1)
			{
				String name = "criteria"+getRandomId();
				condition = new HpcCompoundQueryCondition(name, criteria);
			}
			else
				return null;
		}
		return condition;
	}
	
	private HpcCompoundQueryCondition buildQueryCondition(String criteria)
	{
		HpcCompoundQueryCondition condition = null;

		if(criteria.indexOf("(") != -1)
			parseQueryCondition(criteria);
		else
		{
			String name = "criteria"+getRandomId();
			condition = new HpcCompoundQueryCondition(name, criteria);
		}
		return condition;
	}
	
	  public String getRandomId() {
		    return new BigInteger(130, random).toString(32);
	}	
	  
	  
	  public static void main(String args[])
	  {
		  HpcCompoundQuery query = new HpcCompoundQuery();
		  String criteria = "(A1 AND A2) OR A3";
		  Map<String, HpcMetadataQuery> queries = new HashMap<String, HpcMetadataQuery>();
		  HpcMetadataQuery A1 = new HpcMetadataQuery();
		  A1.setAttribute("pi_name");
		  A1.setValue("%");
		  A1.setOperator(HpcMetadataQueryOperator.LIKE);
		  
		  HpcMetadataQuery A2 = new HpcMetadataQuery();
		  A2.setAttribute("registered_by");
		  A2.setValue("konkapv");
		  A2.setOperator(HpcMetadataQueryOperator.EQUAL);

		  HpcMetadataQuery A3 = new HpcMetadataQuery();
		  A3.setAttribute("registered_by_doc");
		  A3.setValue("FNLCR");
		  A3.setOperator(HpcMetadataQueryOperator.EQUAL);

		  queries.put("A1", A1);
		  queries.put("A2", A2);
		  queries.put("A3", A3);
		  query.setQueries(queries);
		  query.setCriteria(criteria);
		  
		  HpcCompoundMetadataQuery compoundMetadataQuery = new HpcCompoundSearchBuilder(query).build();
		  System.out.println(compoundMetadataQuery);
	  }
	
}
