/*
 * Copyright (c) 2017 Teradata
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.teradata.jaqy;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;

import javax.script.ScriptEngine;

import com.teradata.jaqy.interfaces.Display;
import com.teradata.jaqy.interfaces.JaqyExporter;
import com.teradata.jaqy.interfaces.JaqyImporter;
import com.teradata.jaqy.interfaces.JaqyOption;
import com.teradata.jaqy.interfaces.JaqyPlugin;
import com.teradata.jaqy.interfaces.JaqyPrinter;
import com.teradata.jaqy.interfaces.Variable;
import com.teradata.jaqy.utils.FixedVariable;
import com.teradata.jaqy.utils.PathUtils;
import com.teradata.jaqy.utils.URLUtils;

/**
 * This class holds all the globals and configuration informations.
 *
 * @author	Heng Yuan
 */
public class Globals
{
	/** The program name */
	private String m_name;
	/** The program version */
	private String m_version;
	/** The greeting message */
	private String m_greeting;

	private final JaqyDriverManager m_driverManager = new JaqyDriverManager ();
	private final OptionManager m_optionManager = new OptionManager ();
	private final ScriptManager m_scriptManager = new ScriptManager ();
	private final DebugManager m_debugManager = new DebugManager ();
	private final HashMap<String, ScriptEngine> m_engines = new HashMap<String, ScriptEngine> ();
	private final Os m_os = new Os ();
	private final CommandManager m_commandManager = new CommandManager (this);
	private final AliasManager m_aliasManager = new AliasManager ();

	private Object m_sessionLock = new Object ();
	private final ArrayList<Session> m_sessions = new ArrayList<Session> ();

	private final VariableManager m_variables = new VariableManager (null);
	private final Variable m_globalsVar = new FixedVariable ("globals", this, "Global objects");

	private final JaqyHandlerFactoryManager<JaqyPrinter> m_printerManager = new JaqyHandlerFactoryManager<JaqyPrinter> ("com.teradata.jaqy.interfaces.JaqyPrinter");
	private final JaqyHandlerFactoryManager<JaqyExporter> m_exporterManager = new JaqyHandlerFactoryManager<JaqyExporter> ("com.teradata.jaqy.interfaces.JaqyExporter");
	private final JaqyHandlerFactoryManager<JaqyImporter<?>> m_importerManager = new JaqyHandlerFactoryManager<JaqyImporter<?>> ("com.teradata.jaqy.interfaces.JaqyImporter");

	private final String m_rc = "com.teradata.jaqy.interfaces.JaqyPlugin";
	private final File m_dir = new File (".");

	Globals ()
	{
	}

	public void printVersion (PrintWriter pw, String defaultName, String defaultVersion)
	{
		String name = getName ();
		String version = getVersion ();
		if (name == null)
			name = defaultName;
		if (version == null)
			version = defaultVersion;
		pw.println (name + " " + version);
	}

	public void addOption (JaqyOption option)
	{
		m_optionManager.addOption (option);
	}

	public OptionManager getOptionManager ()
	{
		return m_optionManager;
	}

	public JaqyDriverManager getDriverManager ()
	{
		return m_driverManager;
	}

	public String getName ()
	{
		if (m_name == null)
		{
			Package pkg = Globals.class.getPackage ();
			m_name = pkg.getImplementationTitle ();
		}
		return m_name;
	}

	public String getVersion ()
	{
		if (m_version == null)
		{
			Package pkg = Globals.class.getPackage ();
			m_version = pkg.getImplementationVersion ();
		}
		return m_version;
	}

	public void setName (String name)
	{
		m_name = name;
	}

	public void setVersion (String version)
	{
		m_version = version;
	}

	/**
	 * Get the greeting message.
	 *
	 * @return	the greeting message
	 */
	public String getGreeting ()
	{
		return m_greeting;
	}

	/**
	 * Set the greeting message.
	 *
	 * @param	greetMessage
	 *			the greeting message to be set
	 */
	public void setGreeting (String greetMessage)
	{
		m_greeting = greetMessage;
	}

	/**
	 * Get the script manager.
	 *
	 * @return	the script manager
	 */
	public ScriptManager getScriptManager ()
	{
		return m_scriptManager;
	}

	/**
	 * Get the global script engine for a particular script type.
	 *
	 * @param	type
	 *			script engine type.
	 * @return	script engine for the type.
	 */
	public ScriptEngine getScriptEngine (String type)
	{
		synchronized (m_engines)
		{
			ScriptEngine engine = m_engines.get (type);
			if (engine == null)
			{
				engine = m_scriptManager.createEngine (type);
				if (engine != null)
					m_engines.put (type, engine);
			}
			return engine;
		}
	}

