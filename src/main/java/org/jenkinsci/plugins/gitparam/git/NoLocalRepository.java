package org.jenkinsci.plugins.gitparam.git;

import java.io.IOException;

import org.eclipse.jgit.internal.storage.file.FileRepository;

public class NoLocalRepository extends FileRepository {

	public NoLocalRepository() throws IOException {
		super("");
	}

}
