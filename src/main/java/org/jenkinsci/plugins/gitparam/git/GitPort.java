package org.jenkinsci.plugins.gitparam.git;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LsRemoteCommand;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;

public class GitPort {

	private static final String MASTER_NAME = "master";
	private static final String PATH_TAGS = "refs/tags/";
	private static final String PATH_HEADS = "refs/heads/";
	private static final String SSH_IDENTITY = ".ssh/id_rsa";
	private static final String SSH_KNOWN_HOSTS = ".ssh/known_hosts";

	private URIish repositoryUrl;
	private CredentialsProvider credentialsProvider;

	public GitPort() {
		setupSsh();
	}

	public GitPort(URIish repositoryUrl) {
		this();
		this.repositoryUrl = repositoryUrl;
	}

	public URIish getRepositoryUrl() {
		return repositoryUrl;
	}
	
	public void setRepositoryUrl(URIish repositoryUrl) {
		this.repositoryUrl = repositoryUrl;
		this.credentialsProvider = getCredentialsProvider();

	}

	public List<String> getTagList() throws Exception {
		LsRemoteCommand command = initLsRemoteCommand();
		Collection<Ref> refs = command.setTags(true).call();
		List<String> tagList = getItemNameList(refs, PATH_TAGS);
		return tagList;
	}

	public List<String> getBranchList() throws Exception {
		return getBranchList(true);
	}

	public List<String> getBranchList(boolean omitMaster) throws Exception {
		LsRemoteCommand command = initLsRemoteCommand();
		Collection<Ref> refs = command.setHeads(true).call();
		List<String> branchList = getItemNameList(refs, PATH_HEADS);
		if (omitMaster && branchList.contains(MASTER_NAME) && branchList.size() > 1) {
			branchList.remove(MASTER_NAME);
		}
		return branchList;
	}


	private void setupSsh() {
		setupSsh(SSH_IDENTITY, SSH_KNOWN_HOSTS);
	}
	
	private GitPort setupSsh(String identity, String knownHosts) {
		GitPortSshSessionFactory jschConfigSessionFactory = new GitPortSshSessionFactory();
	    JSch jsch = new JSch();
	    try {
	        jsch.addIdentity(identity);
	        jsch.setKnownHosts(knownHosts);
	    } catch (JSchException e) {
	        e.printStackTrace();  
	    }
	    SshSessionFactory.setInstance(jschConfigSessionFactory);
	    return this;
	}
	
	
	private List<String> getItemNameList(Collection<Ref> refList,
			String stringToOmit) {
		if (refList == null)
			return null;

		List<String> nameList = new LinkedList<String>();
		for (Ref ref : refList) {
			nameList.add(ref.getName().replace(stringToOmit, ""));
		}

		return nameList;
	}

	private LsRemoteCommand initLsRemoteCommand() throws IOException {
		Git git = new Git(new NoLocalRepository());
		return git.lsRemote().setCredentialsProvider(getCredentialsProvider())
				.setRemote(this.repositoryUrl.toString());
	}

	private CredentialsProvider getCredentialsProvider() {
		if (credentialsProvider == null) {
			URIish url = getRepositoryUrl();
			if (url.getUser() != null && url.getPass() != null) {
				credentialsProvider = new UsernamePasswordCredentialsProvider(
						url.getUser(), url.getPass());
			} else {
				credentialsProvider = CredentialsProvider.getDefault();
			}
		}
		return credentialsProvider;
	}
}
