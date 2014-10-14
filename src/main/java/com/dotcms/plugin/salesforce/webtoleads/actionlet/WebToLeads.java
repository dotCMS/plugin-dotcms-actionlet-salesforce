package com.dotcms.plugin.salesforce.webtoleads.actionlet;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.PostMethod;

import com.dotmarketing.beans.Host;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.cmis.proxy.DotInvocationHandler;
import com.dotmarketing.cmis.proxy.DotRequestProxy;
import com.dotmarketing.cmis.proxy.DotResponseProxy;
import com.dotmarketing.portlets.contentlet.model.Contentlet;
import com.dotmarketing.portlets.workflows.actionlet.WorkFlowActionlet;
import com.dotmarketing.portlets.workflows.model.WorkflowActionClassParameter;
import com.dotmarketing.portlets.workflows.model.WorkflowActionFailureException;
import com.dotmarketing.portlets.workflows.model.WorkflowActionletParameter;
import com.dotmarketing.portlets.workflows.model.WorkflowProcessor;
import com.dotmarketing.util.DNSUtil;
import com.dotmarketing.util.Logger;
import com.dotmarketing.util.UtilMethods;
import com.dotmarketing.util.VelocityUtil;

public class WebToLeads extends WorkFlowActionlet {

	private static final long serialVersionUID = 1L;
	final String postUrl = "https://www.salesforce.com/servlet/servlet.WebToLead?encoding=UTF-8";

	@Override
	public List<WorkflowActionletParameter> getParameters() {
		List<WorkflowActionletParameter> params = new ArrayList<WorkflowActionletParameter>();
		params.add(new WorkflowActionletParameter("oid", "Salesforce Web-to-Lead OID", "", true));
		params.add(new WorkflowActionletParameter("first_name", "First Name", "$!content.firstName", true));
		params.add(new WorkflowActionletParameter("last_name", "Last Name", "$!content.lastName", true));
		params.add(new WorkflowActionletParameter("email", "Email", "$!content.email", true));
		params.add(new WorkflowActionletParameter("company", "Company", "$!content.company", false));
		params.add(new WorkflowActionletParameter("address1", "Address1", "$!content.address1", false));
		params.add(new WorkflowActionletParameter("address2", "Address2", "$!content.address2", false));
		params.add(new WorkflowActionletParameter("city", "City", "$!content.city", false));
		params.add(new WorkflowActionletParameter("state", "State", "$!content.state", false));
		params.add(new WorkflowActionletParameter("zip", "Postal Code", "$!content.zip", false));
		params.add(new WorkflowActionletParameter("country", "Country", "$!content.country", false));
		params.add(new WorkflowActionletParameter("website", "Website", "$!content.website", false));
		params.add(new WorkflowActionletParameter("phone", "Phone", "$!content.phone", false));
		params.add(new WorkflowActionletParameter("description", "Description", "$!content.description", false));
		params.add(new WorkflowActionletParameter("00NG000000EWhkM", "IP Address", "$!content.ipAddress", false));
		params.add(new WorkflowActionletParameter("condition", 
				"Condition - lead will be sent unless<br>velocity prints 'false'", "", false));
		return params;
	}

	@Override
	public String getName() {
		return "Post to Salesforce";
	}

	@Override
	public String getHowTo() {
		return "This actionlet will post content from a form entry to your Salesforce leads list. The value of every field here is parsed velocity.  So, if your content has a field, 'userEmail' to set this to Salesforce's email, use $content.userEmail in the 'Email' field and the system will replace it with the variables from the content";
	}

	@Override
	public void executeAction(WorkflowProcessor processor, Map<String, WorkflowActionClassParameter> params)
			throws WorkflowActionFailureException {

		Contentlet c = processor.getContentlet();

		String rev = c.getStringProperty("ipAddress");
		try {
			rev = DNSUtil.reverseDns(rev);
			c.setStringProperty("ipAddress", rev);
		} catch (Exception e) {
			Logger.error(this.getClass(), "error on reverse lookup" + e.getMessage());
		}


		String condition = params.get("condition").getValue();

		try {
			// get the host of the content
			Host host = APILocator.getHostAPI().find(processor.getContentlet().getHost(), APILocator.getUserAPI().getSystemUser(), false);
			if (host.isSystemHost()) {
				host = APILocator.getHostAPI().findDefaultHost(APILocator.getUserAPI().getSystemUser(), false);
			}

			InvocationHandler dotInvocationHandler = new DotInvocationHandler(new HashMap());

			DotRequestProxy requestProxy = (DotRequestProxy) Proxy.newProxyInstance(DotRequestProxy.class.getClassLoader(),
					new Class[] { DotRequestProxy.class }, dotInvocationHandler);
			requestProxy.put("host", host);
			requestProxy.put("host_id", host.getIdentifier());
			requestProxy.put("user", processor.getUser());
			DotResponseProxy responseProxy = (DotResponseProxy) Proxy.newProxyInstance(DotResponseProxy.class.getClassLoader(),
					new Class[] { DotResponseProxy.class }, dotInvocationHandler);

			org.apache.velocity.context.Context ctx = VelocityUtil.getWebContext(requestProxy, responseProxy);
			ctx.put("host", host);
			ctx.put("host_id", host.getIdentifier());
			ctx.put("user", processor.getUser());
			ctx.put("workflow", processor);
			ctx.put("stepName", processor.getStep().getName());
			ctx.put("stepId", processor.getStep().getId());
			ctx.put("nextAssign", processor.getNextAssign().getName());
			ctx.put("workflowMessage", processor.getWorkflowMessage());
			ctx.put("nextStepResolved", processor.getNextStep().isResolved());
			ctx.put("nextStepId", processor.getNextStep().getId());
			ctx.put("nextStepName", processor.getNextStep().getName());
			ctx.put("workflowTaskTitle", UtilMethods.isSet(processor.getTask().getTitle()) ? processor.getTask().getTitle() : processor
					.getContentlet().getTitle());
			ctx.put("modDate", processor.getTask().getModDate());
			ctx.put("structureName", processor.getContentlet().getStructure().getName());

			ctx.put("contentlet", c);
			ctx.put("content", c);

			if (UtilMethods.isSet(condition)) {
				condition = VelocityUtil.eval(condition, ctx);
				if (UtilMethods.isSet(condition) && condition.indexOf("false") > -1) {
					Logger.info(this.getClass(), processor.getAction().getName() + " email condition contains false, skipping email send");
					return;
				}
			}

	        HttpClient client = new HttpClient();



	        PostMethod method = new PostMethod( postUrl );

	        List<NameValuePair> data = new ArrayList<NameValuePair>();

	        for(WorkflowActionletParameter param : getParameters()){
	        	String paramKey = param.getKey();
	    		String paramVal = params.get(paramKey).getValue();
				if(UtilMethods.isSet(paramVal)){
					paramVal = VelocityUtil.eval(paramVal, ctx);
					if(UtilMethods.isSet(paramVal)){
						
						data.add(new NameValuePair(paramKey, paramVal.trim()));
					}
				}
	        }
	        

	        
	        
	        method.setRequestBody( data.toArray(new NameValuePair[data.size()]));
	        client.executeMethod( method );

		} catch (Exception e) {
			Logger.error(WebToLeads.class, e.getMessage(), e);
		}

	}

}
