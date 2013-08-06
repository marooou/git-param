package org.jenkinsci.plugins.gitparam;

import hudson.Extension;
import hudson.model.ParameterValue;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.gitparam.git.GitPort;
import org.jenkinsci.plugins.gitparam.util.StringVersionComparator;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

public class GitParameterDefinition extends ParameterDefinition implements
		Comparable<GitParameterDefinition> {

	private static final long serialVersionUID = 1183643266235305947L;

	public static final String PARAM_TYPE_BRANCH = "PT_BRANCH";
	public static final String PARAM_TYPE_TAG = "PT_TAG";
	public static final String SORT_ASC = "S_ASC";
	public static final String SORT_DESC = "S_DESC";

	@Extension
	public static class DescriptorImpl extends ParameterDescriptor {
		@Override
		public String getDisplayName() {
			return "Git branch/tag parameter";
		}
	}

	private String type;
	private String defaultValue;
	private String sortOrder;
	private boolean parseVersion;
	
	private List<String> branchList;
	private List<String> tagList;
	private UUID uuid;
	private String errorMessage;

	public String getErrorMessage() {
		return errorMessage;
	}

	@Override
	public String getType() {
		return type;
	}

	public void setType(String type) {
		if (type.equals(PARAM_TYPE_BRANCH) || type.equals(PARAM_TYPE_TAG)) {
			this.type = type;
		} else {
			this.errorMessage = "Wrong type";
			System.err.println(this.errorMessage);
		}
	}
	
	public String getSortOrder() {
		return sortOrder;
	}

	public void setSortOrder(String sortOrder) {
		this.sortOrder = sortOrder;
	}

	public boolean getParseVersion() {
		return parseVersion;
	}

	public void setParseVersion(boolean parseVersion) {
		this.parseVersion = parseVersion;
	}	

	public String getDefaultValue() {
		return defaultValue;
	}

	public void setDefaultValue(String defaultValue) {
		this.defaultValue = defaultValue;
	}

	@DataBoundConstructor
	public GitParameterDefinition(String name, String type,
			String defaultValue, String description, 
			String sortOrder, boolean parseVersion) {
		super(name, description);

		this.type = type;
		this.defaultValue = defaultValue;
		this.sortOrder = sortOrder;
		this.parseVersion = parseVersion;
		this.uuid = UUID.randomUUID(); 
		this.errorMessage = "";
	}

	public int compareTo(GitParameterDefinition o) {
		if (o.uuid.equals(uuid)) {
			return 0;
		}
		return -1;
	}

	/*
	 * Creates a value on GET request
	 */
	@Override
	public ParameterValue createValue(StaplerRequest request) {
		String[] values = request.getParameterValues(getName());
		if (values == null) {
			return getDefaultParameterValue();
		}
		return null;
	}

	/*
	 * Creates a value on POST request
	 */
	@Override
	public ParameterValue createValue(StaplerRequest arg0, JSONObject jsonObj) {
		Object value = jsonObj.get("value");
		String strValue = "";
		if (value instanceof String) {
			strValue = (String) value;
		} else if (value instanceof JSONArray) {
			JSONArray jsonValues = (JSONArray) value;
			for (int i = 0; i < jsonValues.size(); i++) {
				strValue += jsonValues.getString(i);
				if (i < jsonValues.size() - 1) {
					strValue += ",";
				}
			}
		}

		if ("".equals(strValue)) {
			strValue = getDefaultValue();
		}

		GitParameterValue gitParameterValue = new GitParameterValue(
				jsonObj.getString("name"), strValue);
		return gitParameterValue;
	}

	@Override
	public ParameterValue getDefaultParameterValue() {
		String defValue = getDefaultValue();
		if (!StringUtils.isBlank(defValue)) {
			return new GitParameterValue(getName(), defValue);
		}
		return super.getDefaultParameterValue();
	}

	public List<String> getBranchList() {
		if (branchList == null || branchList.isEmpty()) {
			branchList = generateContents(PARAM_TYPE_BRANCH);
		}
		return branchList;
	}

	public List<String> getTagList() {
		if (tagList == null || tagList.isEmpty()) {
			tagList = generateContents(PARAM_TYPE_TAG);
		}
		return tagList;
	}
	
	private List<String> generateContents(String paramTypeTag) {
		AbstractProject<?, ?> project = getCurrentProject();
					
		URIish repoUrl = getRepositoryUrl(project);
		if (repoUrl == null) 
			return null;

		GitPort git = new GitPort(repoUrl).useSsh();
		
		List<String> contentList = null;
		try {
			if (paramTypeTag.equals(PARAM_TYPE_BRANCH)) {
				contentList = git.getBranchList();
			}
			else if (paramTypeTag.equals(PARAM_TYPE_TAG)) {
				contentList = git.getTagList();
			}
			
			boolean reverseComparator = this.getSortOrder().equals(SORT_DESC);
			StringVersionComparator comparator = new StringVersionComparator(reverseComparator, getParseVersion());
			Collections.sort(contentList, comparator);
			return contentList;
		}
		catch(Exception ex) {
			this.errorMessage = "An error occurred during getting list content. \r\n" + ex.getMessage();
			System.err.println(this.errorMessage);
			return null;
		}
	}
	
	private URIish getRepositoryUrl(AbstractProject<?, ?> project) {
		GitSCM gitScm = getGitSCM(project);
		if (gitScm == null) 
			return null;
		URIish repoUri = null;
		try {
			repoUri = gitScm.getRepositories().get(0).getURIs().get(0);
			return repoUri;
		}
		catch(IndexOutOfBoundsException ex) {
			this.errorMessage = "There is no Git repository defined";
			System.err.println(this.errorMessage);
			return null;
		}
	}
	
	private GitSCM getGitSCM(AbstractProject<?, ?> project) {
		SCM scm = project.getScm();
		if (!(scm instanceof GitSCM)) {
			this.errorMessage = "There is no Git SCM defined";
			System.err.println(this.errorMessage);
			return null;
		}
		return (GitSCM)scm;
	}
	
	private AbstractProject<?, ?> getCurrentProject() {
		AbstractProject<?, ?> context = null;
		List<AbstractProject> jobs = Hudson.getInstance().getItems(
				AbstractProject.class);

		for (AbstractProject<?, ?> project : jobs) {
			ParametersDefinitionProperty property = (ParametersDefinitionProperty) project
					.getProperty(ParametersDefinitionProperty.class);

			if (property != null) {
				List<ParameterDefinition> parameterDefinitions = property
						.getParameterDefinitions();

				if (parameterDefinitions != null) {
					for (ParameterDefinition pd : parameterDefinitions) {

						if (pd instanceof GitParameterDefinition
								&& ((GitParameterDefinition) pd)
										.compareTo(this) == 0) {

							context = project;
							break;
						}
					}
				}
			}
		}

		return context;
	}

}
