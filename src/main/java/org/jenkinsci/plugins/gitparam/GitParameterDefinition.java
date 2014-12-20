package org.jenkinsci.plugins.gitparam;

import hudson.model.Item;
import hudson.plugins.git.GitStatus;
import hudson.security.ACL;
import org.jenkinsci.plugins.gitclient.GitClient;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import org.eclipse.jgit.transport.RefSpec;

import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceDescriptor;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.SCMSourceOwners;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersDefinitionProperty;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.gitparam.git.GitPort;
import org.jenkinsci.plugins.gitparam.util.StringVersionComparator;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.export.Exported;

import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import org.jenkinsci.plugins.gitclient.GitClient;

public class GitParameterDefinition extends ParameterDefinition implements
		Comparable<GitParameterDefinition> {

	private static final long serialVersionUID = 1183643266235305947L;
	private static final String DISPLAY_NAME = "Git branch/tag parameter";
	private static final Logger LOG = Logger.getLogger(GitParameterDefinition.class.getName());
	
	public static final String PARAM_TYPE_BRANCH = "PT_BRANCH";
	public static final String PARAM_TYPE_TAG = "PT_TAG";
	public static final String SORT_ASC = "S_ASC";
	public static final String SORT_DESC = "S_DESC";


	@Extension
	public static class DescriptorImpl extends ParameterDescriptor {
		@Override
		public String getDisplayName() {
			return DISPLAY_NAME;
		}
	}

	private String type;
	private String defaultValue;
	private String sortOrder;
	private boolean parseVersion;
	private boolean omitMaster;
	private String selectView;
	
	private List<String> branchList;
	private List<String> tagList;
	private String errorMessage;
	private UUID uuid;
    private String repositoryUrl;

	public String getErrorMessage() {
		return errorMessage;
	}
	
	private void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
		if (errorMessage != null && !errorMessage.equals(""))
			System.err.println(errorMessage);
	}

	@Override
	public String getType() {
        // pretending a choice parameter to support some external IDE plugins
		return "ChoiceParameterDefinition";
	}

    public String getParamType() {
        return type;
    }

	public void setParamType(String type) {
		if (type.equals(PARAM_TYPE_BRANCH) || type.equals(PARAM_TYPE_TAG)) {
			this.type = type;
		} else {
			this.setErrorMessage("Wrong type");
		}
	}

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
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

// JRO
	public boolean getOmitMaster() {
		return omitMaster;
	}

	public void setOmitMaster(boolean omitMaster) {
		this.omitMaster = omitMaster;
	}

	public String getSelectView() {
		return selectView;
	}
	
	public void setSelectView(String selectView) {
		this.selectView = selectView;
	}
// JRO END

	public String getDefaultValue() {
		return defaultValue;
	}

	public void setDefaultValue(String defaultValue) {
		this.defaultValue = defaultValue;
	}

	@DataBoundConstructor
	public GitParameterDefinition(String name, String type,
			String defaultValue, String description, 
			String sortOrder, boolean parseVersion, boolean omitMaster, String selectView,
			String repositoryUrl) {
		super(name, description);

		this.type = type;
		this.defaultValue = defaultValue;
		this.sortOrder = sortOrder;
		this.parseVersion = parseVersion;
		this.omitMaster = omitMaster;
		this.selectView = selectView;
		this.uuid = UUID.randomUUID();
		this.errorMessage = "";
        this.repositoryUrl = repositoryUrl;

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
		// first parameter value wins
		GitParameterValue gitParameterValue = new GitParameterValue(
                getName(), values[0]);
        return gitParameterValue;
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
	/*
	  API method pretending a choice parameter to support some external IDE plugins
	 */
    @Exported
    public List<String> getChoices() {
        List<String> contentList = null;
        try {
            if (this.getParamType().equals(PARAM_TYPE_BRANCH)) {
                contentList = this.getBranchList();
            }
            else if (this.getParamType().equals(PARAM_TYPE_TAG)) {
                contentList = this.getTagList();
            }
            return contentList;
        }
        catch(Exception ex) {
            this.errorMessage = "An error occurred during getting list content. \r\n" + ex.getMessage();
            System.err.println(this.errorMessage);
            return null;
        }
    }
	
	private List<String> generateContents(String paramTypeTag) {
		AbstractProject<?, ?> project = getCurrentProject();

        URIish repoUrl = null;
        if (this.getRepositoryUrl() == null || this.getRepositoryUrl().trim().equals("")) {
            repoUrl = getRepositoryUrl(project);
        } else {
            try {
                repoUrl = new URIish(this.getRepositoryUrl());
            }
            catch(Exception ex) {
                this.setErrorMessage("An error occurred during parsing repo URL. \r\n" + ex.getMessage());
                return null;
            }
        }

		if (repoUrl == null) {
			return null;
        }

		GitPort git = new GitPort(repoUrl);
		
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
			this.setErrorMessage("An error occurred during getting list content. \r\n" + ex.getMessage());
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
			for (hudson.plugins.git.UserRemoteConfig uc : gitScm.getUserRemoteConfigs()) { // DZI
				if (uc.getCredentialsId() != null) {
					String url = uc.getUrl();
					StandardUsernamePasswordCredentials credentials = CredentialsMatchers.firstOrNull(
						    CredentialsProvider.lookupCredentials(
						    		StandardUsernamePasswordCredentials.class, project, ACL.SYSTEM,
						    		URIRequirementBuilder.fromUri(url).build()
						    ),
					        CredentialsMatchers.allOf(
					        		CredentialsMatchers.withId(uc.getCredentialsId()), GitClient.CREDENTIALS_MATCHER
					        )
					    );
					if (credentials != null) {
						repoUri = repoUri.setUser(credentials.getUsername());
						repoUri = repoUri.setPass(credentials.getPassword().getPlainText());
					}
				}
			} // DZI
			return repoUri;
		}
		catch(IndexOutOfBoundsException ex) {
			this.setErrorMessage("There is no Git repository defined");
			return null;
		}
	}
	
	private GitSCM getGitSCM(AbstractProject<?, ?> project) {
		SCM scm = project.getScm();
		if (!(scm instanceof GitSCM)) {
			this.setErrorMessage("There is no Git SCM defined");
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
