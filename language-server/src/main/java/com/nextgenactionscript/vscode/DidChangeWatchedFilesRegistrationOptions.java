package com.nextgenactionscript.vscode;

import java.util.List;
import org.eclipse.xtext.xbase.lib.Pure;
import org.eclipse.xtext.xbase.lib.util.ToStringBuilder;

@SuppressWarnings("all")
public class DidChangeWatchedFilesRegistrationOptions
{
public static class FileSystemWatcher
{
	public FileSystemWatcher(String globPattern)
	{
		this.globPattern = globPattern;
	}
	String globPattern;
}
  private List<FileSystemWatcher> watchers;
  
  public DidChangeWatchedFilesRegistrationOptions() {
  }
  
  public DidChangeWatchedFilesRegistrationOptions(final List<FileSystemWatcher> watchers) {
    this.watchers = watchers;
  }

  @Pure
  public List<FileSystemWatcher> getWatchers() {
    return this.watchers;
  }
  
  /**
   * The commands to be executed on the server
   */
  public void setWatchers(final List<FileSystemWatcher> watchers) {
    this.watchers = watchers;
  }
  
  @Override
  @Pure
  public String toString() {
    ToStringBuilder b = new ToStringBuilder(this);
    b.add("watchers", this.watchers);
    return b.toString();
  }
  
  @Override
  @Pure
  public boolean equals(final Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
	  DidChangeWatchedFilesRegistrationOptions other = (DidChangeWatchedFilesRegistrationOptions) obj;
    if (this.watchers == null) {
      if (other.watchers != null)
        return false;
    } else if (!this.watchers.equals(other.watchers))
      return false;
    return true;
  }
  
  @Override
  @Pure
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((this.watchers== null) ? 0 : this.watchers.hashCode());
    return result;
  }
}