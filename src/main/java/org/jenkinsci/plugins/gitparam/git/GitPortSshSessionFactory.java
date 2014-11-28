package org.jenkinsci.plugins.gitparam.git;

import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig.Host;

import com.jcraft.jsch.Session;

public class GitPortSshSessionFactory extends JschConfigSessionFactory {

	@Override
	protected void configure(Host hc, Session session) {
		 session.setConfig("StrictHostKeyChecking", "yes");
	}

}