	public Session createSession (Display display)
	{
		synchronized (m_sessionLock)
		{
			int sessionId = m_sessions.size ();
			Session session = new Session (this, sessionId, display);
			m_sessions.add (session);
			return session;
		}
	}

	/**
	 * Get current number of sessions.
	 * @return	the current number of sessions.
	 */
	public int getNumSessions ()
	{
		synchronized (m_sessionLock)
		{
			return m_sessions.size ();
		}
	}

	/**
	 * Get a session based on the session id.
	 * @param	id
	 * 			the session id
	 * @return	the session corresponding to the id.
	 */
	public Session getSession (int id)
	{
		synchronized (m_sessionLock)
		{
			if (id < 0 || id >= m_sessions.size ())
				return null;
			return m_sessions.get (id);
		}
	}

	/**
	 * Get current sessions.
	 * <p>
	 * This function should only be called when sessionLock is locked.
	 * @return	the current sessions.
	 */
	public Collection<Session> getSessions ()
	{
		ArrayList<Session> sessions = new ArrayList<Session> ();
		synchronized (m_sessionLock)
		{
			sessions.addAll (m_sessions);
		}
		return sessions;
	}

	/**
	 * Retrieves the debug manager.
	 * @return	the debug manager
	 */
	public DebugManager getDebugManager ()
	{
		return m_debugManager;
	}

	/**
	 * Retrieves the OS object.
	 *
	 * @return	the os object
	 */
	public Os getOs ()
	{
		return m_os;
	}

	/**
	 * Gets the command manager.
	 * @return	the command manager
	 */
	public CommandManager getCommandManager ()
	{
		return m_commandManager;
	}

	/**
	 * Gets the alias manager.
	 * @return	the alias manager
	 */
	public AliasManager getAliasManager ()
	{
		return m_aliasManager;
	}

	/**
	 * Gets the variable manager.
	 * @return	the variable manager
	 */
	public VariableManager getVariables ()
	{
		return m_variables;
	}

	public void setupVariables (VariableManager variables)
	{
		variables.setVariable (m_globalsVar);
	}

	/**
	 * @return	the printerManager
	 */
	public JaqyHandlerFactoryManager<JaqyPrinter> getPrinterManager ()
	{
		return m_printerManager;
	}

	/**
	 * @return	the exporterManager
	 */
	public JaqyHandlerFactoryManager<JaqyExporter> getExporterManager ()
	{
		return m_exporterManager;
	}

	/**
	 * @return	the importerManager
	 */
	public JaqyHandlerFactoryManager<JaqyImporter<?>> getImporterManager ()
	{
		return m_importerManager;
	}

	private void loadRC (ClassLoader cl, JaqyInterpreter interpreter) throws IOException
	{
		String name = "META-INF/services/" + m_rc;
		assert Debug.debug ("loading service: " + name);
		Enumeration<URL> e;
		if (cl instanceof URLClassLoader)
			e = ((URLClassLoader) cl).findResources (name);
		else
			e = cl.getResources (name);
		for (; e.hasMoreElements ();)
		{
			URL url = e.nextElement ();
			assert Debug.debug ("load service url: " + url);
			loadURL (cl, url, interpreter);
		}
	}

	private void loadURL (ClassLoader cl, URL url, JaqyInterpreter interpreter) throws IOException
	{
		BufferedReader reader = new BufferedReader (new InputStreamReader (url.openStream (), "utf-8"));
		String line;
		while ((line = reader.readLine ()) != null)
		{
			line = line.trim ();
			if (line.length () == 0)
				continue;
			assert Debug.debug ("load service class: " + line);
			loadClass (cl, line, interpreter);
		}
		reader.close ();
	}

	private void loadClass (ClassLoader cl, String className, JaqyInterpreter interpreter)
	{
		try
		{
			Class<?> c = cl.loadClass (className);
			JaqyPlugin plugin = (JaqyPlugin) c.newInstance ();
			plugin.init (this);
		}
		catch (Throwable t)
		{
			assert Debug.debug (t);
			interpreter.error (t);
		}
	}

	public void loadPlugin (String path, JaqyInterpreter interpreter)
	{
		try
		{
			String[] paths = PathUtils.split (path);
			URL[] urls = new URL[paths.length];
			for (int i = 0; i < paths.length; ++i)
			{
				urls[i] = URLUtils.getFileURL (paths[i]);
			}
			ClassLoader cl = new URLClassLoader (urls);

			loadRC (cl, interpreter);

			// apparently we do not want to load the resources, since
			// that would result in re-initiating the same resource multiple
			// times.
			m_printerManager.loadRC (cl);
			m_exporterManager.loadRC (cl);
			m_importerManager.loadRC (cl);
		}
		catch (Throwable t)
		{
			assert Debug.debug (t);
			interpreter.error ("invalid classpath: " + path);
		}
	}

	public File getDirectory ()
	{
		return m_dir;
	}
}
